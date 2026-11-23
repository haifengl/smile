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
package smile.llm.model.inkling;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.util.AutoScope;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.engine.ModelExecutor;
import smile.llm.engine.Sampling;
import smile.llm.template.InklingChatTemplate;

import static smile.llm.model.inkling.Ix.cat;
import static smile.llm.model.inkling.Ix.stepScoped;

/**
 * Inkling-2-Mini behind the continuous-batching engine: a {@link LanguageModel} for chat requests and a
 * {@link ModelExecutor} for {@link smile.llm.engine.InferenceEngine}. Text only for now.
 *
 * <p>Every request owns an {@link InklingState} (caches of the 48 layer applications, sliding-window caches limited to
 * the window plus one chunk). All tensor work runs on one worker thread, which also loads the weights. A decode step
 * runs the requests one after another (each as a one-token forward); batching them is a kernel-level change.
 *
 * @author Haifeng Li
 */
public final class Inkling implements LanguageModel, ModelExecutor, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Inkling.class);

    private final String name;
    private final InklingModelArgs args;
    private final InklingTokenizer tokenizer;
    private final InklingChatTemplate template = new InklingChatTemplate();
    private final ExecutorService worker;
    private final Device device;
    private final Map<Integer, InklingState> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private InklingModel model;
    private final InklingPrefixCache prefixCache;
    private final Map<Integer, InklingPrefixCache.Entry> prefixMatches = new ConcurrentHashMap<>();
    private volatile boolean prefixReuse = true;
    private InklingDecoder decoder;
    private InklingDraft draft;
    /** Per request: the draft's context (target features of the processed positions). */
    private final Map<Integer, InklingDraft.State> drafts = new ConcurrentHashMap<>();
    private static final boolean SPECULATE = !"0".equals(System.getenv("SMILE_INKLING_SPECULATE"));
    private final Map<Integer, DecodeGraph> verifyGraphs = new java.util.HashMap<>();
    private InklingVisionArgs visionArgs;
    private InklingVisionTower vision;
    private InklingImageProcessor imageProcessor;
    /** Per multimodal request: the image embeddings of the whole prompt and, per position, how many placeholders precede it. */
    private record ImageState(Tensor embeds, int[] before) { }
    private final Map<Integer, ImageState> images = new ConcurrentHashMap<>();
    private static final boolean GRAPHS = !"0".equals(System.getenv("SMILE_INKLING_GRAPH"));
    private static final int MAX_GRAPH_BATCH = Integer.getInteger("smile.inkling.graph.maxbatch", 16);
    /** Decode graphs for batches 1, 2, 4 .. this size are captured at load; larger ones when first needed. */
    private static final int WARM_GRAPH_BATCH = Integer.getInteger("smile.inkling.graph.warm", 8);
    private final Map<Integer, DecodeGraph> graphs = new java.util.HashMap<>();
    private boolean graphsDisabled;
    private InklingState padState;
    private static final boolean FUSED = !"0".equals(System.getenv("SMILE_INKLING_FUSED"));
    /** Prefill takes a snapshot at every multiple of this many tokens (when the cache has room). */
    private static final int SNAPSHOT_EVERY = Integer.getInteger("smile.inkling.prefix.every", 1024);
    /** Prefill takes no snapshots beyond this many tokens: they hold the whole history, churn the allocator and rarely repeat. */
    private static final int SNAPSHOT_MAX = Integer.getInteger("smile.inkling.prefix.max", 32768);
    /** Shorter prefixes are not worth a snapshot. */
    private static final int SNAPSHOT_MIN = Integer.getInteger("smile.inkling.prefix.min", 256);

    private Inkling(String name, InklingModelArgs args, InklingTokenizer tokenizer, Path dir, Device device) {
        this.name = name;
        this.args = args;
        this.tokenizer = tokenizer;
        this.device = device;
        // a failing load must not leave the JVM waiting on this thread
        long cacheMb = Long.getLong("smile.inkling.prefix.cache.mb", 2048L);
        this.prefixCache = cacheMb > 0 ? new InklingPrefixCache(cacheMb << 20) : null;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "inkling-worker");
            t.setDaemon(true);
            return t;
        });
        onWorker(() -> {
            this.model = InklingModel.load(dir.toString(), args, device);
            InklingVisionArgs va = InklingVisionArgs.load(dir.toString());
            InklingCheckpoint ck = new InklingCheckpoint(dir.toString());
            if (va != null && !"0".equals(System.getenv("SMILE_INKLING_VISION"))
                    && ck.hasRaw("model.visual.proj_out.weight")) {
                this.visionArgs = va;
                this.vision = InklingVisionTower.load(ck, va, device);
                this.imageProcessor = new InklingImageProcessor(va);
                logger.info("Inkling vision tower loaded ({} layers, up to {} tiles per image)", va.layers(), va.maxTokens());
            }
            if (SPECULATE) {
                this.draft = InklingDraft.load(dir.toString(), model, device);
                if (draft != null) {
                    logger.info("Inkling DSpark draft loaded ({} drafts per block)", draft.drafts());
                }
            }
            if (InklingState.SPILL_DEVICE >= 0 && InklingDecoder.available()) {
                smile.torch.InklingNative.op(20, new long[]{0}, new long[]{InklingState.SPILL_DEVICE}, new double[0]);
                logger.info("Inkling KV caches of the last {} global applications live on device {}", InklingState.SPILL_APPS,
                        InklingState.SPILL_DEVICE);
            }
            if (FUSED && InklingDecoder.available()) {
                this.decoder = new InklingDecoder(model);
                logger.info("Inkling decode: fused kernels");
                if (GRAPHS && smile.torch.Native.cudaGraphAvailable()) {
                    int top = Math.min(MAX_GRAPH_BATCH, WARM_GRAPH_BATCH);
                    for (int nb = 1; nb <= top && !graphsDisabled; nb <<= 1) {
                        DecodeGraph g = capture(nb);
                        if (g == null) {
                            graphsDisabled = true;
                        } else {
                            graphs.put(nb, g);
                        }
                    }
                    logger.info("Inkling decode CUDA graphs captured for batches 1..{}", top);
                    if (hasDraft() && !graphsDisabled) {
                        int rows = Math.min(draft.drafts() + 1, InklingState.VERIFY_ROWS);
                        for (int reqs = 1; reqs <= Math.min(4, top); reqs <<= 1) {
                            draftGraph(verifyGraph(reqs, rows), reqs, rows);
                        }
                    }
                }
            } else {
                logger.info("Inkling decode: reference ops (fused kernels unavailable or disabled)");
            }
            return null;
        });
    }

    /**
     * Loads Inkling-2-Mini.
     *
     * @param checkpointDir the checkpoint directory.
     * @param layers        a slice of the first {@code layers} layers for tests ({@code 0}: all).
     * @param maxSeqLen     maximum sequence length; {@code <= 0} uses the model's.
     * @param device        the CUDA device index.
     * @return the model.
     * @throws IOException if the checkpoint cannot be read.
     */
    public static Inkling build(String checkpointDir, int layers, int maxSeqLen, byte device) throws IOException {
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(checkpointDir, maxSeqLen);
        if (layers > 0) {
            args = args.withLayers(layers, layers >= args.coreEnd() ? args.numPasses() : 1);
        }
        Path dir = Path.of(checkpointDir);
        return new Inkling(dir.getFileName().toString(), args, InklingTokenizer.of(checkpointDir), dir, Device.CUDA(device));
    }

    private <T> T onWorker(Callable<T> task) {
        try {
            return worker.submit(task).get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private InklingState session(int requestId) {
        InklingState s = sessions.get(requestId);
        if (s == null) {
            throw new IllegalArgumentException("unknown request " + requestId);
        }
        return s;
    }

    /** Hyperparameters. */
    public InklingModelArgs args() {
        return args;
    }

    // ------------------------------------------------------------------ LanguageModel

    @Override
    public String family() {
        return "inkling";
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int maxSeqLen() {
        return args.maxSeqLen();
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
                throw new IllegalArgumentException("images need processMultimodal (use the multimodal request path)");
            }
        }
        return tokenizer.encodeWithSpecials(template.encode(Arrays.asList(dialog), options));
    }

    @Override
    public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature, double topp, boolean logprobs,
                                   long seed, GenerationListener listener, BooleanSupplier cancelRequested) {
        if (prompt == null || prompt.length == 0) {
            throw new IllegalArgumentException("prompt must not be empty");
        }
        if (logprobs) {
            throw new UnsupportedOperationException("logprobs are not supported yet");
        }
        for (int token : prompt) {
            if (token < 0 || token >= args.unpaddedVocabSize()) {
                throw new IllegalArgumentException("Prompt token id " + token + " out of range for vocab_size "
                        + args.unpaddedVocabSize());
            }
        }
        int promptLen = prompt.length;
        if (promptLen > args.maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }
        int maxGen = Math.max(0, Math.min(maxGenLen, args.maxSeqLen() - promptLen));
        int id = bind(prompt, promptLen + maxGen);
        List<Integer> out = new ArrayList<>();
        boolean stopped = false;
        try (var guard = Tensor.noGradGuard()) {
            if (listener != null) {
                listener.onInputTokens(promptLen);
                listener.onCachedInputTokens(0);
            }
            Set<Integer> stops = new HashSet<>();
            for (int s : stopTokens()) {
                stops.add(s);
            }
            if (maxGen > 0) {
                int next = sample(prefill(id, prompt, 0), temperature, topp);
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
                    next = sample(decodeStep(new int[]{id}, new int[]{next}, new int[]{pos++}), temperature, topp);
                }
            }
            int[] completion = out.stream().mapToInt(Integer::intValue).toArray();
            String text = safeDecode(completion);
            if (listener != null) {
                listener.onGeneratedTokens(completion.length);
                if (completion.length > 0) {
                    listener.onText(text);
                }
            }
            return new ChatCompletion(name, text, prompt, completion,
                    stopped ? FinishReason.stop : FinishReason.length, null);
        } finally {
            evict(id);
        }
    }

    @Override
    public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature, double topp, boolean logprobs,
                               long seed, GenerationListener listener, BooleanSupplier cancelRequested) {
        return generate(encodeChat(dialog), maxGenLen, temperature, topp, logprobs, seed, listener, cancelRequested);
    }

    private static int sample(Tensor logits, double temperature, double topp) {
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

    // ------------------------------------------------------------------ images

    /**
     * Whether images can be processed (the checkpoint has its vision weights).
     *
     * @return {@code true} when {@link #processMultimodal} may be called.
     */
    public boolean isMultimodal() {
        return vision != null;
    }

    private static final String IMAGE_TOKEN = InklingTokenizer.IMAGE_TOKEN;

    /**
     * Renders a dialog with images: the chat template writes one image token per image, every one is expanded to one token
     * per 40x40 tile (as the reference processor does), and the images are preprocessed into tiles.
     *
     * @param dialog  the messages (image parts only).
     * @param options chat options.
     * @return the expanded prompt and the tiles to encode during prefill.
     * @throws IOException              if an image cannot be read or decoded.
     * @throws IllegalArgumentException if the request is malformed.
     */
    public InklingMultimodal processMultimodal(Message[] dialog, ChatOptions options) throws IOException {
        if (vision == null) {
            throw new IllegalArgumentException("this Inkling checkpoint has no vision weights");
        }
        if (dialog == null || dialog.length == 0) {
            throw new IllegalArgumentException("No messages provided.");
        }
        List<String> urls = new ArrayList<>();
        for (Message m : dialog) {
            for (var part : m.parts()) {
                if (part instanceof smile.llm.ImageUrlPart image) {
                    urls.add(image.url());
                } else if (part instanceof smile.llm.VideoUrlPart) {
                    throw new IllegalArgumentException("Inkling-2 video input is not supported");
                } else if (part instanceof smile.llm.AudioUrlPart) {
                    throw new IllegalArgumentException("Inkling-2 does not accept audio input");
                }
            }
        }
        String text = template.encode(Arrays.asList(dialog), options);
        int markers = 0;
        for (int i = text.indexOf(IMAGE_TOKEN); i >= 0; i = text.indexOf(IMAGE_TOKEN, i + IMAGE_TOKEN.length())) {
            markers++;
        }
        if (markers != urls.size()) {
            throw new IllegalArgumentException("the prompt contains " + markers + " image tokens for " + urls.size()
                    + " images (do not type the placeholder token into message text)");
        }
        List<InklingImageProcessor.Patches> tiles = new ArrayList<>();
        try {
            for (String url : urls) {
                try {
                    tiles.add(imageProcessor.process(smile.llm.model.minimax.MiniMaxMedia.image(url)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("unusable image: " + e.getMessage(), e);
                }
            }
            StringBuilder out = new StringBuilder(text.length() + 16 * tiles.size() * 64);
            int at = 0;
            for (InklingImageProcessor.Patches p : tiles) {
                int next = text.indexOf(IMAGE_TOKEN, at);
                out.append(text, at, next).append(IMAGE_TOKEN.repeat(p.grid()[1] * p.grid()[2]));
                at = next + IMAGE_TOKEN.length();
            }
            out.append(text, at, text.length());
            int[] ids = tokenizer.encodeWithSpecials(out.toString());
            if (ids.length > args.maxSeqLen()) {
                throw new IllegalArgumentException("the prompt (" + ids.length + " tokens with its images) is longer than max_seq_len");
            }
            Tensor pixels = tiles.isEmpty() ? null
                    : tiles.size() == 1 ? tiles.get(0).pixels().copy()
                    : Tensor.vstack(tiles.stream().map(InklingImageProcessor.Patches::pixels).toArray(Tensor[]::new));
            int[][] grids = tiles.stream().map(InklingImageProcessor.Patches::grid).toArray(int[][]::new);
            return new InklingMultimodal(ids, pixels, grids);
        } finally {
            for (var p : tiles) {
                p.pixels().close();
            }
        }
    }

    @Override
    public Tensor prefillMultimodal(int requestId, smile.llm.engine.MultimodalPrompt prompt, int from, int to) {
        InklingMultimodal mm = (InklingMultimodal) prompt;
        InklingState st = session(requestId);
        if (st.length != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + st.length + ", not " + from);
        }
        int[] ids = mm.inputIds();
        boolean last = to == ids.length;
        InklingDraft.State noDraft = drafts.remove(requestId);      // image prompts are not speculated
        if (noDraft != null) {
            onWorker(() -> {
                noDraft.close();
                return null;
            });
        }
        return onWorker(() -> {
            try (var guard = Tensor.noGradGuard()) {
                ImageState is = images.get(requestId);
                if (is == null && mm.hasVision()) {
                    // encode every image once, at the first chunk
                    Tensor embeds = Ix.keep(Ix.stepScoped(() -> vision.encode(mm.pixels(), mm.grids())));
                    int[] before = new int[ids.length + 1];
                    for (int i = 0; i < ids.length; i++) {
                        before[i + 1] = before[i] + (ids[i] == visionArgs.imageToken() ? 1 : 0);
                    }
                    if (before[ids.length] != embeds.size(0)) {
                        embeds.close();
                        throw new IllegalArgumentException("the prompt has " + before[ids.length] + " image tokens for "
                                + embeds.size(0) + " image embeddings");
                    }
                    is = new ImageState(embeds, before);
                    images.put(requestId, is);
                }
                int limit = st.maxChunk(args);
                int pos = from;
                Tensor logits = null;
                while (pos < to) {
                    int end = Math.min(to, pos + limit);
                    long[] chunk = new long[end - pos];
                    java.util.List<Long> where = new ArrayList<>();
                    for (int i = 0; i < chunk.length; i++) {
                        chunk[i] = ids[pos + i];
                        if (is != null && ids[pos + i] == visionArgs.imageToken()) {
                            where.add((long) i);
                        }
                    }
                    Tensor rows = null;
                    long[] positions = null;
                    if (!where.isEmpty()) {
                        positions = where.stream().mapToLong(Long::longValue).toArray();
                        rows = Ix.narrow(is.embeds, 0, is.before[pos], positions.length);
                    }
                    boolean wantLogits = last && end == to;
                    Tensor t = model.forward(st, chunk, wantLogits ? null : new int[0], null, rows, positions);
                    if (wantLogits) {
                        logits = t;
                    }
                    pos = end;
                }
                return logits;
            }
        });
    }

    // ------------------------------------------------------------------ ModelExecutor

    @Override
    public LanguageModel model() {
        return this;
    }

    @Override
    public KvCachePool kvCachePool() {
        return null;
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
    public String tryDecode(int[] tokens, boolean skipSpecial) throws java.nio.charset.CharacterCodingException {
        return tokenizer.tryDecode(tokens, skipSpecial);
    }

    @Override
    public int bind(int[] prompt, int totalCapacity) {
        if (totalCapacity > args.maxSeqLen()) {
            throw new IllegalArgumentException("capacity " + totalCapacity + " exceeds max_seq_len " + args.maxSeqLen());
        }
        int margin = draft != null && decoder != null ? InklingState.VERIFY_ROWS : 0;
        InklingState state = onWorker(() -> model.newState(Math.max(1, totalCapacity) + margin));
        int id = nextId.getAndIncrement();
        sessions.put(id, state);
        if (margin > 0) {
            drafts.put(id, onWorker(() -> draft.newState(Math.max(1, totalCapacity) + margin)));
        }
        if (prefixCache != null && prefixReuse && prompt != null) {
            // the last prompt token is always processed (its logits are sampled)
            InklingPrefixCache.Entry hit;
            synchronized (prefixCache) {
                hit = prefixCache.acquire(prompt, prompt.length - 1);
            }
            if (hit != null) {
                prefixMatches.put(id, hit);
            }
        }
        return id;
    }

    /**
     * Turns prefix reuse on or off.
     *
     * @param enabled whether requests restore stored snapshots and finished requests store theirs.
     */
    public void setPrefixReuseEnabled(boolean enabled) {
        this.prefixReuse = enabled;
    }

    /**
     * Prefix-cache statistics.
     *
     * @return a one-line summary, or an empty string when the cache is off.
     */
    public String prefixCacheStats() {
        if (prefixCache == null) {
            return "";
        }
        synchronized (prefixCache) {
            return prefixCache.stats();
        }
    }

    @Override
    public int prefixLen(int requestId) {
        InklingPrefixCache.Entry hit = prefixMatches.get(requestId);
        return hit == null ? 0 : hit.length();
    }

    /** Loads the matched snapshot into the request's (empty) state; the engine then prefills from its length. */
    @Override
    public void warmPrefix(int requestId, int[] prompt, int prefixLen) {
        InklingPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit == null) {
            return;
        }
        try {
            if (prefixLen != hit.length()) {
                throw new IllegalStateException("request " + requestId + " resumes at " + prefixLen
                        + " but its stored prefix has " + hit.length() + " tokens");
            }
            InklingState st = session(requestId);
            onWorker(() -> {
                st.restore(hit.snapshot);
                return null;
            });
        } finally {
            synchronized (prefixCache) {
                prefixCache.release(hit);
            }
        }
    }

    @Override
    public Tensor prefill(int requestId, int[] prompt, int prefixLen) {
        return prefillChunk(requestId, prompt, prefixLen, prompt.length);
    }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        InklingState st = session(requestId);
        if (st.length != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + st.length + ", not " + from);
        }
        boolean last = to == prompt.length;
        return onWorker(() -> {
            try (var guard = Tensor.noGradGuard()) {
                int limit = st.maxChunk(args);
                int pos = from;
                Tensor logits = null;
                while (pos < to) {
                    int end = Math.min(to, pos + limit);
                    // stop at the next position worth a snapshot: the state there is a prefix of later prompts
                    if (prefixCache != null && prefixReuse) {
                        int cut = (pos / SNAPSHOT_EVERY + 1) * SNAPSHOT_EVERY;
                        if (cut < prompt.length && cut < end && cut <= SNAPSHOT_MAX) {
                            end = cut;
                        }
                    }
                    long[] ids = new long[end - pos];
                    for (int i = 0; i < ids.length; i++) {
                        ids[i] = prompt[pos + i];
                    }
                    boolean wantLogits = last && end == to;
                    InklingDraft.State ds = drafts.get(requestId);
                    final int at = pos;
                    Tensor t = model.forward(st, ids, wantLogits ? null : new int[0], null, null, null,
                            ds == null ? null : taps -> draft.absorb(ds, taps, at));
                    if (wantLogits) {
                        logits = t;
                    }
                    pos = end;
                    if (prefixCache != null && prefixReuse && pos % SNAPSHOT_EVERY == 0 && pos < prompt.length && pos <= SNAPSHOT_MAX) {
                        store(st, Arrays.copyOf(prompt, pos));
                    }
                }
                return logits;
            }
        });
    }

    /** Stores a snapshot of {@code st}, which has processed exactly {@code tokens}; on the worker thread. */
    private void store(InklingState st, int[] tokens) {
        if (tokens.length < SNAPSHOT_MIN || st.length != tokens.length) {
            return;
        }
        long bytes = InklingState.snapshotBytes(args, tokens.length);
        synchronized (prefixCache) {
            if (!prefixCache.fits(bytes) || prefixCache.has(tokens, tokens.length)) {
                return;
            }
        }
        InklingState.Snapshot snap = st.snapshot(args);
        synchronized (prefixCache) {
            prefixCache.put(tokens, snap);
        }
    }

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        int n = requestIds.length;
        InklingState[] states = new InklingState[n];
        for (int i = 0; i < n; i++) {
            states[i] = session(requestIds[i]);
            if (states[i].length != positions[i]) {
                throw new IllegalStateException("request " + requestIds[i] + " is at position " + states[i].length
                        + ", not " + positions[i]);
            }
        }
        if (decoder != null) {
            return onWorker(() -> {
                try (var guard = Tensor.noGradGuard()) {
                    return stepScoped(() -> fusedStep(states, lastTokens));
                }
            });
        }
        return onWorker(() -> {
            try (var guard = Tensor.noGradGuard()) {
                return stepScoped(() -> {
                    Tensor[] rows = new Tensor[n];
                    for (int i = 0; i < n; i++) {
                        rows[i] = model.forward(states[i], new long[]{lastTokens[i]}, null, null);
                    }
                    return n == 1 ? rows[0] : cat(0, rows);
                });
            }
        });
    }

    /** A captured decode step of {@code nb} rows: the fixed device inputs and the output logits. */
    private final class DecodeGraph {
        final int nb;
        /** Rows per request: 1 for decode, the verify chunk length otherwise. */
        final int rowsPer;
        final Tensor ids;
        final Tensor pos;
        final Tensor tbl;
        /** Verify graphs: the draft's target features {@code [nb, 4, D]} and the rows kept per request. */
        Tensor taps, accepted;
        Tensor logits;
        java.lang.foreign.MemorySegment graph;

        DecodeGraph(int nb) {
            this(nb, 1);
        }

        DecodeGraph(int nb, int rowsPer) {
            this.nb = nb;
            this.rowsPer = rowsPer;
            if (rowsPer > 1) {
                this.taps = Ix.keep(Ix.zeros(device, smile.deep.tensor.ScalarType.BFloat16, nb, 4, args.dim()));
                this.accepted = Ix.keep(Tensor.of(new int[nb / rowsPer], nb / rowsPer).to(device));
            }
            this.ids = Ix.keep(Tensor.of(new long[nb], nb).to(device));
            this.pos = Ix.keep(Tensor.of(new int[nb], nb).to(device));
            this.tbl = Ix.keep(Tensor.of(new long[args.numApplications() * nb * 8], args.numApplications() * nb * 8L).to(device));
        }

        /** Writes a verify chunk's inputs: {@code rows[i]} are the tokens of request {@code i} at its next positions. */
        void fillVerify(InklingState[] states, int[][] rows) {
            int apps = args.numApplications();
            int reqs = nb / rowsPer;
            long[] idv = new long[nb];
            int[] pv = new int[nb];
            long[] tv = new long[apps * nb * 8];
            for (int r = 0; r < reqs; r++) {
                InklingState st = r < states.length ? states[r] : padState;
                long[] t = st.table();
                for (int j = 0; j < rowsPer; j++) {
                    int i = r * rowsPer + j;
                    idv[i] = r < states.length ? rows[r][j] : 0;
                    pv[i] = (r < states.length ? st.length : 0) + j;
                    for (int a = 0; a < apps; a++) {
                        System.arraycopy(t, a * 8, tv, (a * nb + i) * 8, 8);
                    }
                }
            }
            smile.torch.Aten.call("aten::copy_", ids, Tensor.of(idv, nb).to(device), false);
            smile.torch.Aten.call("aten::copy_", pos, Tensor.of(pv, nb).to(device), false);
            smile.torch.Aten.call("aten::copy_", tbl, Tensor.of(tv, tv.length).to(device), false);
        }

        /** Writes the inputs: real rows first, padding rows on the pad state. */
        void fill(InklingState[] states, int[] lastTokens) {
            int apps = args.numApplications();
            long[] idv = new long[nb];
            int[] pv = new int[nb];
            long[] tv = new long[apps * nb * 8];
            for (int i = 0; i < nb; i++) {
                InklingState st = i < states.length ? states[i] : padState;
                idv[i] = i < states.length ? lastTokens[i] : 0;
                pv[i] = i < states.length ? st.length : 0;
                long[] t = st.table();
                for (int a = 0; a < apps; a++) {
                    System.arraycopy(t, a * 8, tv, (a * nb + i) * 8, 8);
                }
            }
            smile.torch.Aten.call("aten::copy_", ids, Tensor.of(idv, nb).to(device), false);
            smile.torch.Aten.call("aten::copy_", pos, Tensor.of(pv, nb).to(device), false);
            smile.torch.Aten.call("aten::copy_", tbl, Tensor.of(tv, tv.length).to(device), false);
        }
    }

    /** Captures the decode step of {@code nb} rows (on the worker thread); {@code null} if capture fails. */
    private DecodeGraph capture(int nb) {
        if (padState == null) {
            padState = model.newState(16);
        }
        DecodeGraph g = new DecodeGraph(nb);
        try {
            g.fill(new InklingState[0], new int[0]);
            // eager warm-up: libraries initialise their workspaces outside the capture
            Tensor warm = stepScoped(() -> decoder.forward(nb, g.ids, g.pos, g.tbl, null));
            smile.torch.Aten.one("aten::sum", warm).doubleValue();
            warm.close();
            g.graph = smile.torch.Native.cudaGraphCreate();
            smile.torch.Native.cudaGraphCaptureBegin(g.graph, device.index());
            try {
                g.logits = stepScoped(() -> decoder.forward(nb, g.ids, g.pos, g.tbl, null));
            } finally {
                smile.torch.Native.cudaGraphCaptureEnd(g.graph);
            }
            g.logits.detachFromScopes();
            return g;
        } catch (RuntimeException e) {
            logger.warn("Inkling decode CUDA graph capture failed for batch {}: {}", nb, e.toString());
            return null;
        }
    }

    private Tensor graphStep(InklingState[] states, int[] lastTokens) {
        int n = states.length;
        int nb = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
        DecodeGraph g = graphs.get(nb);
        if (g == null) {
            g = capture(nb);
            if (g == null) {
                graphsDisabled = true;
                return null;
            }
            graphs.put(nb, g);
            logger.info("Inkling decode CUDA graph captured for batch {}", nb);
        }
        g.fill(states, lastTokens);
        smile.torch.Native.cudaGraphReplay(g.graph);
        for (InklingState s : states) {
            s.length++;
        }
        return smile.torch.Aten.one("aten::clone", Ix.narrow(g.logits, 0, 0, n));
    }

    /** The captured commit + absorb + draft of one (requests, rows) shape, chained after a verify graph. */
    private final class DraftGraph {
        final int reqs, rowsPer;
        final DecodeGraph verify;
        final Tensor anchor, p0, ptrs, rowT, row8, first, freq;
        Tensor drafts;
        java.lang.foreign.MemorySegment graph;

        DraftGraph(DecodeGraph verify, int reqs, int rowsPer) {
            this.verify = verify;
            this.reqs = reqs;
            this.rowsPer = rowsPer;
            int block = draft.drafts() + 1;
            anchor = Ix.keep(Tensor.of(new long[reqs], reqs).to(device));
            p0 = Ix.keep(Tensor.of(new int[reqs], reqs).to(device));
            ptrs = Ix.keep(Tensor.of(new long[reqs * draft.pointerStride()], (long) reqs * draft.pointerStride()).to(device));
            int[] rt = new int[rowsPer];
            int[] r8 = new int[block];
            long[] f = new long[reqs];
            for (int i = 0; i < rt.length; i++) {
                rt[i] = i;
            }
            for (int i = 0; i < r8.length; i++) {
                r8[i] = i;
            }
            for (int i = 0; i < reqs; i++) {
                f[i] = (long) i * rowsPer;
            }
            rowT = Ix.keep(Tensor.of(rt, rt.length).to(device));
            row8 = Ix.keep(Tensor.of(r8, r8.length).to(device));
            first = Ix.keep(Tensor.of(f, reqs).to(device));
            freq = draft.frequencies();
        }

        void fill(InklingDraft.State[] ds, InklingState[] states, int[] anchors) {
            int stride = draft.pointerStride();
            long[] pt = new long[reqs * stride];
            long[] av = new long[reqs];
            int[] pv = new int[reqs];
            for (int i = 0; i < reqs; i++) {
                boolean real = i < ds.length;
                long[] row = (real ? ds[i] : padDraft).pointers();
                System.arraycopy(row, 0, pt, i * stride, stride);
                av[i] = real ? anchors[i] : 0;
                pv[i] = real ? states[i].length : 0;
            }
            smile.torch.Aten.call("aten::copy_", ptrs, Tensor.of(pt, pt.length).to(device), false);
            smile.torch.Aten.call("aten::copy_", anchor, Tensor.of(av, reqs).to(device), false);
            smile.torch.Aten.call("aten::copy_", p0, Tensor.of(pv, reqs).to(device), false);
        }

        Tensor body() {
            decoder.commit(reqs, rowsPer, verify.tbl, verify.accepted);
            return draft.round(reqs, rowsPer, verify.taps, verify.accepted, p0, anchor, ptrs, rowT, row8, first, freq);
        }
    }

    private final Map<Integer, DraftGraph> draftGraphs = new java.util.HashMap<>();
    private InklingDraft.State padDraft;

    private DraftGraph draftGraph(DecodeGraph verify, int reqs, int rowsPer) {
        int key = reqs * 64 + rowsPer;
        DraftGraph d = draftGraphs.get(key);
        if (d != null) {
            return d;
        }
        if (padDraft == null) {
            padDraft = draft.newState(64);
        }
        long t0 = System.nanoTime();
        DraftGraph dg = new DraftGraph(verify, reqs, rowsPer);
        dg.fill(new InklingDraft.State[0], new InklingState[0], new int[0]);
        // warm-up with accepted = 0 (commit and absorb leave every real state alone: nothing is real here)
        Tensor warm = stepScoped(dg::body);
        smile.torch.Aten.one("aten::sum", warm).doubleValue();
        warm.close();
        dg.graph = smile.torch.Native.cudaGraphCreate();
        smile.torch.Native.cudaGraphCaptureBegin(dg.graph, device.index());
        try {
            dg.drafts = stepScoped(dg::body);
        } finally {
            smile.torch.Native.cudaGraphCaptureEnd(dg.graph);
        }
        dg.drafts.detachFromScopes();
        draftGraphs.put(key, dg);
        logger.info("Inkling draft CUDA graph captured for {} request(s) x {} rows in {} ms", reqs, rowsPer,
                (System.nanoTime() - t0) / 1_000_000);
        return dg;
    }

    /** The verify graph of {@code reqs} requests (a power of two) of {@code rowsPer} rows; captured on first use. */
    private DecodeGraph verifyGraph(int reqs, int rowsPer) {
        int key = reqs * 64 + rowsPer;
        DecodeGraph g = verifyGraphs.get(key);
        if (g != null) {
            return g;
        }
        if (padState == null) {
            padState = model.newState(16);
        }
        long t0 = System.nanoTime();
        int nb = reqs * rowsPer;
        g = new DecodeGraph(nb, rowsPer);
        DecodeGraph gg = g;
        gg.fillVerify(new InklingState[0], new int[0][]);
        Tensor warm = stepScoped(() -> decoder.forward(nb, rowsPer, gg.ids, gg.pos, gg.tbl, null, gg.taps));
        smile.torch.Aten.one("aten::sum", warm).doubleValue();
        warm.close();
        gg.graph = smile.torch.Native.cudaGraphCreate();
        smile.torch.Native.cudaGraphCaptureBegin(gg.graph, device.index());
        try {
            gg.logits = stepScoped(() -> decoder.forward(nb, rowsPer, gg.ids, gg.pos, gg.tbl, null, gg.taps));
        } finally {
            smile.torch.Native.cudaGraphCaptureEnd(gg.graph);
        }
        gg.logits.detachFromScopes();
        verifyGraphs.put(key, gg);
        logger.info("Inkling verify CUDA graph captured for {} request(s) x {} rows in {} ms", reqs, rowsPer,
                (System.nanoTime() - t0) / 1_000_000);
        return gg;
    }

    /** Whether the draft model is loaded and the fused decoder can verify chunks. */
    public boolean hasDraft() {
        return draft != null && decoder != null && GRAPHS && smile.torch.Native.cudaGraphAvailable();
    }

    /** Draft tokens per block. */
    public int draftBlock() {
        return draft == null ? 0 : draft.drafts();
    }

    @Override
    public boolean speculationMayOvershoot() {
        return true;
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts, double temperature,
                                 double topp) {
        double[] t = new double[requestIds.length];
        double[] p = new double[requestIds.length];
        java.util.Arrays.fill(t, temperature);
        java.util.Arrays.fill(p, topp);
        return speculateStep(requestIds, lastTokens, positions, numDrafts, t, p);
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts, double[] temperatures,
                                 double[] topps) {
        if (!hasDraft()) {
            throw new UnsupportedOperationException("no draft model");
        }
        int n = requestIds.length;
        InklingState[] states = new InklingState[n];
        InklingDraft.State[] ds = new InklingDraft.State[n];
        for (int i = 0; i < n; i++) {
            states[i] = session(requestIds[i]);
            ds[i] = drafts.get(requestIds[i]);
            if (states[i].length != positions[i]) {
                throw new IllegalStateException("request " + requestIds[i] + " is at position " + states[i].length
                        + ", not " + positions[i]);
            }
            if (ds[i] == null || ds[i].absorbed < positions[i]) {
                throw new UnsupportedOperationException("request " + requestIds[i] + " has no draft context");
            }
        }
        int rowsPer = Math.max(2, Math.min(numDrafts + 1, Math.min(draft.drafts() + 1, InklingState.VERIFY_ROWS)));
        int reqs = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
        return onWorker(() -> {
            try (var guard = Tensor.noGradGuard()) {
                return speculateRound(states, ds, lastTokens, rowsPer, reqs, temperatures, topps);
            }
        });
    }

    /** One verify + commit + absorb + draft round of {@code states.length} requests (on the worker thread). */
    private int[][] speculateRound(InklingState[] states, InklingDraft.State[] ds, int[] lastTokens, int rowsPer, int reqs,
                                   double[] temperatures, double[] topps) {
        int n = states.length;
        long t0 = System.nanoTime();
        int[][] rows = new int[n][rowsPer];
        for (int i = 0; i < n; i++) {
            if (ds[i].pending == null) {
                int idx = i;
                ds[i].pending = scoped(() -> draft.draft(ds[idx], lastTokens[idx], states[idx].length));
            }
            rows[i][0] = lastTokens[i];
            System.arraycopy(ds[i].pending, 0, rows[i], 1, rowsPer - 1);
        }
        long t1 = System.nanoTime();
        DecodeGraph g = verifyGraph(reqs, rowsPer);
        DraftGraph dg = draftGraph(g, reqs, rowsPer);
        g.fillVerify(states, rows);
        smile.torch.Native.cudaGraphReplay(g.graph);
        int[][] out = new int[n][];
        int[] kept = new int[reqs];
        int[] anchors = new int[n];
        long t2 = System.nanoTime();
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            for (int i = 0; i < n; i++) {
                Tensor lg = Ix.narrow(g.logits, 0, (long) i * rowsPer, rowsPer);
                int[] s = Sampling.sampleTokenIds(lg, temperatures[i], topps[i]);
                int a = 0;
                while (a < rowsPer - 1 && rows[i][a + 1] == s[a]) {
                    a++;
                }
                out[i] = new int[a + 1];
                System.arraycopy(rows[i], 1, out[i], 0, a);
                out[i][a] = s[a];
                kept[i] = a + 1;
                anchors[i] = s[a];
            }
            long t3 = System.nanoTime();
            smile.torch.Aten.call("aten::copy_", g.accepted, Tensor.of(kept, reqs).to(device), false);
            dg.fill(ds, states, anchors);
            smile.torch.Native.cudaGraphReplay(dg.graph);
            long[] next = dg.drafts.to(smile.deep.tensor.Device.CPU()).longArray();
            int nd = draft.drafts();
            for (int i = 0; i < n; i++) {
                ds[i].pending = new int[nd];
                for (int j = 0; j < nd; j++) {
                    ds[i].pending[j] = (int) next[i * nd + j];
                }
                ds[i].absorbed = states[i].length + kept[i];
                states[i].length += kept[i];
            }
            if (TIMING) {
                logger.info("spec round: draft {} ms, verify+fill {} ms, sample+accept {} ms, commit+absorb+draft {} ms", (t1 - t0) / 1e6,
                        (t2 - t1) / 1e6, (t3 - t2) / 1e6, (System.nanoTime() - t3) / 1e6);
            }
        } finally {
            Tensor.pop();
        }
        return out;
    }

    private static final boolean TIMING = System.getProperty("smile.inkling.timing") != null;

    /** Runs {@code task} in its own tensor scope (the draft's temporaries are freed on return). */
    private static <T> T scoped(java.util.function.Supplier<T> task) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            return task.get();
        } finally {
            Tensor.pop();
        }
    }

    /** One decode step of all requests with the fused kernels (on the worker thread). */
    private Tensor fusedStep(InklingState[] states, int[] lastTokens) {
        if (GRAPHS && !graphsDisabled && states.length <= MAX_GRAPH_BATCH && smile.torch.Native.cudaGraphAvailable()) {
            Tensor t = graphStep(states, lastTokens);
            if (t != null) {
                return t;
            }
        }
        int nb = states.length;
        int apps = args.numApplications();
        long[] ids = new long[nb];
        int[] pos = new int[nb];
        long[] table = new long[apps * nb * 8];
        for (int i = 0; i < nb; i++) {
            ids[i] = lastTokens[i];
            pos[i] = states[i].length;
            long[] t = states[i].table();
            for (int a = 0; a < apps; a++) {
                System.arraycopy(t, a * 8, table, (a * nb + i) * 8, 8);
            }
        }
        Tensor logits = decoder.forward(nb, Tensor.of(ids, nb).to(device), Tensor.of(pos, nb).to(device),
                Tensor.of(table, table.length).to(device), null);
        for (InklingState s : states) {
            s.length++;
        }
        return logits;
    }

    @Override
    public void finish(int requestId, int[] sequenceTokens) {
        InklingState st = sessions.get(requestId);
        if (st != null && prefixCache != null && prefixReuse && sequenceTokens != null && !images.containsKey(requestId)
                && st.length >= SNAPSHOT_MIN && st.length <= sequenceTokens.length) {
            // the whole conversation so far is the likely prefix of the next turn
            int[] tokens = Arrays.copyOf(sequenceTokens, st.length);
            onWorker(() -> {
                try (var guard = Tensor.noGradGuard()) {
                    store(st, tokens);
                }
                return null;
            });
        }
        evict(requestId);
    }

    @Override
    public void evict(int requestId) {
        InklingPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit != null) {
            synchronized (prefixCache) {
                prefixCache.release(hit);
            }
        }
        ImageState is = images.remove(requestId);
        if (is != null) {
            onWorker(() -> {
                is.embeds.close();
                return null;
            });
        }
        InklingDraft.State ds = drafts.remove(requestId);
        if (ds != null) {
            onWorker(() -> {
                ds.close();
                return null;
            });
        }
        InklingState s = sessions.remove(requestId);
        if (s != null) {
            onWorker(() -> {
                s.close();
                return null;
            });
        }
    }

    /**
     * Tokens processed so far for a request.
     *
     * @param requestId the request id from {@link #bind}.
     * @return the position of its next token.
     */
    public int positionOf(int requestId) {
        return session(requestId).length;
    }

    @Override
    public void close() {
        for (int id : new ArrayList<>(sessions.keySet())) {
            evict(id);
        }
        onWorker(() -> {
            if (prefixCache != null) {
                synchronized (prefixCache) {
                    prefixCache.clear();
                }
            }
            for (DecodeGraph g : graphs.values()) {
                if (g.graph != null) {
                    smile.torch.Native.cudaGraphDestroy(g.graph);
                    g.graph = null;
                }
            }
            graphs.clear();
            for (DecodeGraph g : verifyGraphs.values()) {
                if (g.graph != null) {
                    smile.torch.Native.cudaGraphDestroy(g.graph);
                    g.graph = null;
                }
            }
            verifyGraphs.clear();
            for (DraftGraph d : draftGraphs.values()) {
                if (d.graph != null) {
                    smile.torch.Native.cudaGraphDestroy(d.graph);
                    d.graph = null;
                }
            }
            draftGraphs.clear();
            if (padDraft != null) {
                padDraft.close();
                padDraft = null;
            }
            for (InklingDraft.State d : drafts.values()) {
                d.close();
            }
            drafts.clear();
            if (padState != null) {
                padState.close();
                padState = null;
            }
            return null;
        });
        worker.shutdown();
    }
}
