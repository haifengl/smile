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
        return id;
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
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestId);
        }
        int[] window = Arrays.copyOfRange(prompt, from, to);
        boolean last = to == prompt.length;
        Tensor[] logits = onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Tensor tokens = Tensor.of(window).reshape(1, window.length).to(models[r].device());
                    Tensor out = models[r].forward(tokens, from, false);
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
        Tensor row = firstAndClose(logits);
        if (row != null) {
            // Hand ownership to the caller's scope (a no-op without one).
            Tensor owned = row.copy();
            row.close();
            return owned;
        }
        return null;
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
        for (MiniMaxModel m : models) {
            m.kvCachePool().activateStep(requestIds);
        }
        long[] toks = new long[b];
        for (int i = 0; i < b; i++) {
            toks[i] = lastTokens[i];
        }
        Tensor[] logits = onRanks(r -> {
            try (var scope = new AutoScope()) {
                Tensor.push(scope);
                try {
                    Tensor graphed = models[r].decodeGraph(toks, positions);
                    if (graphed != null) {
                        graphed.detachFromScopes();
                        return graphed;
                    }
                    Tensor tokens = Tensor.of(toks).reshape(b, 1).to(models[r].device());
                    Tensor out = models[r].forward(tokens, positions);
                    Tensor rows = out.reshape(b, -1).copy();
                    rows.detachFromScopes();
                    return rows;
                } finally {
                    Tensor.pop();
                }
            }
        });
        Tensor rows = firstAndClose(logits);
        Tensor owned = rows.copy();
        rows.close();
        return owned;
    }

    @Override
    public void finish(int requestId, int[] sequenceTokens) {
        boolean media = multimodalRequests.remove(requestId);
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
            args = args.withWeightMode(mode);
            logger.info("MXFP8 checkpoint: weight mode {}", mode);
        }
        MiniMaxTokenizer tokenizer = MiniMaxTokenizer.of(checkpointDir);
        if (tokenizer.size() > args.vocabSize()) {
            logger.warn("Tokenizer size {} exceeds config vocab_size {}; embedding gather may OOB",
                    tokenizer.size(), args.vocabSize());
        }

        final MiniMaxModelArgs modelArgs = args;
        TensorParallelGroup group = ranks > 1 ? new TensorParallelGroup(ParallelConfig.tensorParallel(devices)) : null;
        MiniMaxModel[] networks = new MiniMaxModel[ranks];
        for (int r = 0; r < ranks; r++) {
            long t0 = System.currentTimeMillis();
            Device device = cuda ? Device.CUDA(devices[r]) : Device.CPU();
            networks[r] = new MiniMaxModel(args, new MiniMaxShard(r, ranks, device), group);
            networks[r].to(device, computeDtype);
            logger.info("rank {}/{}: allocated {} layers ({} local experts) on {} in {} ms", r, ranks,
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
        if (!"0".equals(System.getenv("SMILE_MINIMAX_VISION"))) {
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

        // MSA index keys take extra bytes per token on top of K/V; shrink the pool's share so both fit.
        double kvPerToken = 2.0 * args.numLayers() * args.numKvHeads() * args.headDim();
        double indexPerToken = (double) args.numSparseLayers() * args.numIndexKeyHeads() * args.indexDim();
        double poolFraction = memFractionStatic * kvPerToken / (kvPerToken + indexPerToken);
        for (MiniMaxModel network : networks) {
            Device device = network.device();
            device.emptyCache();
            KvCachePool pool = memFractionStatic > 0
                    ? KvCachePool.allocate(args.kvCacheLayout(), device, computeDtype, poolFraction, pageSize)
                    : KvCachePool.forTesting(args.kvCacheLayout(), device);
            network.setKvCachePool(pool, false);
            network.eval();
        }
        MiniMax result = new MiniMax(dir.getName(), networks, group, tokenizer, args);
        result.setVision(vision);
        return result;
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
                    if (hfName.endsWith(SCALE_SUFFIX)) {
                        continue;  // consumed together with its weight
                    }
                    Tensor src = st.tensors().get(hfName);
                    if (src == null) {
                        throw new IOException("Tensor '" + hfName + "' missing from " + shard);
                    }
                    String smileName = MiniMaxWeightNames.remap(hfName);
                    Tensor onDevice;
                    Tensor scaleCpu = hfName.endsWith(".weight") ? st.tensors().get(hfName + "_scale_inv") : null;
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

    /** Elements staged on the device before they are copied into the model (about 0.5 GB in bf16). */
    private static final long LOAD_BATCH_ELEMENTS = 256L << 20;

    private static final String SCALE_SUFFIX = ".weight_scale_inv";

    private static final java.util.regex.Pattern EXPERT =
            java.util.regex.Pattern.compile("^layers\\.(\\d+)\\.moe\\.experts\\.(\\d+)\\.w([123])\\.weight$");

    /** Expert weight or its scale sidecar; used to skip tensors owned by other ranks. */
    private static final java.util.regex.Pattern EXPERT_KEY =
            java.util.regex.Pattern.compile("^layers\\.(\\d+)\\.moe\\.experts\\.(\\d+)\\.w([123])\\.weight(_scale_inv)?$");

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
