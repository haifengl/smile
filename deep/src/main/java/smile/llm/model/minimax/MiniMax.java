/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.minimax;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.cluster.ClusterFollower;
import smile.llm.cluster.ClusterLeader;
import smile.llm.engine.ModelExecutor;
import smile.llm.engine.MultimodalPrompt;
import smile.llm.ImageUrlPart;
import smile.llm.VideoUrlPart;
import smile.llm.engine.Sampling;
import smile.llm.parallel.ParallelConfig;
import smile.llm.template.MiniMaxChatTemplate;
import smile.llm.quant.MxFp8;
import smile.torch.Native;
import smile.llm.parallel.ParallelState;
import smile.llm.parallel.TensorParallelGroup;
import smile.torch.smile_torch_h;
import smile.util.AutoScope;

/**
 * MiniMax-M3 language model: loads a BF16 checkpoint and serves requests through the
 * {@link ModelExecutor} step API (bind, prefill, decode, finish).
 *
 * <p>This is the single-device reference path: dense attention (no block selection yet),
 * host-routed MoE, no CUDA graphs, no speculation. Quantized (MXFP8) checkpoints and chat
 * templating are added in later phases.
 *
 * @author Haifeng Li
 */
public class MiniMax implements LanguageModel, ModelExecutor, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MiniMax.class);

    /** Model family. */
    public static final String family = "minimax/m3";

    private final String name;
    /** One replica per expert-parallel rank; rank 0 is the reference rank for results. */
    private final MiniMaxModel[] models;
    private final MiniMaxModel model;
    private final TensorParallelGroup group;
    private final ExecutorService executor;
    private final MiniMaxTokenizer tokenizer;
    private final MiniMaxModelArgs params;
    /** Vision encoder on rank 0's device, or {@code null} when the checkpoint has no vision weights. */
    private MiniMaxVisionTower vision;
    private MiniMaxVisionProcessor visionProcessor;
    /** Requests whose KV holds image/video content: never added to the prefix cache (the ids are placeholders). */
    private final Set<Integer> multimodalRequests = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Leader of a multi-node cluster (this process owns the first ranks and mirrors every state change), or null. */
    private volatile ClusterLeader cluster;

    /** Op codes of the messages the leader mirrors to the follower processes (see {@link ClusterLeader}). */
    private static final int OP_BIND = 1;
    private static final int OP_PREFILL = 2;
    private static final int OP_DECODE = 3;
    private static final int OP_FINISH = 4;
    private static final int OP_EVICT = 5;
    private static final int OP_PREFIX_REUSE = 6;

    /**
     * Placement of this process's ranks in a multi-node model.
     *
     * @param worldSize  expert-parallel ranks of the whole model.
     * @param rankOffset world rank of this process's first rank.
     * @param uniqueId   the NCCL unique id shared by every process.
     */
    public record ClusterSpec(int worldSize, int rankOffset, byte[] uniqueId) { }

    /**
     * Makes this process the leader of a cluster: from now on bind, prefill, decode, finish, evict and the prefix-reuse
     * switch are mirrored to the followers, whose copies of the model execute them in lockstep.
     *
     * @param leader the connected cluster.
     */
    public void attachCluster(ClusterLeader leader) {
        this.cluster = leader;
    }

    private static void writeInts(java.io.DataOutputStream out, int[] a) throws IOException {
        out.writeInt(a.length);
        for (int v : a) {
            out.writeInt(v);
        }
    }

    private static int[] readInts(java.io.DataInputStream in) throws IOException {
        int[] a = new int[in.readInt()];
        for (int i = 0; i < a.length; i++) {
            a[i] = in.readInt();
        }
        return a;
    }

    /**
     * Constructor.
     *
     * @param name      model name.
     * @param model     the network (weights loaded, KV pool installed).
     * @param tokenizer the tokenizer.
     * @param params    hyperparameters.
     */
    public MiniMax(String name, MiniMaxModel model, MiniMaxTokenizer tokenizer, MiniMaxModelArgs params) {
        this(name, new MiniMaxModel[]{model}, null, tokenizer, params);
    }

    /**
     * Expert-parallel constructor.
     *
     * @param name      model name.
     * @param models    one replica per rank (weights loaded, KV pools installed), in rank order.
     * @param group     all-reduce group over the ranks' devices, or {@code null} for a single rank.
     * @param tokenizer the tokenizer.
     * @param params    hyperparameters.
     */
    public MiniMax(String name, MiniMaxModel[] models, TensorParallelGroup group,
                   MiniMaxTokenizer tokenizer, MiniMaxModelArgs params) {
        this.name = name;
        this.models = models;
        this.model = models[0];
        this.group = group;
        this.executor = models.length > 1 ? Executors.newFixedThreadPool(models.length) : null;
        this.tokenizer = tokenizer;
        this.params = params;
    }

    /**
     * Returns the hyperparameters.
     * @return model args.
     */
    public MiniMaxModelArgs params() {
        return params;
    }

    /**
     * Returns the network of rank 0.
     * @return the model.
     */
    public MiniMaxModel network() {
        return model;
    }

    /**
     * Returns the network of every expert-parallel rank.
     * @return one replica per rank.
     */
    public MiniMaxModel[] networks() {
        return models;
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (draft != null) {
            draftSnapshots.forEach(snapshot -> draft.discard(snapshot.context()));
            draftSnapshots.clear();
            draft.close();
            draft = null;
        }
        vision = null;   // its weights are released with the module (cleaner)
        visionProcessor = null;
        for (MiniMaxModel m : models) {
            KvCachePool pool = m.kvCachePool();
            if (pool != null) {
                pool.close();
            }
        }
    }

    /**
     * Runs {@code task} on every rank, concurrently when there is more than one (the ranks meet in
     * the MoE all-reduce, so they must run in parallel). Returns each rank's tensor; the caller owns them.
     */
    private Tensor[] onRanks(java.util.function.IntFunction<Tensor> task) {
        Tensor[] out = new Tensor[models.length];
        if (models.length == 1) {
            try (var guard = Tensor.noGradGuard()) {
                out[0] = task.apply(0);
            }
            return out;
        }
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int rank = 0; rank < models.length; rank++) {
            final int r = rank;
            futures.add(executor.submit(() -> {
                ParallelState.setCurrent(group.state(r));
                try (var guard = Tensor.noGradGuard()) {
                    return task.apply(r);
                } finally {
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < out.length; r++) {
                out[r] = futures.get(r).get();
            }
        } catch (Exception e) {
            for (Tensor t : out) {
                if (t != null) {
                    t.close();
                }
            }
            throw new RuntimeException("expert-parallel forward failed", e);
        }
        return out;
    }

    /** Keeps rank 0's result and releases the others. */
    private static Tensor firstAndClose(Tensor[] results) {
        for (int r = 1; r < results.length; r++) {
            if (results[r] != null) {
                results[r].close();
            }
        }
        return results[0];
    }

    @Override
    public String toString() {
        return String.format("%s/%s", family, name);
    }

    @Override
    public String family() {
        return family;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int maxSeqLen() {
        return params.maxSeqLen();
    }

    /**
     * Returns whether images and videos can be processed (the checkpoint shipped vision weights and they loaded).
     * @return {@code true} when {@link #processMultimodal} may be called.
     */
    public boolean isMultimodal() {
        return vision != null;
    }

    /**
     * Installs the vision encoder (rank 0's device).
     *
     * @param tower the loaded tower, or {@code null}.
     */
    void setVision(MiniMaxVisionTower tower) {
        this.vision = tower;
        this.visionProcessor = tower == null ? null : new MiniMaxVisionProcessor(tower.args());
    }

    private static final String IMAGE_MARKER = "]<]image[>[";
    private static final String VIDEO_MARKER = "]<]video[>[";
    private static final String VISION_START = "]<]start of image[>[";
    private static final String VISION_END = "]<]end of image[>[";
    /** Tokens per prefill window of a multimodal prompt. */
    private static final int MULTIMODAL_CHUNK = 2048;

    /** Python's {@code f"{x:.1f}"}: the exact binary value rounded half to even. */
    private static String seconds(double value) {
        return new java.math.BigDecimal(value).setScale(1, java.math.RoundingMode.HALF_EVEN).toPlainString();
    }

    /**
     * Applies the chat template to a dialog with images and videos, preprocesses the media, and expands every
     * placeholder to one token per vision patch group exactly as the reference processor does: an image becomes
     * {@code start, image * n, end}; a video becomes, per two-frame group, a timestamp text, {@code start,
     * video * n, end}.
     *
     * @param dialog  the messages; media parts are image or video URLs (data, http(s) or file).
     * @param options chat options.
     * @return the expanded token ids and the patches to encode during prefill.
     * @throws IOException              if media cannot be read or decoded.
     * @throws IllegalArgumentException if the request is malformed (no vision weights, audio, a media marker typed
     *                                  into text, an extreme aspect ratio, ...).
     */
    public MiniMaxMultimodal processMultimodal(Message[] dialog, ChatOptions options) throws IOException {
        if (vision == null) {
            throw new IllegalArgumentException("this MiniMax-M3 checkpoint has no vision weights");
        }
        if (dialog == null || dialog.length == 0) {
            throw new IllegalArgumentException("No messages provided.");
        }
        List<String> imageUrls = new ArrayList<>();
        List<VideoUrlPart> videos = new ArrayList<>();
        for (Message m : dialog) {
            for (var part : m.parts()) {
                if (part instanceof ImageUrlPart image) {
                    imageUrls.add(image.url());
                } else if (part instanceof VideoUrlPart video) {
                    videos.add(video);
                } else if (part instanceof smile.llm.AudioUrlPart) {
                    throw new IllegalArgumentException("MiniMax-M3 does not accept audio input");
                }
            }
        }
        String text = MiniMaxChatTemplate.forOptions(options).encode(Arrays.asList(dialog), options);
        if (count(text, IMAGE_MARKER) != imageUrls.size() || count(text, VIDEO_MARKER) != videos.size()) {
            throw new IllegalArgumentException("the prompt contains " + count(text, IMAGE_MARKER) + " image and "
                    + count(text, VIDEO_MARKER) + " video markers for " + imageUrls.size() + " images and "
                    + videos.size() + " videos (do not type the placeholder tokens into message text)");
        }

        int merge2 = vision.args().mergeSize() * vision.args().mergeSize();
        List<MiniMaxVisionProcessor.Patches> images = new ArrayList<>();
        List<MiniMaxVisionProcessor.Patches> clips = new ArrayList<>();
        List<double[]> clipTimes = new ArrayList<>();
        try {
            for (String url : imageUrls) {
                try {
                    images.add(visionProcessor.image(MiniMaxMedia.image(url)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("unusable image: " + e.getMessage(), e);
                }
            }
            for (VideoUrlPart v : videos) {
                var clip = MiniMaxMedia.video(v.url(), v.fps());
                clips.add(visionProcessor.video(clip.frames()));
                clipTimes.add(clip.timestamps());
            }

            StringBuilder out = new StringBuilder(text.length() + 64);
            int imageIndex = 0;
            int videoIndex = 0;
            int at = 0;
            while (at < text.length()) {
                int nextImage = text.indexOf(IMAGE_MARKER, at);
                int nextVideo = text.indexOf(VIDEO_MARKER, at);
                int next = nextImage < 0 ? nextVideo : nextVideo < 0 ? nextImage : Math.min(nextImage, nextVideo);
                if (next < 0) {
                    out.append(text, at, text.length());
                    break;
                }
                out.append(text, at, next);
                if (next == nextImage) {
                    int[] g = images.get(imageIndex++).grid();
                    out.append(VISION_START).append(IMAGE_MARKER.repeat(g[0] * g[1] * g[2] / merge2)).append(VISION_END);
                    at = next + IMAGE_MARKER.length();
                } else {
                    int[] g = clips.get(videoIndex).grid();
                    double[] times = clipTimes.get(videoIndex++);
                    int perFrame = g[1] * g[2] / merge2;
                    int tps = vision.args().temporalPatchSize();
                    for (int t = 0; t < g[0]; t++) {
                        double ts = times[Math.min(t * tps, times.length - 1)];
                        out.append("]<]").append(seconds(ts)).append(" seconds[>[");
                        out.append(VISION_START).append(VIDEO_MARKER.repeat(perFrame)).append(VISION_END);
                    }
                    at = next + VIDEO_MARKER.length();
                }
            }
            int[] ids = tokenizer.encodeWithSpecials(out.toString());
            Tensor imagePixels = images.isEmpty() ? null : stack(images);
            Tensor videoPixels = clips.isEmpty() ? null : stack(clips);
            return new MiniMaxMultimodal(ids, imagePixels, grids(images), videoPixels, grids(clips));
        } finally {
            for (var p : images) {
                p.pixels().close();
            }
            for (var p : clips) {
                p.pixels().close();
            }
        }
    }

    private static int count(String text, String marker) {
        int n = 0;
        for (int i = text.indexOf(marker); i >= 0; i = text.indexOf(marker, i + marker.length())) {
            n++;
        }
        return n;
    }

    private static Tensor stack(List<MiniMaxVisionProcessor.Patches> items) {
        if (items.size() == 1) {
            return items.get(0).pixels().copy();
        }
        return Tensor.vstack(items.stream().map(MiniMaxVisionProcessor.Patches::pixels).toArray(Tensor[]::new));
    }

    private static int[][] grids(List<MiniMaxVisionProcessor.Patches> items) {
        return items.stream().map(MiniMaxVisionProcessor.Patches::grid).toArray(int[][]::new);
    }

    @Override
    public Tensor prefillMultimodal(int requestId, MultimodalPrompt prompt, int from, int to) {
        if (!(prompt instanceof MiniMaxMultimodal mm)) {
            throw new IllegalArgumentException("a MiniMax-M3 multimodal prompt is required");
        }
        if (vision == null) {
            throw new IllegalStateException("vision encoder not loaded");
        }
        if (cluster != null) {
            // the followers would never join the collectives of this prefill: refuse rather than hang
            throw new UnsupportedOperationException("images and videos are not supported on a multi-node model yet");
        }
        int[] ids = mm.inputIds();
        if (from < 0 || to < from || to > ids.length) {
            throw new IllegalArgumentException("invalid prefill range [" + from + ", " + to + ") for len " + ids.length);
        }
        if (from == to) {
            return null;
        }
        final int imageId = vision.args().imageTokenId();
        final int videoId = vision.args().videoTokenId();
        int imageTokens = 0;
        int videoTokens = 0;
        for (int id : ids) {
            if (id == imageId) {
                imageTokens++;
            } else if (id == videoId) {
                videoTokens++;
            }
        }
        int merge2 = vision.args().mergeSize() * vision.args().mergeSize();
        if (imageTokens != patchCount(mm.imageGrids()) / merge2 || videoTokens != patchCount(mm.videoGrids()) / merge2) {
            throw new IllegalArgumentException("prompt has " + imageTokens + " image and " + videoTokens
                    + " video placeholders but the media provide "
                    + patchCount(mm.imageGrids()) / merge2 + " and " + patchCount(mm.videoGrids()) / merge2);
        }
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestId);
        }
        multimodalRequests.add(requestId);

        // 1. Encode all media on rank 0 (images first, then videos), 2. every rank prefills window by window
        // with the placeholder rows replaced by its copy of those embeddings.
        Tensor[] encoded = onRanks(r -> {
            if (r != 0) {
                return null;
            }
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Device device = models[0].device();
                    ScalarType dtype = models[0].dtype();
                    List<Tensor> parts = new ArrayList<>();
                    if (mm.imagePixels() != null) {
                        parts.add(vision.forward(mm.imagePixels().to(device, dtype), mm.imageGrids()));
                    }
                    if (mm.videoPixels() != null) {
                        parts.add(vision.forward(mm.videoPixels().to(device, dtype), mm.videoGrids()));
                    }
                    Tensor all = parts.size() == 1 ? parts.get(0).copy() : Tensor.vstack(parts.toArray(Tensor[]::new));
                    all.detachFromScopes();
                    return all;
                } finally {
                    Tensor.pop();
                }
            }
        });
        Tensor embeddings = firstAndClose(encoded);
        try {
            int imageBefore = 0;
            int videoBefore = 0;
            for (int j = 0; j < from; j++) {
                if (ids[j] == imageId) {
                    imageBefore++;
                } else if (ids[j] == videoId) {
                    videoBefore++;
                }
            }
            final int imageStart = imageBefore;
            final int videoStart = videoBefore;
            final int imageCount = imageTokens;
            Tensor[] logits = onRanks(r -> {
                try (var scope = new AutoScope()) {
                    Tensor.push(scope);
                    try {
                        Device device = models[r].device();
                        Tensor mine = embeddings == null ? null : embeddings.to(device);
                        int img = imageStart;
                        int vid = videoStart;
                        Tensor last = null;
                        for (int a = from; a < to; a += MULTIMODAL_CHUNK) {
                            int b = Math.min(to, a + MULTIMODAL_CHUNK);
                            int[] window = Arrays.copyOfRange(ids, a, b);
                            long[] rows = new long[window.length];
                            long[] source = new long[window.length];
                            int n = 0;
                            for (int j = 0; j < window.length; j++) {
                                if (window[j] == imageId) {
                                    rows[n] = j;
                                    source[n++] = img++;
                                } else if (window[j] == videoId) {
                                    rows[n] = j;
                                    source[n++] = imageCount + vid++;
                                }
                            }
                            Tensor tokens = Tensor.of(window).reshape(1, window.length).to(device);
                            Tensor out;
                            if (n == 0) {
                                out = models[r].forward(tokens, a, false);
                            } else {
                                long[] usedRows = Arrays.copyOf(rows, n);
                                Tensor sourceIndex = Tensor.of(Arrays.copyOf(source, n)).to(device);
                                Tensor picked = mine.get(sourceIndex);
                                out = models[r].forward(tokens, a, false, usedRows, picked);
                            }
                            if (b == to && to == ids.length) {
                                last = out.reshape(1, -1).copy();
                            }
                        }
                        if (last == null) {
                            return null;
                        }
                        last.detachFromScopes();
                        return last;
                    } finally {
                        Tensor.pop();
                    }
                }
            });
            Tensor row = firstAndClose(logits);
            if (row == null) {
                return null;
            }
            Tensor owned = row.copy();
            row.close();
            return owned;
        } finally {
            if (embeddings != null) {
                embeddings.close();
            }
        }
    }

    private static long patchCount(int[][] grids) {
        long n = 0;
        for (int[] g : grids) {
            n += (long) g[0] * g[1] * g[2];
        }
        return n;
    }

    @Override
    public int[] encodeChat(Message... dialog) {
        return encodeChat(dialog, null);
    }

    @Override
    public int[] encodeChat(Message[] dialog, ChatOptions options) {
        if (dialog == null || dialog.length == 0) {
            throw new IllegalArgumentException("No messages provided.");
        }
        for (Message m : dialog) {
            if (m.hasMedia()) {
                throw new IllegalArgumentException("MiniMax-M3 image, video and audio input is not supported yet");
            }
        }
        String prompt = MiniMaxChatTemplate.forOptions(options).encode(Arrays.asList(dialog), options);
        return tokenizer.encodeWithSpecials(prompt);
    }

    /**
     * Encodes plain text (no chat template, no BOS/EOS).
     * @param text the text.
     * @return token ids.
     */
    public int[] encode(String text) {
        return tokenizer.encode(text);
    }

    /**
     * Returns how linear weights are stored and multiplied.
     * @return the weight mode.
     */
    public MiniMaxWeightMode weightMode() {
        return params.weightMode();
    }

    /**
     * Enables or disables radix KV prefix reuse on every rank. The MSA index keys live in side caches
     * addressed by the same slots, so shared prefixes reuse them too.
     *
     * @param enabled {@code true} to reuse cached prefixes.
     */
    public void setPrefixReuseEnabled(boolean enabled) {
        ClusterLeader c = cluster;
        if (c != null) {
            c.send(OP_PREFIX_REUSE, o -> o.writeBoolean(enabled));
        }
        for (MiniMaxModel m : models) {
            m.kvCachePool().setPrefixReuseEnabled(enabled);
        }
    }

    @Override
    public LanguageModel model() {
        return this;
    }

    @Override
    public KvCachePool kvCachePool() {
        return model.kvCachePool();
    }

    @Override
    public int padToken() {
        return tokenizer.pad();
    }

    @Override
    public int[] stopTokens() {
        return tokenizer.stopTokens();
    }

    @Override
    public String decode(int[] tokens) {
        return tokenizer.decode(tokens);
    }

    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial)
            throws java.nio.charset.CharacterCodingException {
        return tokenizer.tryDecode(tokens, skipSpecial);
    }

    @Override
    public int bind(int[] prompt, int totalCapacity) {
        ClusterLeader c = cluster;
        if (c == null) {
            return bindLocal(prompt, totalCapacity);
        }
        long msg = c.send(OP_BIND, o -> {
            writeInts(o, prompt);
            o.writeInt(totalCapacity);
        });
        int id = bindLocal(prompt, totalCapacity);
        c.awaitAck(msg);   // binds are rare: confirm the followers agree on the request id
        for (int i = 0; i < c.followers(); i++) {
            int theirs = c.valueOf(i, msg);
            if (theirs != id) {
                throw new IllegalStateException("KV request id mismatch across nodes: leader=" + id
                        + " follower " + i + "=" + theirs);
            }
        }
        return id;
    }

    private int bindLocal(int[] prompt, int totalCapacity) {
        int id = -1;
        for (MiniMaxModel m : models) {
            int local = m.kvCachePool().bindRequest(prompt, totalCapacity);
            if (id < 0) {
                id = local;
            } else if (local != id) {
                throw new IllegalStateException(String.format(
                        "KV request id mismatch across ranks: rank0=%d other=%d", id, local));
            }
        }
        if (draft != null) {
            draftCapacity.put(id, totalCapacity);
            adoptDraftPrefix(id, prompt, totalCapacity);
        }
        return id;
    }

    /** Layers whose output hidden states prefill and decode capture for a draft model, or {@code null}. */
    private int[] auxLayers;
    /** Rank 0's taps from the last prefill chunk / eager decode step, until {@link #takeAux()}. */
    private volatile Tensor lastAux;

    /**
     * Captures the hidden states leaving the decoder layers {@code layerIds} on every following prefill chunk and eager decode step
     * (CUDA-graph decode is bypassed while enabled); fetch them with {@link #takeAux()}. Used by the DSpark draft
     * model ({@code target_layer_ids}). Not supported on a multi-node cluster yet.
     *
     * @param layerIds strictly increasing layer indices, or {@code null} / empty to disable.
     */
    public void setAuxLayers(int... layerIds) {
        if (layerIds != null && layerIds.length > 0 && cluster != null) {
            throw new UnsupportedOperationException("aux hidden-state capture is not supported on a cluster");
        }
        for (MiniMaxModel m : models) {
            m.setAuxLayers(layerIds);
        }
        auxLayers = layerIds == null || layerIds.length == 0 ? null : layerIds.clone();
        Tensor stale = takeAux(null);
        if (stale != null) {
            stale.close();
        }
    }

    /**
     * Returns the hidden states of the last prefill chunk or decode step: {@code [rows, numAuxLayers * dim]}, one
     * row per token fed to the model (in order, batch-major), identical on all ranks. The caller owns the tensor.
     *
     * @return the taps, or {@code null} if none were captured since the last call.
     */
    public Tensor takeAux() {
        return takeAux(null);
    }

    private Tensor takeAux(Tensor replacement) {
        Tensor old = lastAux;
        lastAux = replacement;
        if (replacement == null) {
            return old;
        }
        if (old != null) {
            old.close();
        }
        return null;
    }

    /** Moves each rank's taps out of its model: rank 0 keeps them for {@link #takeAux()}, the others free theirs. */
    private void stashAux(int rank) {
        Tensor aux = models[rank].takeAux();
        if (aux == null) {
            return;
        }
        if (rank == 0) {
            takeAux(aux);
        } else {
            aux.close();
        }
    }

    // ------------------------------------------------------------------ DSpark speculative decoding

    /** The DSpark block drafter on rank 0's device, or {@code null}. */
    private MiniMaxDspark draft;
    private volatile boolean speculativeEnabled = true;
    private volatile int numSpeculativeTokens = 7;
    /** Per request: the next position the draft's context expects, i.e. how many positions of taps it has absorbed. */
    private final Map<Integer, Integer> draftPosition = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, Integer> draftCapacity = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong speculativeRounds = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong speculativeProposed = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong speculativeAccepted = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong speculativePlain = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong draftNanos = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong verifyNanos = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Installs the DSpark drafter and starts capturing the target taps it consumes.
     *
     * @param drafter the loaded draft (owned by this model from now on), or {@code null} to remove it.
     */
    void setDraft(MiniMaxDspark drafter) {
        this.draft = drafter;
        setAuxLayers(drafter == null ? null : drafter.args().targetLayerIds());
    }

    /**
     * Builds the target-head callbacks of a draft loaded before this model exists.
     *
     * @param self filled with the model once it is built.
     * @return callbacks running the embedding / output head on all ranks and returning rank 0's result.
     */
    static MiniMaxDspark.TargetHeads headsOf(java.util.concurrent.atomic.AtomicReference<MiniMax> self) {
        return new MiniMaxDspark.TargetHeads() {
            @Override
            public Tensor embed(Tensor ids) {
                return self.get().embedRank0(ids);
            }

            @Override
            public Tensor logits(Tensor hidden) {
                return self.get().logitsRank0(hidden);
            }
        };
    }

    // The inputs of the collective calls below are made per rank on the calling thread, before any rank launches its
    // all-reduce: a rank copying a tensor from rank 0's device inside its own thread would queue that copy on rank
    // 0's stream behind rank 0's already-launched all-reduce, which waits for the very rank that waits for the copy.

    private Tensor embedRank0(Tensor ids) {
        long[] host;
        try (Tensor cpu = ids.to(Device.CPU()).copy()) {
            host = cpu.longArray();
        }
        Tensor[] out = onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Tensor local = Tensor.of(host).to(models[r].device());
                    Tensor e = models[r].embedIds(local);
                    e.detachFromScopes();
                    return e;
                } finally {
                    Tensor.pop();
                }
            }
        });
        return firstAndClose(out);
    }

    private Tensor logitsRank0(Tensor hidden) {
        Tensor[] inputs = new Tensor[models.length];
        for (int r = 0; r < inputs.length; r++) {
            inputs[r] = r == 0 ? hidden : hidden.to(models[r].device());
        }
        try {
            Tensor[] out = onRanks(r -> {
                try (var scope = new AutoScope()) {
                    Tensor.push(scope);
                    try {
                        Tensor l = models[r].headLogits(inputs[r]);
                        l.detachFromScopes();
                        return l;
                    } finally {
                        Tensor.pop();
                    }
                }
            });
            return firstAndClose(out);
        } finally {
            for (int r = 1; r < inputs.length; r++) {
                inputs[r].close();
            }
        }
    }

    /**
     * Switches the fused sparse-attention kernels on or off and drops the captured graphs (diagnostics: the fallback is
     * the reference masked-SDPA path, so the two can be compared on the same state).
     *
     * @param enabled whether the fused kernels may be used.
     */
    public void setSparseKernel(boolean enabled) {
        MiniMaxAttention.setSparseKernel(enabled);
        for (MiniMaxModel m : models) {
            m.closeGraphs();
        }
    }

    /**
     * Returns whether DSpark speculative decoding is loaded and enabled.
     * @return {@code true} when {@link #speculateStep} drafts.
     */
    public boolean isSpeculativeEnabled() {
        return speculativeEnabled && draft != null && cluster == null;
    }

    /**
     * Enables or disables speculation (the draft stays loaded).
     * @param enabled whether {@link #speculateStep} drafts.
     */
    public void setSpeculativeEnabled(boolean enabled) {
        this.speculativeEnabled = enabled;
    }

    /**
     * Sets the most drafts verified per round; clamped to the draft's block size.
     * @param n draft tokens per round; {@code <= 0} selects the block size.
     */
    public void setNumSpeculativeTokens(int n) {
        int block = draft == null ? 7 : draft.args().blockSize();
        this.numSpeculativeTokens = n <= 0 ? block : Math.min(n, block);
    }

    /**
     * Returns the most drafts verified per round.
     * @return draft tokens per round.
     */
    public int numSpeculativeTokens() {
        return numSpeculativeTokens;
    }

    /**
     * Returns the number of draft + verify rounds run so far.
     * @return rounds.
     */
    public long speculativeRounds() {
        return speculativeRounds.get();
    }

    /**
     * Returns the number of plain decode steps taken by requests that could not speculate.
     * @return plain steps.
     */
    public long speculativePlainSteps() {
        return speculativePlain.get();
    }

    /**
     * Returns a summary of the speculation counters.
     * @return rounds, accepted drafts per round, share of proposed drafts accepted, plain fallbacks and time split.
     */
    public String speculativeStats() {
        long rounds = speculativeRounds.get();
        long proposed = speculativeProposed.get();
        return String.format("rounds=%d accepted/round=%.2f accept-rate=%.2f plain-steps=%d draft-ms/round=%.1f verify-ms/round=%.1f",
                rounds, rounds == 0 ? 0.0 : (double) speculativeAccepted.get() / rounds,
                proposed == 0 ? 0.0 : (double) speculativeAccepted.get() / proposed, speculativePlain.get(),
                rounds == 0 ? 0.0 : draftNanos.get() / 1e6 / rounds, rounds == 0 ? 0.0 : verifyNanos.get() / 1e6 / rounds);
    }

    /** Whether the taps of {@code requestId} are being fed to the draft, and a chunk starting at {@code from} continues them. */
    private boolean draftTracks(int requestId, int from) {
        return from == 0 || draftPosition.getOrDefault(requestId, -1) == from;
    }

    /** Feeds the taps of a prefill chunk {@code [from, to)} to the draft. */
    private void absorbPrefill(int requestId, int from, int to, boolean captured) {
        Tensor aux = captured ? takeAux() : null;
        if (aux == null) {
            draftPosition.remove(requestId);   // a gap in the taps (prefix hit, multimodal): the request cannot speculate
            return;
        }
        try {
            if (from == 0) {
                draft.bind(requestId, draftCapacity.getOrDefault(requestId, params.maxSeqLen()));
            }
            draft.absorb(requestId, aux, from);
            draftPosition.put(requestId, to);
        } finally {
            aux.close();
        }
    }

    /** A finished request's draft context, kept for later requests that share its prefix. */
    private record DraftSnapshot(int[] tokens, MiniMaxDspark.Cache context) { }

    private static final int MAX_DRAFT_SNAPSHOTS = Math.max(0, Integer.parseInt(
            System.getenv().getOrDefault("SMILE_MINIMAX_DSPARK_SNAPSHOTS", "8").trim()));
    /** Most recently used last. */
    private final java.util.LinkedList<DraftSnapshot> draftSnapshots = new java.util.LinkedList<>();

    /**
     * Keeps a finished request's draft context when it is complete enough to serve as the prefix of a later request.
     * Returns {@code false} when the request had no usable context (the caller releases it).
     */
    private boolean snapshotDraft(int requestId, int[] sequence) {
        Integer absorbed = draftPosition.get(requestId);
        if (MAX_DRAFT_SNAPSHOTS == 0 || draft == null || sequence == null || absorbed == null
                || absorbed < 16 || absorbed > sequence.length) {
            return false;
        }
        MiniMaxDspark.Cache context = draft.detach(requestId);
        if (context == null) {
            return false;
        }
        if (absorbed > draft.ringOf(context)) {
            draft.discard(context);   // the ring wrapped: the early positions are gone
        } else {
            int[] tokens = Arrays.copyOf(sequence, absorbed);
            // A snapshot whose tokens are a prefix of another one adds nothing: keep only the longer.
            boolean covered = false;
            for (var it = draftSnapshots.iterator(); it.hasNext(); ) {
                DraftSnapshot other = it.next();
                int[] t = other.tokens();
                int common = Arrays.mismatch(t, tokens);
                if (common < 0) {
                    common = Math.min(t.length, tokens.length);
                }
                if (common == tokens.length && t.length >= tokens.length) {
                    covered = true;                        // the new one is a prefix of an existing one
                    it.remove();
                    draftSnapshots.addLast(other);         // refresh its recency (the iterator is abandoned below)
                    break;
                }
                if (common == t.length) {                  // the existing one is a prefix of the new one
                    it.remove();
                    draft.discard(other.context());
                }
            }
            if (covered) {
                draft.discard(context);
            } else {
                draftSnapshots.addLast(new DraftSnapshot(tokens, context));
                while (draftSnapshots.size() > MAX_DRAFT_SNAPSHOTS) {
                    draft.discard(draftSnapshots.removeFirst().context());
                }
            }
        }
        draftPosition.remove(requestId);
        draftCapacity.remove(requestId);
        return true;
    }

    /**
     * Gives a freshly bound request the draft context of an earlier request with the same prefix, so it can speculate
     * although prefill skips the positions the KV cache already holds. The request resumes prefill at
     * {@code min(matched, promptLen - 1)}, which is where its draft context must end.
     */
    private void adoptDraftPrefix(int requestId, int[] prompt, int totalCapacity) {
        if (draft == null || !speculativeEnabled || draftSnapshots.isEmpty()) {
            return;
        }
        int matched = model.kvCachePool().matchedPrefixLen(requestId);
        int resume = Math.min(matched, prompt.length - 1);
        if (resume < 1) {
            return;
        }
        DraftSnapshot best = null;
        for (DraftSnapshot snapshot : draftSnapshots) {
            int[] t = snapshot.tokens();
            int common = 0;
            int limit = Math.min(Math.min(t.length, prompt.length), resume);
            while (common < limit && t[common] == prompt[common]) {
                common++;
            }
            if (common >= resume) {
                best = snapshot;
                break;
            }
        }
        if (best != null && draft.adopt(requestId, totalCapacity, best.context(), resume)) {
            draftPosition.put(requestId, resume);
            draftCapacity.put(requestId, totalCapacity);
            draftSnapshots.remove(best);
            draftSnapshots.addLast(best);   // most recently used
        }
    }

    private void releaseDraft(int requestId) {
        draftCapacity.remove(requestId);
        if (draftPosition.remove(requestId) != null && draft != null) {
            draft.release(requestId);
        }
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions,
                                 int numDrafts, double temperature, double topp) {
        if (!isSpeculativeEnabled()) {
            throw new UnsupportedOperationException("DSpark speculation not available");
        }
        if (requestIds == null || lastTokens == null || positions == null
                || lastTokens.length != requestIds.length || positions.length != requestIds.length
                || requestIds.length == 0) {
            throw new IllegalArgumentException("speculateStep batch sizes must match and be non-empty");
        }
        if (numDrafts < 1) {
            throw new IllegalArgumentException("numDrafts must be >= 1");
        }
        int n = Math.min(numDrafts, draft.args().blockSize());
        int[][] out = new int[requestIds.length][];
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < requestIds.length; i++) {
            Integer absorbed = draftPosition.get(requestIds[i]);
            if (absorbed != null && absorbed == positions[i]) {
                ready.add(i);
            } else {
                // No usable draft context (prefix hit without a snapshot, multimodal, a gap): an ordinary step.
                out[i] = plainStep(requestIds[i], lastTokens[i], positions[i], temperature, topp);
            }
        }
        if (ready.size() == 1) {
            int i = ready.get(0);
            out[i] = speculateOne(requestIds[i], lastTokens[i], positions[i], n, temperature, topp);
        } else if (ready.size() > 1) {
            speculateBatch(ready, requestIds, lastTokens, positions, n, temperature, topp, out);
        }
        return out;
    }

    /** Drafts one block for a request whose draft context is complete up to {@code pos}. */
    private int[] draftBlock(int requestId, int anchor, int pos, int n) {
        return Arrays.copyOf(draft.draft(requestId, anchor, pos, false).tokens(), n);
    }

    /** One draft + verify round of one request. */
    private int[] speculateOne(int requestId, int anchor, int pos, int n, double temperature, double topp) {
        long t0 = System.nanoTime();
        int[] drafts = draftBlock(requestId, anchor, pos, n);
        long t1 = System.nanoTime();
        int[] accepted = verifyOne(requestId, anchor, pos, drafts, temperature, topp);
        draftNanos.addAndGet(t1 - t0);
        verifyNanos.addAndGet(System.nanoTime() - t1);
        return accepted;
    }

    /**
     * Verifies one request's block in a single window forward (CUDA graph when possible), accepts the matching prefix
     * and feeds the committed positions' taps to the draft.
     */
    private int[] verifyOne(int requestId, int anchor, int pos, int[] drafts, double temperature, double topp) {
        int n = drafts.length;
        int[] window = new int[n + 1];
        window[0] = anchor;
        System.arraycopy(drafts, 0, window, 1, n);

        Tensor rows = windowRows(requestId, window, pos);
        Tensor aux = takeAux();
        try {
            return accept(requestId, pos, drafts, rows, aux, temperature, topp);
        } finally {
            rows.close();
            if (aux != null) {
                aux.close();
            }
        }
    }

    /**
     * Runs a verify window of one request on every rank (CUDA graph when possible) and returns rank 0's logits for all rows
     * {@code [window.length, vocab]}; the aux taps are left for {@link #takeAux()}.
     */
    private Tensor windowRows(int requestId, int[] window, int pos) {
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestId);
        }
        Tensor[] logits = onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    long[] ids = new long[window.length];
                    for (int i = 0; i < ids.length; i++) {
                        ids[i] = window[i];
                    }
                    Tensor graphed = models[r].windowGraph(ids, new int[]{pos}, window.length);
                    if (graphed != null) {
                        stashAux(r);
                        graphed.detachFromScopes();
                        return graphed;
                    }
                    Tensor tokens = Tensor.of(window).reshape(1, window.length).to(models[r].device());
                    Tensor out = models[r].forwardAux(tokens, pos, true, null, null);
                    stashAux(r);
                    Tensor rows = out.reshape(window.length, -1).copy();
                    rows.detachFromScopes();
                    return rows;
                } finally {
                    Tensor.pop();
                }
            }
        });
        return firstAndClose(logits);
    }

    /**
     * Scores {@code window} (consecutive tokens starting at {@code pos}) in one verify-window forward and returns the
     * logits of every row, {@code [window.length, vocab]} float32 (the caller owns it). Writes the window's KV like a
     * verify does. For tests and diagnostics; needs a loaded draft (the window path captures its taps).
     *
     * @param requestId a request bound with {@link #bind} and prefilled up to {@code pos}.
     * @param window    tokens at positions {@code pos ..}.
     * @param pos       absolute position of {@code window[0]}.
     * @return the logits.
     */
    public Tensor windowLogits(int requestId, int[] window, int pos) {
        if (draft == null || auxLayers == null) {
            throw new IllegalStateException("windowLogits needs the DSpark draft (aux taps)");
        }
        Tensor rows = windowRows(requestId, window, pos);
        Tensor aux = takeAux();
        if (aux != null) {
            aux.close();
        }
        return rows;
    }

    /**
     * Samples the verify rows of one request, accepts the matching drafts and absorbs the committed taps.
     *
     * @param rows target logits of the request's window {@code [n + 1, vocab]}.
     * @param aux  taps of the request's window rows {@code [n + 1, width]}, or {@code null}.
     */
    private int[] accept(int requestId, int pos, int[] drafts, Tensor rows, Tensor aux, double temperature, double topp) {
        int[] targetSamples = smile.llm.engine.Sampling.sampleTokenIds(rows, temperature, topp);
        var accept = smile.llm.engine.SpeculativeDecoding.acceptGreedy(drafts, targetSamples);
        int accepted = accept.numDraftAccepted();
        if (aux == null) {
            draftPosition.remove(requestId);
        } else {
            try (var noGrad = Tensor.noGradGuard();
                 var keep = smile.deep.tensor.Index.slice(0, accepted + 1);
                 Tensor committed = aux.get(keep, smile.deep.tensor.Index.Colon)) {
                // Rows 0..accepted are the anchor and the accepted drafts, now committed; the bonus token is next round's anchor.
                draft.absorb(requestId, committed, pos);
                draftPosition.put(requestId, pos + accepted + 1);
            }
        }
        speculativeRounds.incrementAndGet();
        speculativeProposed.addAndGet(drafts.length);
        speculativeAccepted.addAndGet(accepted);
        return accept.acceptedTokens();
    }

    /**
     * One round for several requests at once: a block is drafted per request, then all windows are verified in one
     * batched CUDA-graph forward (the MoE sees every row of every request together). Falls back to verifying the
     * requests one by one when no graph applies.
     */
    private void speculateBatch(List<Integer> ready, int[] requestIds, int[] lastTokens, int[] positions, int n,
                                double temperature, double topp, int[][] out) {
        int k = ready.size();
        int s = n + 1;
        long t0 = System.nanoTime();
        int[][] drafts = new int[k][];
        int[] ids = new int[k];
        int[] pos = new int[k];
        long[] tokens = new long[k * s];
        for (int j = 0; j < k; j++) {
            int i = ready.get(j);
            ids[j] = requestIds[i];
            pos[j] = positions[i];
            drafts[j] = draftBlock(ids[j], lastTokens[i], pos[j], n);
            tokens[j * s] = lastTokens[i];
            for (int d = 0; d < n; d++) {
                tokens[j * s + 1 + d] = drafts[j][d];
            }
        }
        long t1 = System.nanoTime();
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(ids);
        }
        Tensor[] logits = onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Tensor graphed = models[r].windowGraph(tokens, pos, s);
                    if (graphed == null) {
                        return null;
                    }
                    stashAux(r);
                    graphed.detachFromScopes();
                    return graphed;
                } finally {
                    Tensor.pop();
                }
            }
        });
        Tensor rows = firstAndClose(logits);
        if (rows == null) {
            takeAux();   // nothing was captured
            for (int j = 0; j < k; j++) {
                int i = ready.get(j);
                out[i] = verifyOne(ids[j], lastTokens[i], pos[j], drafts[j], temperature, topp);
            }
            draftNanos.addAndGet(t1 - t0);
            return;
        }
        Tensor aux = takeAux();
        try (var noGrad = Tensor.noGradGuard()) {
            for (int j = 0; j < k; j++) {
                try (var window = smile.deep.tensor.Index.slice(j * s, (j + 1) * s);
                     Tensor logitRows = rows.get(window, smile.deep.tensor.Index.Colon);
                     Tensor tapRows = aux == null ? null : aux.get(window, smile.deep.tensor.Index.Colon)) {
                    out[ready.get(j)] = accept(ids[j], pos[j], drafts[j], logitRows, tapRows, temperature, topp);
                }
            }
        } finally {
            rows.close();
            if (aux != null) {
                aux.close();
            }
        }
        draftNanos.addAndGet(t1 - t0);
        verifyNanos.addAndGet(System.nanoTime() - t1);
    }

    /** One ordinary decode step for a request that cannot speculate (or has no room); keeps its taps when it can. */
    private int[] plainStep(int requestId, int token, int pos, double temperature, double topp) {
        Integer absorbed = draftPosition.get(requestId);
        boolean capture = draft != null && auxLayers != null && absorbed != null && absorbed == pos;
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestId);
        }
        Tensor[] logits = decodeLocal(new int[]{requestId}, new int[]{token}, new int[]{pos}, capture);
        Tensor rows = firstAndClose(logits);
        int sampled;
        try {
            sampled = smile.llm.engine.Sampling.sampleTokenIds(rows, temperature, topp)[0];
        } finally {
            rows.close();
        }
        if (capture) {
            Tensor aux = takeAux();
            if (aux == null) {
                draftPosition.remove(requestId);
            } else {
                try {
                    draft.absorb(requestId, aux, pos);
                    draftPosition.put(requestId, pos + 1);
                } finally {
                    aux.close();
                }
            }
        }
        speculativePlain.incrementAndGet();
        return new int[]{sampled};
    }

    @Override
    public int prefixLen(int requestId) {
        return model.kvCachePool().matchedPrefixLen(requestId);
    }

    @Override
    public Tensor prefill(int requestId, int[] prompt, int prefixLen) {
        return prefillChunk(requestId, prompt, prefixLen, prompt.length);
    }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        if (from < 0 || to < from || to > prompt.length) {
            throw new IllegalArgumentException(
                    "invalid prefill range [" + from + ", " + to + ") for len " + prompt.length);
        }
        if (from == to) {
            return null;
        }
        int[] window = Arrays.copyOfRange(prompt, from, to);
        boolean last = to == prompt.length;
        ClusterLeader c = cluster;
        if (c != null) {
            c.send(OP_PREFILL, o -> {
                o.writeInt(requestId);
                o.writeInt(from);
                o.writeBoolean(last);
                writeInts(o, window);
            });
        }
        boolean capture = auxLayers != null && (draft == null || (speculativeEnabled && draftTracks(requestId, from)));
        Tensor[] logits = prefillWindow(requestId, window, from, last, capture);
        if (draft != null) {
            absorbPrefill(requestId, from, to, capture);
        }
        Tensor row = firstAndClose(logits);
        if (row != null) {
            // Hand ownership to the caller's scope (a no-op without one).
            Tensor owned = row.copy();
            row.close();
            return owned;
        }
        return null;
    }

    /** Runs one prefill window on this process's ranks; each rank's last-position logits when {@code last}. */
    private Tensor[] prefillWindow(int requestId, int[] window, int from, boolean last, boolean capture) {
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestId);
        }
        return onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Tensor tokens = Tensor.of(window).reshape(1, window.length).to(models[r].device());
                    Tensor out = capture
                            ? models[r].forwardAux(tokens, from, false, null, null)
                            : models[r].forward(tokens, from, false);
                    if (capture) {
                        stashAux(r);
                    }
                    if (!last) {
                        return null;
                    }
                    Tensor row = out.reshape(1, -1).copy();
                    row.detachFromScopes();
                    return row;
                } finally {
                    Tensor.pop();
                }
            }
        });
    }

    /**
     * Prefills {@code ids} from position 0 and returns rank 0's logits for every position
     * ({@code [1, n, vocab]}, float32). For tests and numerical comparison; the caller owns the tensor.
     *
     * @param requestId a request bound with {@link #bind}.
     * @param ids       the prompt token ids.
     * @return logits for all positions.
     */
    Tensor forwardAllLogits(int requestId, int[] ids) {
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestId);
        }
        Tensor[] logits = onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Tensor tokens = Tensor.of(ids).reshape(1, ids.length).to(models[r].device());
                    Tensor out = models[r].forward(tokens, 0, true).copy();
                    out.detachFromScopes();
                    return out;
                } finally {
                    Tensor.pop();
                }
            }
        });
        return firstAndClose(logits);
    }

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        if (requestIds == null || lastTokens == null || positions == null) {
            throw new IllegalArgumentException("decodeStep args must not be null");
        }
        int b = requestIds.length;
        if (lastTokens.length != b || positions.length != b || b == 0) {
            throw new IllegalArgumentException("decodeStep batch sizes must match and be non-empty");
        }
        ClusterLeader c = cluster;
        if (c != null) {
            c.send(OP_DECODE, o -> {
                writeInts(o, requestIds);
                writeInts(o, lastTokens);
                writeInts(o, positions);
            });
        }
        Tensor[] logits = decodeLocal(requestIds, lastTokens, positions, false);
        Tensor rows = firstAndClose(logits);
        Tensor owned = rows.copy();
        rows.close();
        return owned;
    }

    /**
     * Runs one decode step on this process's ranks; each rank's logits {@code [batch, vocab]}. With {@code capture} the
     * step runs eagerly and the aux taps are left for {@link #takeAux()}.
     */
    private Tensor[] decodeLocal(int[] requestIds, int[] lastTokens, int[] positions, boolean capture) {
        int b = requestIds.length;
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestIds);
        }
        long[] toks = new long[b];
        for (int i = 0; i < b; i++) {
            toks[i] = lastTokens[i];
        }
        return onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Tensor graphed = capture ? null : models[r].decodeGraph(toks, positions);
                    if (graphed != null) {
                        graphed.detachFromScopes();
                        return graphed;
                    }
                    Tensor tokens = Tensor.of(toks).reshape(b, 1).to(models[r].device());
                    Tensor out = capture
                            ? models[r].forwardAux(tokens, positions)
                            : models[r].forward(tokens, positions);
                    if (capture) {
                        stashAux(r);
                    }
                    Tensor rows = out.reshape(b, -1).copy();
                    rows.detachFromScopes();
                    return rows;
                } finally {
                    Tensor.pop();
                }
            }
        });
    }

    @Override
    public void finish(int requestId, int[] sequenceTokens) {
        ClusterLeader c = cluster;
        if (c != null) {
            c.send(OP_FINISH, o -> {
                o.writeInt(requestId);
                writeInts(o, sequenceTokens == null ? new int[0] : sequenceTokens);
                o.writeBoolean(sequenceTokens != null);
            });
        }
        finishLocal(requestId, sequenceTokens);
    }

    private void finishLocal(int requestId, int[] sequenceTokens) {
        boolean media = multimodalRequests.remove(requestId);
        if (media || !snapshotDraft(requestId, sequenceTokens)) {
            releaseDraft(requestId);
        }
        for (MiniMaxModel m : models) {
            if (media) {
                m.kvCachePool().unbindRequest(requestId);   // placeholder ids must not become a reusable prefix
            } else {
                m.kvCachePool().finishRequest(requestId, sequenceTokens);
            }
        }
    }

    @Override
    public void evict(int requestId) {
        ClusterLeader c = cluster;
        if (c != null) {
            c.send(OP_EVICT, o -> o.writeInt(requestId));
        }
        evictLocal(requestId);
    }

    private void evictLocal(int requestId) {
        releaseDraft(requestId);
        multimodalRequests.remove(requestId);
        for (MiniMaxModel m : models) {
            m.kvCachePool().unbindRequest(requestId);
        }
    }

    @Override
    public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature, double topp,
                                   boolean logprobs, long seed, GenerationListener listener,
                                   BooleanSupplier cancelRequested) {
        if (prompt == null || prompt.length == 0) {
            throw new IllegalArgumentException("prompt must not be empty");
        }
        if (logprobs) {
            throw new UnsupportedOperationException("logprobs are not supported yet");
        }
        for (int token : prompt) {
            if (token < 0 || token >= params.vocabSize()) {
                throw new IllegalArgumentException(
                        "Prompt token id " + token + " out of range for vocab_size " + params.vocabSize());
            }
        }
        int promptLen = prompt.length;
        if (promptLen > params.maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }
        int maxGen = Math.max(0, Math.min(maxGenLen, params.maxSeqLen() - promptLen));
        if (seed != 0) {
            smile_torch_h.smile_manual_seed(seed);
        }

        int capacity = promptLen + maxGen;
        int id = bind(prompt, capacity);
        List<Integer> out = new ArrayList<>();
        boolean stopped = false;
        try (var guard = Tensor.noGradGuard()) {
            int cached = Math.min(prefixLen(id), promptLen - 1);
            if (listener != null) {
                listener.onInputTokens(promptLen);
                listener.onCachedInputTokens(cached);
            }
            Set<Integer> stops = new HashSet<>();
            for (int s : stopTokens()) {
                stops.add(s);
            }
            if (maxGen > 0) {
                Tensor logits = prefill(id, prompt, cached);
                int next = sample(logits, temperature, topp);
                int pos = promptLen;
                while (true) {
                    if (cancelRequested != null && cancelRequested.getAsBoolean()) {
                        throw new java.util.concurrent.CancellationException("generation cancelled");
                    }
                    if (stops.contains(next)) {
                        stopped = true;
                        break;
                    }
                    out.add(next);
                    if (out.size() >= maxGen) {
                        break;
                    }
                    Tensor step = decodeStep(new int[]{id}, new int[]{next}, new int[]{pos});
                    pos++;
                    next = sample(step, temperature, topp);
                }
            }
            int[] completion = out.stream().mapToInt(Integer::intValue).toArray();
            if (listener != null) {
                listener.onGeneratedTokens(completion.length);
                if (completion.length > 0) {
                    listener.onText(safeDecode(completion));
                }
            }
            int[] sequence = new int[promptLen + completion.length];
            System.arraycopy(prompt, 0, sequence, 0, promptLen);
            System.arraycopy(completion, 0, sequence, promptLen, completion.length);
            finish(id, sequence);
            id = -1;
            return new ChatCompletion(name, safeDecode(completion), prompt, completion,
                    stopped ? FinishReason.stop : FinishReason.length, null);
        } finally {
            if (id >= 0) {
                evict(id);
            }
        }
    }

    /**
     * Follower main loop: executes the leader's mirrored calls on this process's ranks until the leader shuts the
     * cluster down. Results (logits) are discarded: only the leader samples.
     *
     * @param link the connection to the leader.
     * @throws Exception the first failing call (already reported to the leader).
     */
    public void serveCluster(ClusterFollower link) throws Exception {
        link.run((op, in) -> {
            switch (op) {
                case OP_BIND: {
                    int[] prompt = readInts(in);
                    return bindLocal(prompt, in.readInt());
                }
                case OP_PREFILL: {
                    int requestId = in.readInt();
                    int from = in.readInt();
                    boolean last = in.readBoolean();
                    int[] window = readInts(in);
                    for (Tensor t : prefillWindow(requestId, window, from, last, false)) {
                        if (t != null) {
                            t.close();
                        }
                    }
                    return -1;
                }
                case OP_DECODE: {
                    int[] ids = readInts(in);
                    int[] last = readInts(in);
                    int[] positions = readInts(in);
                    for (Tensor t : decodeLocal(ids, last, positions, false)) {
                        if (t != null) {
                            t.close();
                        }
                    }
                    return -1;
                }
                case OP_FINISH: {
                    int requestId = in.readInt();
                    int[] tokens = readInts(in);
                    finishLocal(requestId, in.readBoolean() ? tokens : null);
                    return -1;
                }
                case OP_EVICT:
                    evictLocal(in.readInt());
                    return -1;
                case OP_PREFIX_REUSE:
                    for (MiniMaxModel m : models) {
                        m.kvCachePool().setPrefixReuseEnabled(in.readBoolean());
                    }
                    return -1;
                default:
                    throw new IllegalStateException("unknown cluster op " + op);
            }
        });
    }

    private int sample(Tensor logits, double temperature, double topp) {
        try (logits) {
            return Sampling.sampleTokenIds(logits, temperature, topp)[0];
        }
    }

    private String safeDecode(int[] tokens) {
        try {
            return tokenizer.tryDecode(tokens, true);
        } catch (Exception e) {
            return tokenizer.decode(tokens);
        }
    }

    @Override
    public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature, double topp,
                               boolean logprobs, long seed, GenerationListener listener,
                               BooleanSupplier cancelRequested) {
        return generate(encodeChat(dialog), maxGenLen, temperature, topp, logprobs, seed,
                listener, cancelRequested);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Builds a model from a HuggingFace BF16 checkpoint directory on a single device.
     *
     * @param checkpointDir   directory with {@code config.json}, {@code tokenizer.json} and safetensors.
     * @param maxBatchSize    maximum concurrent requests.
     * @param maxSeqLen       maximum context length; {@code <= 0} uses {@code max_position_embeddings}.
     * @param deviceId        CUDA device index, or a negative value for CPU.
     * @param memFractionStatic fraction of device memory for weights + KV; {@code <= 0} sizes the pool for tests.
     * @param pageSize        KV page size in tokens.
     * @return the loaded model.
     * @throws IOException if the checkpoint is missing, quantized or incomplete.
     */
    public static MiniMax build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                                double memFractionStatic, int pageSize) throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen,
                deviceId >= 0 ? new byte[]{deviceId} : null, memFractionStatic, pageSize);
    }

    /**
     * Builds a model from a HuggingFace BF16 checkpoint directory, sharding the routed experts of
     * every MoE layer across {@code devices} (expert parallelism with an all-reduce; attention,
     * dense FFNs, router and shared expert are replicated on every rank).
     *
     * @param checkpointDir     directory with {@code config.json}, {@code tokenizer.json} and safetensors.
     * @param maxBatchSize      maximum concurrent requests.
     * @param maxSeqLen         maximum context length; {@code <= 0} uses {@code max_position_embeddings}.
     * @param devices           CUDA device indices, one rank each, or {@code null} for a single CPU rank.
     * @param memFractionStatic fraction of device memory for weights + KV; {@code <= 0} sizes the pool for tests.
     * @param pageSize          KV page size in tokens.
     * @return the loaded model.
     * @throws IOException if the checkpoint is missing, quantized or incomplete.
     */
    public static MiniMax build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte[] devices,
                                double memFractionStatic, int pageSize) throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, devices, memFractionStatic, pageSize, null);
    }

    /**
     * Builds a model, choosing how MXFP8 weights are stored and multiplied.
     *
     * @param checkpointDir     directory with {@code config.json}, {@code tokenizer.json} and safetensors.
     * @param maxBatchSize      maximum concurrent requests.
     * @param maxSeqLen         maximum context length; {@code <= 0} uses {@code max_position_embeddings}.
     * @param devices           CUDA device indices, one expert-parallel rank each, or {@code null} for one CPU rank.
     * @param memFractionStatic fraction of device memory for weights + KV; {@code <= 0} sizes the pool for tests.
     * @param pageSize          KV page size in tokens.
     * @param weightMode        {@code auto}, {@code marlin}, {@code fp8}, {@code fp8emu} or {@code dequant};
     *                          {@code null} reads {@code SMILE_MINIMAX_MXFP8} (default {@code auto}).
     * @return the loaded model.
     * @throws IOException if the checkpoint is missing, unsupported or incomplete.
     */
    public static MiniMax build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte[] devices,
                                double memFractionStatic, int pageSize, String weightMode) throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, devices, memFractionStatic, pageSize, weightMode, null);
    }

    /**
     * Builds this process's part of a model whose expert-parallel ranks span several processes (and nodes).
     * {@code devices} are the GPUs of this process; its ranks are {@code cluster.rankOffset() ..} of
     * {@code cluster.worldSize()}, and every process must call this at the same time (the NCCL communicator is created
     * first and returns when all ranks have joined). With a {@code null} {@code cluster} this is the single-process build.
     *
     * @param checkpointDir     directory with {@code config.json}, {@code tokenizer.json} and safetensors.
     * @param maxBatchSize      maximum concurrent requests.
     * @param maxSeqLen         maximum context length; {@code <= 0} uses {@code max_position_embeddings}.
     * @param devices           CUDA device indices of this process, one expert-parallel rank each.
     * @param memFractionStatic fraction of device memory for weights + KV.
     * @param pageSize          KV page size in tokens.
     * @param weightMode        see {@link #build(String, int, int, byte[], double, int, String)}.
     * @param cluster           this process's place in the cluster, or {@code null}.
     * @return the loaded model.
     * @throws IOException if the checkpoint is missing, unsupported or incomplete.
     */
    public static MiniMax build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte[] devices,
                                double memFractionStatic, int pageSize, String weightMode, ClusterSpec cluster)
            throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Checkpoint directory not found: " + checkpointDir);
        }
        Path configJson = Path.of(checkpointDir, "config.json");
        if (!Files.exists(configJson)) {
            throw new IllegalArgumentException("config.json not found in " + checkpointDir);
        }
        JsonNode root = new ObjectMapper().readTree(configJson.toFile());
        if (root.has("quantization_config")) {
            JsonNode q = root.get("quantization_config");
            String method = q.path("quant_method").asString();
            JsonNode block = q.path("weight_block_size");
            boolean mxfp8 = "mxfp8".equals(method) && block.isArray() && block.size() == 2
                    && block.get(0).asInt() == 1 && block.get(1).asInt() == MxFp8.BLOCK;
            if (!mxfp8) {
                throw new IOException("Unsupported quantization (quant_method=" + method
                        + "); only MXFP8 with 1x" + MxFp8.BLOCK + " blocks is supported");
            }
            String expertAlgo = q.path("moe_quant_algo").asString("");
            if (!expertAlgo.isEmpty() && !"NVFP4".equals(expertAlgo)) {
                throw new IOException("Unsupported routed-expert quantization moe_quant_algo=" + expertAlgo);
            }
            if ("NVFP4".equals(expertAlgo) && q.path("group_size").asInt(0) != 16) {
                throw new IOException("NVFP4 experts need group_size 16, got " + q.path("group_size"));
            }
        }

        boolean cuda = devices != null;
        int ranks = cuda ? devices.length : 1;
        ScalarType computeDtype = ScalarType.Float;
        if (cuda) {
            Device.CUDA(devices[0]);
            computeDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            smile_torch_h.smile_set_default_dtype(computeDtype.code());
        }

        MiniMaxModelArgs args = MiniMaxModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        if (root.has("quantization_config")) {
            MiniMaxWeightMode mode = selectWeightMode(weightMode != null && !weightMode.isBlank() ? weightMode.trim().toLowerCase()
                            : System.getenv().getOrDefault("SMILE_MINIMAX_MXFP8", "auto"),
                    devices, computeDtype);
            if ("NVFP4".equals(root.path("quantization_config").path("moe_quant_algo").asString(""))) {
                // MXFP8 attention/dense layers + NVFP4 experts: both run through Marlin (weight-only, bfloat16 activations).
                // SMILE_MINIMAX_MXFP8=dequant expands everything to bfloat16 (reference path, needs the memory).
                if (mode != MiniMaxWeightMode.DENSE) {
                    if (!(cuda && computeDtype == ScalarType.BFloat16
                            && Native.marlinFp8Available() && Native.marlinMoeNvfp4Available())) {
                        throw new IOException("NVFP4 experts need CUDA, bfloat16 and a libsmile_torch built with Marlin NVFP4");
                    }
                    mode = MiniMaxWeightMode.MARLIN;
                }
                args = args.withNvfp4Experts();
            }
            args = args.withWeightMode(mode);
            logger.info("MXFP8 checkpoint: weight mode {}{}", mode, args.nvfp4Experts() ? " with NVFP4 experts" : "");
        }
        MiniMaxTokenizer tokenizer = MiniMaxTokenizer.of(checkpointDir);
        if (tokenizer.size() > args.vocabSize()) {
            logger.warn("Tokenizer size {} exceeds config vocab_size {}; embedding gather may OOB",
                    tokenizer.size(), args.vocabSize());
        }

        final MiniMaxModelArgs modelArgs = args;
        if (cluster != null && !cuda) {
            throw new IllegalArgumentException("a multi-node model needs CUDA devices");
        }
        final int world = cluster != null ? cluster.worldSize() : ranks;
        final int firstRank = cluster != null ? cluster.rankOffset() : 0;
        TensorParallelGroup group = cluster != null
                ? new TensorParallelGroup(ParallelConfig.tensorParallel(devices), world, firstRank, cluster.uniqueId())
                : (ranks > 1 ? new TensorParallelGroup(ParallelConfig.tensorParallel(devices)) : null);
        // Tensor-parallel attention/FFN/vocabulary: the replicated weights of a big checkpoint (Flash: 16 GB per GPU) would
        // not leave room next to its experts. auto = on for NVFP4 experts; SMILE_MINIMAX_TP=on|off forces it.
        String tpRequest = System.getenv().getOrDefault("SMILE_MINIMAX_TP", "auto").trim().toLowerCase();
        boolean tensorParallel = cluster == null && ranks > 1
                && ("on".equals(tpRequest) || ("auto".equals(tpRequest) && args.nvfp4Experts()));
        if (tensorParallel) {
            logger.info("tensor-parallel attention, FFNs and vocabulary across {} ranks", ranks);
        }
        MiniMaxModel[] networks = new MiniMaxModel[ranks];
        for (int r = 0; r < ranks; r++) {
            long t0 = System.currentTimeMillis();
            Device device = cuda ? Device.CUDA(devices[r]) : Device.CPU();
            networks[r] = new MiniMaxModel(args, new MiniMaxShard(firstRank + r, world, device, tensorParallel), group);
            networks[r].to(device, computeDtype);
            logger.info("rank {}/{}: allocated {} layers ({} local experts) on {} in {} ms", firstRank + r, world,
                    args.numLayers(), networks[r].shard().localExperts(args.numExperts()), device,
                    System.currentTimeMillis() - t0);
        }

        // Each rank streams the shards and keeps its own experts plus the replicated tensors.
        ExecutorService loader = Executors.newFixedThreadPool(ranks);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (MiniMaxModel network : networks) {
                futures.add(loader.submit(() -> {
                    loadWeights(network, dir, modelArgs);
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    throw c instanceof IOException io ? io : new IOException("weight loading failed", c);
                }
            }
        } finally {
            loader.shutdownNow();
        }

        // The vision encoder (~1.7 GB) lives on rank 0's device; load it before the KV pool takes the free memory.
        MiniMaxVisionTower vision = null;
        boolean follower = cluster != null && cluster.rankOffset() > 0;   // the vision encoder lives on the leader's rank 0
        if (!follower && !"0".equals(System.getenv("SMILE_MINIMAX_VISION"))) {
            try {
                long t0 = System.currentTimeMillis();
                vision = MiniMaxVisionTower.load(dir, networks[0].device(), computeDtype);
                if (vision != null) {
                    logger.info("vision encoder loaded on {} in {} ms", networks[0].device(),
                            System.currentTimeMillis() - t0);
                } else {
                    logger.info("checkpoint has no vision weights: images and videos are not supported");
                }
            } catch (IOException | RuntimeException e) {
                logger.warn("vision encoder not loaded, images and videos are not supported: {}", e.toString());
            }
        }

        // The DSpark drafter (2.3 GB FP8) also lives on rank 0's device, loaded before the KV pool takes the free memory.
        java.util.concurrent.atomic.AtomicReference<MiniMax> self = new java.util.concurrent.atomic.AtomicReference<>();
        MiniMaxDspark draft = null;
        if (!follower && cluster == null && cuda && computeDtype == ScalarType.BFloat16
                && !"0".equals(System.getenv("SMILE_MINIMAX_DSPARK")) && new File(dir, "dspark/config.json").exists()) {
            try {
                long t0 = System.currentTimeMillis();
                draft = MiniMaxDspark.load(new File(dir, "dspark").getPath(), networks[0].device(), args.weightMode(),
                        computeDtype, headsOf(self), args.maxSeqLen() * 2);
                logger.info("DSpark draft loaded on {} in {} ms (block {}, taps {})", networks[0].device(),
                        System.currentTimeMillis() - t0, draft.args().blockSize(),
                        Arrays.toString(draft.args().targetLayerIds()));
            } catch (IOException | RuntimeException e) {
                logger.warn("DSpark draft not loaded, speculative decoding is off: {}", e.toString());
            }
        }

        // MSA index keys take extra bytes per token on top of K/V; shrink the pool's share so both fit.
        double kvPerToken = 2.0 * args.numLayers() * networks[0].shard().kvHeads(args.numKvHeads()) * args.headDim();
        double indexPerToken = (double) args.numSparseLayers() * args.numIndexKeyHeads() * args.indexDim();
        double kvShare = kvPerToken / (kvPerToken + indexPerToken);
        for (MiniMaxModel network : networks) {
            Device device = network.device();
            device.emptyCache();
            // The pool counts everything already on the device (weights, NCCL, context) against its budget, so give it
            // the weights plus its share of what is left under the static fraction.
            double poolFraction = memFractionStatic;
            if (device.isCUDA()) {
                long[] memInfo = Native.cudaMemGetInfo(device.index());
                logger.info("rank {}: {} used of {} MiB before the KV pool", network.shard().rank(), (memInfo[1] - memInfo[0]) >> 20, memInfo[1] >> 20);
            }
            if (memFractionStatic > 0 && device.isCUDA()) {
                long[] mem = Native.cudaMemGetInfo(device.index());
                double used = (double) mem[1] - mem[0];
                double room = Math.max(0.0, memFractionStatic * mem[1] - used);
                poolFraction = (used + room * kvShare) / mem[1];
            }
            KvCachePool pool = memFractionStatic > 0
                    ? KvCachePool.allocate(args.kvCacheLayout(network.shard().kvHeads(args.numKvHeads())), device, computeDtype,
                            poolFraction, pageSize)
                    : KvCachePool.forTesting(args.kvCacheLayout(network.shard().kvHeads(args.numKvHeads())), device);
            network.setKvCachePool(pool, false);
            network.eval();
        }
        MiniMax result = new MiniMax(dir.getName(), networks, group, tokenizer, args);
        result.setVision(vision);
        self.set(result);
        if (draft != null) {
            result.setDraft(draft);
        }
        return result;
    }

    /**
     * Builds the leader of a multi-node model: waits for {@code followers} follower processes
     * ({@link MiniMaxFollower}) to connect, builds this process's ranks together with theirs, and mirrors every state
     * change to them from then on. The leader owns world ranks {@code 0 .. devices.length - 1}.
     *
     * @param checkpointDir     checkpoint directory on this node.
     * @param maxBatchSize      maximum concurrent requests.
     * @param maxSeqLen         maximum context length; {@code <= 0} uses {@code max_position_embeddings}.
     * @param devices           CUDA device indices of this process.
     * @param memFractionStatic fraction of device memory for weights + KV (the followers use the same).
     * @param pageSize          KV page size in tokens.
     * @param weightMode        see {@link #build(String, int, int, byte[], double, int, String)}; {@code null} for auto.
     * @param port              TCP port the followers connect to.
     * @param followers         number of follower processes.
     * @param timeoutMs         how long to wait for the followers.
     * @return the leader model.
     * @throws IOException if the followers do not connect or the checkpoint cannot be loaded.
     */
    public static MiniMax buildLeader(String checkpointDir, int maxBatchSize, int maxSeqLen, byte[] devices,
                                      double memFractionStatic, int pageSize, String weightMode,
                                      int port, int followers, long timeoutMs) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("maxBatchSize", Integer.toString(maxBatchSize));
        params.put("maxSeqLen", Integer.toString(maxSeqLen));
        params.put("memFractionStatic", Double.toString(memFractionStatic));
        params.put("pageSize", Integer.toString(pageSize));
        params.put("weightMode", weightMode == null ? "" : weightMode);
        ClusterLeader leader = ClusterLeader.start(port, followers, devices.length, timeoutMs, params);
        MiniMax model;
        try {
            model = build(checkpointDir, maxBatchSize, maxSeqLen, devices, memFractionStatic, pageSize, weightMode,
                    new ClusterSpec(leader.worldSize(), 0, leader.uniqueId()));
        } catch (IOException | RuntimeException e) {
            leader.close();
            throw e;
        }
        model.attachCluster(leader);
        return model;
    }

    /**
     * Chooses how MXFP8 weights are stored and multiplied.
     *
     * <p>{@code auto}: native block-FP8 GEMM on Hopper or newer (after a self-check against the emulated
     * arithmetic, since that path depends on hardware-specific cuBLAS behavior), otherwise Marlin on any
     * other CUDA GPU, otherwise dense. {@code fp8}, {@code fp8emu}, {@code marlin} and {@code dequant}
     * force a mode.
     */
    static MiniMaxWeightMode selectWeightMode(String request, byte[] devices, ScalarType computeDtype)
            throws IOException {
        boolean cuda = devices != null;
        boolean bf16 = computeDtype == ScalarType.BFloat16;
        boolean marlinOk = cuda && bf16 && Native.marlinFp8Available() && Native.marlinMoeFp8Available();
        boolean fp8Libs = cuda && bf16 && Native.mxfp8ToBlockFp8Available() && Native.scaledMmV2Available()
                && Native.fp8Quant1x128Available();
        switch (request) {
            case "dequant":
                return MiniMaxWeightMode.DENSE;
            case "marlin":
                if (!marlinOk) {
                    throw new IOException("SMILE_MINIMAX_MXFP8=marlin needs CUDA, bfloat16 and a libsmile_torch built with MXFP8 Marlin");
                }
                return MiniMaxWeightMode.MARLIN;
            case "fp8emu":
                if (!fp8Libs) {
                    throw new IOException("SMILE_MINIMAX_MXFP8=fp8emu needs CUDA, bfloat16 and the block-FP8 kernels");
                }
                return MiniMaxWeightMode.FP8_EMULATED;
            case "fp8": {
                String why = nativeFp8Problem(devices, fp8Libs);
                if (why != null) {
                    throw new IOException("SMILE_MINIMAX_MXFP8=fp8: " + why);
                }
                return MiniMaxWeightMode.FP8;
            }
            case "auto": {
                String why = nativeFp8Problem(devices, fp8Libs);
                if (why == null) {
                    return MiniMaxWeightMode.FP8;
                }
                if (cuda && why.startsWith("native FP8 self-check")) {
                    logger.warn("Native FP8 GEMM rejected ({}); using Marlin", why);
                }
                return marlinOk ? MiniMaxWeightMode.MARLIN : MiniMaxWeightMode.DENSE;
            }
            default:
                throw new IOException("unknown SMILE_MINIMAX_MXFP8 value '" + request
                        + "' (expected auto, marlin, fp8, fp8emu or dequant)");
        }
    }

    /** Returns why the native block-FP8 path cannot be used on {@code devices}, or {@code null} if it can. */
    private static String nativeFp8Problem(byte[] devices, boolean libsOk) {
        if (devices == null) {
            return "no CUDA device";
        }
        if (!libsOk) {
            return "libsmile_torch lacks the block-FP8 kernels or bfloat16 is unavailable";
        }
        for (byte d : devices) {
            int[] cc = Native.cudaComputeCapability(d);
            if (cc[0] < 9) {
                return "device " + d + " is sm_" + cc[0] + cc[1] + " (needs Hopper, sm_90, or newer)";
            }
            String failure = smile.llm.quant.Fp8BlockGemm.selfCheck(Device.CUDA(d));
            if (failure != null) {
                return "native FP8 self-check on device " + d + ": " + failure;
            }
        }
        return null;
    }

    private static void loadWeights(MiniMaxModel network, File dir, MiniMaxModelArgs args) throws IOException {
        Map<String, String> weightMap = readWeightMap(dir);
        Map<String, List<String>> shardToKeys = new LinkedHashMap<>();
        MiniMaxShard rank = network.shard();
        int firstExpert = rank.expertStart(args.numExperts());
        int lastExpert = firstExpert + rank.localExperts(args.numExperts());
        for (var entry : weightMap.entrySet()) {
            String mapped = MiniMaxWeightNames.remap(entry.getKey());
            if (mapped != null) {
                var em = EXPERT_KEY.matcher(mapped);
                if (em.matches()) {
                    int e = Integer.parseInt(em.group(2));
                    if (e < firstExpert || e >= lastExpert) {
                        continue;  // owned by another rank
                    }
                }
                shardToKeys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        List<String> shards = new ArrayList<>(shardToKeys.keySet());
        Collections.sort(shards);

        var quantLinears = network.quantLinears();
        Set<String> loaded = new HashSet<>();
        Map<Integer, Tensor[]> routerParts = new HashMap<>();
        Device target = network.device();
        for (String shard : shards) {
            List<String> keys = shardToKeys.get(shard);
            long t0 = System.currentTimeMillis();
            SafeTensors st = SafeTensors.read(Path.of(dir.getPath(), shard).toString(), Device.CPU(), keys);
            try (var noGrad = Tensor.noGradGuard()) {
                // Stage tensors on the device in small batches so a shard never needs a second
                // device-resident copy of itself next to the model.
                List<Tensor> owned = new ArrayList<>();
                Map<String, Tensor> stateDict = new HashMap<>();
                long staged = 0;
                for (String hfName : keys) {
                    if (hfName.endsWith(SCALE_SUFFIX) || hfName.endsWith(NVFP4_SIDECAR_SUFFIX)
                            || hfName.endsWith(".input_global_scale")) {
                        continue;  // consumed together with its weight (activations are not quantized here)
                    }
                    Tensor src = st.tensors().get(hfName);
                    if (src == null) {
                        throw new IOException("Tensor '" + hfName + "' missing from " + shard);
                    }
                    if (hfName.endsWith(NVFP4_WEIGHT_SUFFIX)) {
                        loadNvfp4Expert(network, st, hfName, shard, target);
                        loaded.add(MiniMaxWeightNames.remap(hfName.substring(0, hfName.length() - "_packed".length())));
                        continue;
                    }
                    String smileName = MiniMaxWeightNames.remap(hfName);
                    Tensor onDevice;
                    Tensor scaleCpu = hfName.endsWith(".weight") ? st.tensors().get(hfName + "_scale_inv") : null;
                    if (rank.tp()) {
                        src = tpSlice(rank, args, smileName, src, false);
                        scaleCpu = scaleCpu == null ? null : tpSlice(rank, args, smileName, scaleCpu, true);
                    }
                    if (scaleCpu != null && args.weightMode().quantized()) {
                        // FP8-resident: hand the raw FP8 bytes and E8M0 scales to the Marlin layer.
                        Tensor fp8 = src.to(target);
                        Tensor scale = scaleCpu.to(target);
                        try {
                            var em = EXPERT.matcher(smileName);
                            if (em.matches()) {
                                MiniMaxMoE moe = network.layers().get(Integer.parseInt(em.group(1))).moe();
                                moe.loadExpertFp8(Integer.parseInt(em.group(3)),
                                        Integer.parseInt(em.group(2)) - moe.expertStart(), fp8, scale);
                            } else {
                                var layer = quantLinears.get(smileName);
                                if (layer == null) {
                                    throw new IOException("no FP8 layer registered for " + smileName);
                                }
                                layer.setWeight(fp8, scale);
                            }
                        } finally {
                            if (fp8 != src) fp8.close();
                            if (scale != scaleCpu) scale.close();
                        }
                        loaded.add(smileName);
                        continue;
                    }
                    if (scaleCpu != null) {
                        Tensor fp8 = src.to(target);
                        Tensor scale = scaleCpu.to(target);
                        onDevice = MxFp8.dequantize(fp8, scale, network.dtype());
                        if (fp8 != src) {
                            fp8.close();
                        }
                        if (scale != scaleCpu) {
                            scale.close();
                        }
                        owned.add(onDevice);
                    } else {
                        onDevice = src.to(target);
                        if (onDevice != src) {
                            owned.add(onDevice);
                        }
                    }
                    Tensor contiguous = onDevice.contiguous();
                    if (contiguous != onDevice) {
                        owned.add(contiguous);
                    }
                    loaded.add(smileName);
                    var em = EXPERT.matcher(smileName);
                    if (em.matches()) {
                        // Routed experts live in stacked tensors: copy into this expert's slot.
                        MiniMaxMoE moe = network.layers().get(Integer.parseInt(em.group(1))).moe();
                        int local = Integer.parseInt(em.group(2)) - moe.expertStart();
                        try (var index = Index.of(local);
                             Tensor slot = moe.expertWeights(Integer.parseInt(em.group(3))).get(index)) {
                            smile.torch.Native.copy_(slot, contiguous);
                        }
                    } else {
                        stateDict.put(smileName, contiguous);
                    }
                    captureRouter(network, smileName, src, target, routerParts);
                    long elements = 1;
                    for (long dim : src.shape()) {
                        elements *= dim;
                    }
                    staged += elements;
                    if (staged >= LOAD_BATCH_ELEMENTS) {
                        network.loadStateDict(stateDict, false);
                        owned.forEach(Tensor::close);
                        owned.clear();
                        stateDict.clear();
                        staged = 0;
                    }
                }
                if (!stateDict.isEmpty()) {
                    network.loadStateDict(stateDict, false);
                }
                owned.forEach(Tensor::close);
            } finally {
                for (Tensor t : st.tensors().values()) {
                    t.close();
                }
            }
            logger.info("rank {}: loaded {} ({} tensors) in {} ms", rank.rank(), shard, keys.size(),
                    System.currentTimeMillis() - t0);
        }

        for (var e : routerParts.entrySet()) {
            network.layers().get(e.getKey()).moe().setRouterFp32(e.getValue()[0], e.getValue()[1]);
        }

        network.fuseProjections();

        // loadStateDict(strict=false) silently skips unregistered names; fail loudly instead.
        Set<String> missing = new java.util.TreeSet<>(MiniMaxWeightNames.expectedNames(args, rank));
        missing.removeAll(loaded);
        if (!missing.isEmpty()) {
            throw new IOException(missing.size() + " expected weights are missing from the checkpoint, e.g. "
                    + missing.stream().limit(5).toList());
        }
    }

    private static final java.util.regex.Pattern TP_COLUMN = java.util.regex.Pattern.compile(
            "^(layers\\.\\d+\\.(mlp|moe\\.shared_experts)\\.w[13]|embed_tokens|lm_head)\\.weight$");
    private static final java.util.regex.Pattern TP_ROW = java.util.regex.Pattern.compile(
            "^layers\\.\\d+\\.(mlp|moe\\.shared_experts)\\.w2\\.weight$");

    /**
     * Returns the part of a replicated weight (or of its MXFP8 scale, {@code scale}) that a tensor-parallel rank keeps:
     * query heads, this rank's KV head and its index-query group, the matching columns of the output projection, a
     * slice of every dense/shared FFN, and a slice of the vocabulary for the embedding and the output head. Other
     * tensors (norms, the router, the index-key projection) are returned whole.
     */
    private static Tensor tpSlice(MiniMaxShard rank, MiniMaxModelArgs args, String name, Tensor t, boolean scale) {
        int dim;
        int start;
        int len;
        int hd = args.headDim();
        if (name.endsWith(".self_attn.q_proj.weight")) {
            dim = 0;
            start = rank.partStart(args.numHeads()) * hd;
            len = rank.part(args.numHeads()) * hd;
        } else if (name.endsWith(".self_attn.k_proj.weight") || name.endsWith(".self_attn.v_proj.weight")) {
            dim = 0;
            start = rank.kvStart(args.numKvHeads()) * hd;
            len = rank.kvHeads(args.numKvHeads()) * hd;
        } else if (name.endsWith(".self_attn.o_proj.weight")) {
            dim = 1;
            start = rank.partStart(args.numHeads()) * hd;
            len = rank.part(args.numHeads()) * hd;
        } else if (name.endsWith(".self_attn.index_q_proj.weight")) {
            dim = 0;
            start = rank.kvStart(args.numKvHeads()) * args.indexDim();
            len = rank.kvHeads(args.numKvHeads()) * args.indexDim();
        } else if (TP_COLUMN.matcher(name).matches()) {
            dim = 0;
            len = rank.part((int) t.shape()[0]);
            start = rank.partStart((int) t.shape()[0]);
        } else if (TP_ROW.matcher(name).matches()) {
            int cols = (int) t.shape()[1] * (scale ? MxFp8.BLOCK : 1);
            dim = 1;
            len = rank.part(cols);
            start = rank.partStart(cols);
        } else {
            return t;
        }
        if (scale && dim == 1) {
            start /= MxFp8.BLOCK;
            len /= MxFp8.BLOCK;
        }
        try (var range = smile.deep.tensor.Index.slice(start, start + len)) {
            Tensor part = dim == 0 ? t.get(range, smile.deep.tensor.Index.Ellipsis) : t.get(smile.deep.tensor.Index.Ellipsis, range);
            return part.contiguous();
        }
    }

    /** Elements staged on the device before they are copied into the model (about 0.5 GB in bf16). */
    private static final long LOAD_BATCH_ELEMENTS = 256L << 20;

    private static final String SCALE_SUFFIX = ".weight_scale_inv";

    /** NVFP4 expert tensors: the packed weight, and its block-scale and global-scale sidecars. */
    private static final String NVFP4_WEIGHT_SUFFIX = ".weight_packed";
    private static final String NVFP4_SIDECAR_SUFFIX = ".weight_scale";
    private static final String NVFP4_GLOBAL_SUFFIX = ".weight_global_scale";

    /** Hands one NVFP4 routed-expert projection (packed nibbles, E4M3 group-16 scales, global scale) to its MoE layer. */
    private static void loadNvfp4Expert(MiniMaxModel network, SafeTensors st, String hfName, String shard,
                                        Device target) throws IOException {
        String stem = hfName.substring(0, hfName.length() - NVFP4_WEIGHT_SUFFIX.length());
        Tensor packed = st.tensors().get(hfName);
        Tensor scale = st.tensors().get(stem + NVFP4_SIDECAR_SUFFIX);
        Tensor global = st.tensors().get(stem + NVFP4_GLOBAL_SUFFIX);
        if (scale == null || global == null) {
            throw new IOException("NVFP4 expert '" + stem + "' lacks its scales in " + shard);
        }
        var em = EXPERT.matcher(MiniMaxWeightNames.remap(stem + ".weight"));
        if (!em.matches()) {
            throw new IOException("Unexpected NVFP4 tensor " + hfName);
        }
        MiniMaxMoE moe = network.layers().get(Integer.parseInt(em.group(1))).moe();
        Tensor packedDev = packed.to(target);
        Tensor scaleDev = scale.to(target);
        try {
            moe.loadExpertNvfp4(Integer.parseInt(em.group(3)), Integer.parseInt(em.group(2)) - moe.expertStart(),
                    packedDev, scaleDev, global.to(Device.CPU(), ScalarType.Float).floatArray()[0]);
        } finally {
            if (packedDev != packed) packedDev.close();
            if (scaleDev != scale) scaleDev.close();
        }
    }

    private static final java.util.regex.Pattern EXPERT =
            java.util.regex.Pattern.compile("^layers\\.(\\d+)\\.moe\\.experts\\.(\\d+)\\.w([123])\\.weight$");

    /** Expert weight or its scale sidecar; used to skip tensors owned by other ranks. */
    private static final java.util.regex.Pattern EXPERT_KEY = java.util.regex.Pattern.compile(
            "^layers\\.(\\d+)\\.moe\\.experts\\.(\\d+)\\.w([123])\\.(weight(_scale_inv|_packed|_scale|_global_scale)?|input_global_scale)$");

    private static final java.util.regex.Pattern ROUTER =
            java.util.regex.Pattern.compile("^layers\\.(\\d+)\\.moe\\.(gate\\.weight|e_score_correction_bias)$");

    /** Keeps a float32 device copy of each layer's router weight and bias. */
    private static void captureRouter(MiniMaxModel network, String smileName, Tensor src, Device target,
                                      Map<Integer, Tensor[]> parts) {
        var m = ROUTER.matcher(smileName);
        if (!m.matches()) {
            return;
        }
        Tensor copy = src.to(target, ScalarType.Float).copy();
        Tensor[] pair = parts.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new Tensor[2]);
        pair[m.group(2).equals("gate.weight") ? 0 : 1] = copy;
    }

    private static Map<String, String> readWeightMap(File dir) throws IOException {
        Path indexPath = Path.of(dir.getPath(), "model.safetensors.index.json");
        Map<String, String> map = new LinkedHashMap<>();
        if (Files.exists(indexPath)) {
            JsonNode weightMap = new ObjectMapper().readTree(indexPath.toFile()).get("weight_map");
            weightMap.properties().forEach(e -> map.put(e.getKey(), e.getValue().asString()));
            return map;
        }
        File[] listed = dir.listFiles((d, n) -> n.endsWith(".safetensors"));
        if (listed == null || listed.length == 0) {
            throw new IOException("No safetensors files found in " + dir);
        }
        Arrays.sort(listed);
        for (File f : listed) {
            for (String tensor : SafeTensors.listTensors(f.getPath())) {
                map.put(tensor, f.getName());
            }
        }
        return map;
    }
}
