/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
import smile.llm.parallel.ParallelConfig;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.template.GlmChatTemplate;

/**
 * GLM-5.3-Flash behind the continuous-batching engine: a {@link LanguageModel} and {@link ModelExecutor} over
 * {@link GlmModel}.
 *
 * <p>With several devices every device holds a replica of the dense layers and {@code 1/n} of each layer's routed
 * experts (expert parallelism, partial outputs summed with NCCL); each device is driven by its own thread because the
 * collectives need all ranks to be inside the same layer at once. One request's state is a {@link GlmModel.Session}
 * per rank: a slot in the KDA state pools and the MLA latent and indexer caches of the sparse layers.
 *
 * <p>A prompt is prefilled in chunks (the engine's chunk boundaries carry the KDA and cache state across calls); a
 * decode step over {@code B} requests is one batched pass. Prefix reuse restores whole-request snapshots
 * ({@link GlmPrefixCache}). Not yet: speculation (MTP), images.
 *
 * @author Haifeng Li
 */
public final class Glm implements LanguageModel, ModelExecutor, AutoCloseable {
    private final String name;
    private final GlmModelArgs args;
    private final GlmTokenizer tokenizer;
    private final GlmChatTemplate template = new GlmChatTemplate();
    private final TensorParallelGroup group;
    private final ExecutorService[] workers;
    private final GlmModel[] replicas;
    private final Map<Integer, GlmModel.Session[]> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final int maxSessions;
    /** The vision encoder's arguments, or {@code null} when the checkpoint has no vision weights. */
    private final GlmVisionArgs visionArgs;
    private final GlmVisionProcessor visionProcessor;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Glm.class);
    private static final boolean GRAPHS = !"0".equals(System.getenv("SMILE_GLM_GRAPH"));
    private static final int MAX_GRAPH_BATCH = Integer.getInteger("smile.glm.graph.maxbatch", 32);
    private final Map<Integer, GlmDecodeGraph>[] graphs;
    private final Map<Integer, GlmWindowGraph>[] windowGraphs;
    private final Map<Integer, GlmMtpGraph>[] mtpGraphs;
    private volatile boolean mtpGraphsDisabled;
    private volatile boolean windowGraphsDisabled;
    private static final boolean WINDOW_GRAPHS = !"0".equals(System.getenv("SMILE_GLM_WINDOW_GRAPH"));
    private static final boolean MTP_GRAPHS = !"0".equals(System.getenv("SMILE_GLM_MTP_GRAPH"));
    private static final int MAX_WINDOW_BATCH = Integer.getInteger("smile.glm.window.maxbatch", 8);
    private final boolean onCuda;
    private volatile boolean graphsDisabled;
    /** Graph keys captured on every rank (checked on the calling thread so that a ready graph costs no round trip to the rank threads). */
    private final Set<String> readyGraphs = ConcurrentHashMap.newKeySet();
    /** Snapshot budget per rank in MB ({@code -Dsmile.glm.prefix.cache.mb}, 0 = prefix reuse off). */
    /** Prompts shorter than this are not worth a snapshot. */
    private final int prefixMinTokens = Integer.getInteger("smile.glm.prefix.min.tokens", 512);
    private GlmPrefixCache prefixCache;
    private final Map<Integer, GlmPrefixCache.Entry> prefixMatches = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private Glm(String name, GlmModelArgs args, GlmTokenizer tokenizer, Path dir, byte[] devices, int maxSessions) {
        this.name = name;
        this.args = args;
        this.tokenizer = tokenizer;
        this.maxSessions = maxSessions;
        GlmVisionArgs va = null;
        try {
            Path index = dir.resolve("model.safetensors.index.json");
            if (!"0".equals(System.getenv("SMILE_GLM_VISION")) && java.nio.file.Files.exists(index)
                    && java.nio.file.Files.readString(index).contains("\"model.visual.patch_embed.proj.weight\"")) {
                va = GlmVisionArgs.load(dir);
            }
        } catch (IOException e) {
            logger.warn("cannot read the vision config: {}", e.toString());
        }
        this.visionArgs = va;
        this.visionProcessor = va == null ? null : new GlmVisionProcessor(va);
        int ranks = devices == null ? 1 : devices.length;
        this.group = ranks > 1 ? new TensorParallelGroup(ParallelConfig.tensorParallel(devices)) : null;
        if (group != null && !"0".equals(System.getenv("SMILE_GLM_CUSTOM_AR"))) {
            group.enableCustomAllReduce();
        }
        this.workers = new ExecutorService[ranks];
        this.replicas = new GlmModel[ranks];
        this.graphs = new Map[ranks];
        this.windowGraphs = new Map[ranks];
        this.mtpGraphs = new Map[ranks];
        for (int r = 0; r < ranks; r++) {
            graphs[r] = new java.util.HashMap<>();
            windowGraphs[r] = new java.util.HashMap<>();
            mtpGraphs[r] = new java.util.HashMap<>();
        }
        this.onCuda = devices != null;
        for (int r = 0; r < ranks; r++) {
            // a rank that fails must not leave the others blocked in a collective: daemon threads
            workers[r] = Executors.newSingleThreadExecutor(runnable -> {
                Thread t = new Thread(runnable, "glm-rank");
                t.setDaemon(true);
                return t;
            });
        }
        List<Future<?>> loading = new ArrayList<>();
        for (int r = 0; r < ranks; r++) {
            final int rank = r;
            loading.add(workers[r].submit(() -> {
                Device device = devices == null ? Device.CPU() : Device.CUDA(devices[rank]);
                replicas[rank] = new GlmModel(dir, args, device, group, rank, ranks, maxSessions);
                return null;
            }));
        }
        await(loading);
        long cacheMb = Long.getLong("smile.glm.prefix.cache.mb", 4096L);
        if (cacheMb > 0) {
            prefixCache = new GlmPrefixCache(cacheMb << 20, this::disposeEntry);
        }
    }

    /** Frees a snapshot on every rank thread. */
    private void disposeEntry(GlmPrefixCache.Entry e) {
        List<Future<?>> frees = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            final GlmModel.Snapshot snap = e.ranks[r];
            frees.add(workers[r].submit(() -> {
                snap.close();
                return null;
            }));
        }
        try {
            await(frees);
        } catch (RuntimeException ex) {
            logger.warn("GLM prefix snapshot release failed: {}", ex.toString());
        }
    }

    /**
     * Prefix cache statistics.
     *
     * @return a one-line summary, or {@code "disabled"}.
     */
    public String prefixCacheStats() {
        return prefixCache == null ? "disabled" : prefixCache.stats();
    }

    /**
     * Loads a GLM-5.3-Flash checkpoint.
     *
     * @param checkpointDir the checkpoint directory.
     * @param layers        the number of layers to keep (0 = all), for slices.
     * @param maxSeqLen     the longest sequence served (0 = the checkpoint's).
     * @param devices       the GPUs (one rank each; experts are split over them), or {@code null} for the CPU.
     * @param maxSessions   the number of concurrent requests (slots of the KDA state pools).
     * @return the model.
     * @throws IOException if the checkpoint cannot be read.
     */
    public static Glm build(String checkpointDir, int layers, int maxSeqLen, byte[] devices, int maxSessions)
            throws IOException {
        Path dir = Path.of(checkpointDir);
        GlmModelArgs args = GlmModelArgs.load(dir.resolve("config.json").toFile());
        if (layers > 0) {
            args = args.slice(layers);
        }
        if (maxSeqLen > 0) {
            args = args.withMaxSeqLen(maxSeqLen);
        }
        return new Glm(dir.getFileName().toString(), args, GlmTokenizer.of(checkpointDir), dir, devices,
                Math.max(1, maxSessions));
    }

    private static void await(List<? extends Future<?>> futures) {
        RuntimeException failure = null;
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                failure = failure != null ? failure : new RuntimeException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Runs a task on every rank thread; returns rank 0's result and discards the others'. */
    private <T> T onAllRanks(java.util.function.IntFunction<T> task, java.util.function.Consumer<T> discard) {
        List<Future<T>> futures = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            final int rank = r;
            Callable<T> c = () -> task.apply(rank);
            futures.add(workers[r].submit(c));
        }
        await(futures);
        try {
            T first = futures.get(0).get();
            for (int r = 1; r < futures.size(); r++) {
                T other = futures.get(r).get();
                if (other != null) {
                    discard.accept(other);
                }
            }
            return first;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private GlmModel.Session[] session(int requestId) {
        GlmModel.Session[] s = sessions.get(requestId);
        if (s == null) {
            throw new IllegalArgumentException("unknown request " + requestId);
        }
        return s;
    }

    // ------------------------------------------------------------------ LanguageModel

    @Override
    public String family() {
        return "glm";
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
            if (token < 0 || token >= args.vocabSize()) {
                throw new IllegalArgumentException("Prompt token id " + token + " out of range for vocab_size " + args.vocabSize());
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
            if (listener != null) {
                listener.onGeneratedTokens(completion.length);
                if (completion.length > 0) {
                    listener.onText(safeDecode(completion));
                }
            }
            return new ChatCompletion(name, safeDecode(completion), prompt, completion,
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

    /**
     * Display decode that never throws (incomplete UTF-8 at the end of a truncated completion is replaced).
     *
     * @param tokens token ids.
     * @return the text.
     */
    public String tryDecodeSafe(int[] tokens) {
        return safeDecode(tokens);
    }

    private String safeDecode(int[] tokens) {
        try {
            return tokenizer.tryDecode(tokens, true);
        } catch (Exception e) {
            return tokenizer.decode(tokens);
        }
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
        GlmModel.Session[] s = new GlmModel.Session[workers.length];
        List<Future<?>> allocation = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            final int rank = r;
            allocation.add(workers[r].submit(() -> {
                s[rank] = replicas[rank].newSession();
                return null;
            }));
        }
        try {
            await(allocation);
        } catch (RuntimeException e) {
            release(s);
            throw e;
        }
        int id = nextId.getAndIncrement();
        sessions.put(id, s);
        if (prefixCache != null && prompt != null) {
            GlmPrefixCache.Entry hit = prefixCache.acquire(prompt);
            if (hit != null) {
                prefixMatches.put(id, hit);
            }
        }
        return id;
    }

    @Override
    public int prefixLen(int requestId) {
        GlmPrefixCache.Entry hit = prefixMatches.get(requestId);
        return hit == null ? 0 : hit.position();
    }

    /** Loads the matched snapshot into the request's (empty) session; the engine then prefills from its position. */
    @Override
    public void warmPrefix(int requestId, int[] prompt, int prefixLen) {
        GlmPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit == null) {
            if (prefixLen != 0) {
                throw new IllegalStateException("request " + requestId + " has no prefix snapshot for " + prefixLen + " tokens");
            }
            return;
        }
        try {
            if (hit.position() != prefixLen) {
                throw new IllegalStateException("prefix snapshot at " + hit.position() + " cannot serve a restart at " + prefixLen);
            }
            GlmModel.Session[] s = session(requestId);
            gatherAllRanks(rank -> {
                try (var guard = Tensor.noGradGuard()) {
                    replicas[rank].restore(hit.ranks[rank], s[rank]);
                }
                return null;
            });
            logger.info("GLM prefix reuse: request {} resumes at token {} of {}", requestId, prefixLen, prompt.length);
        } finally {
            prefixCache.unpin(hit);
        }
    }

    /**
     * Whether images can be encoded (the checkpoint has the vision weights).
     *
     * @return {@code true} when {@link #processMultimodal} may be called.
     */
    public boolean isMultimodal() {
        return visionArgs != null;
    }

    private static final String IMAGE_MARKER = "<|image|>";

    /**
     * Renders a dialog with images: the chat template puts one image marker per image, every marker is expanded to one
     * placeholder per vision token (as the reference processor does), and the images are preprocessed to patch rows.
     *
     * @param dialog  the messages (image parts only: no video or audio).
     * @param options chat options.
     * @return the expanded token ids and the patches to encode during prefill.
     * @throws IOException              if an image cannot be read or decoded.
     * @throws IllegalArgumentException if the request is malformed.
     */
    public GlmMultimodal processMultimodal(Message[] dialog, ChatOptions options) throws IOException {
        if (visionArgs == null) {
            throw new IllegalArgumentException("this GLM-5.3 checkpoint has no vision weights");
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
                    throw new IllegalArgumentException("GLM-5.3 video input is not supported yet");
                } else if (part instanceof smile.llm.AudioUrlPart) {
                    throw new IllegalArgumentException("GLM-5.3 does not accept audio input");
                }
            }
        }
        String text = template.encode(Arrays.asList(dialog), options);
        int markers = 0;
        for (int i = text.indexOf(IMAGE_MARKER); i >= 0; i = text.indexOf(IMAGE_MARKER, i + IMAGE_MARKER.length())) {
            markers++;
        }
        if (markers != urls.size()) {
            throw new IllegalArgumentException("the prompt contains " + markers + " image markers for " + urls.size()
                    + " images (do not type the placeholder tokens into message text)");
        }
        int merge2 = visionArgs.mergeSize() * visionArgs.mergeSize();
        List<GlmVisionProcessor.Patches> images = new ArrayList<>();
        try {
            for (String url : urls) {
                try {
                    images.add(visionProcessor.image(smile.llm.model.minimax.MiniMaxMedia.image(url)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("unusable image: " + e.getMessage(), e);
                }
            }
            StringBuilder out = new StringBuilder(text.length() + 8 * images.size() * 256);
            int at = 0;
            for (GlmVisionProcessor.Patches p : images) {
                int next = text.indexOf(IMAGE_MARKER, at);
                int[] g = p.grid();
                out.append(text, at, next).append(IMAGE_MARKER.repeat(g[0] * g[1] * g[2] / merge2));
                at = next + IMAGE_MARKER.length();
            }
            out.append(text, at, text.length());
            int[] ids = tokenizer.encodeWithSpecials(out.toString());
            if (ids.length > args.maxSeqLen()) {
                throw new IllegalArgumentException("the prompt (" + ids.length + " tokens with its images) is longer than max_seq_len");
            }
            Tensor pixels = images.isEmpty() ? null
                    : images.size() == 1 ? images.get(0).pixels().copy()
                    : Tensor.vstack(images.stream().map(GlmVisionProcessor.Patches::pixels).toArray(Tensor[]::new));
            return new GlmMultimodal(ids, pixels, images.stream().map(GlmVisionProcessor.Patches::grid).toArray(int[][]::new));
        } finally {
            for (var p : images) {
                p.pixels().close();
            }
        }
    }

    /** Tokens of a multimodal prefill chunk (the forward over a long image prompt is cut to bound the activations). */
    private static final int MM_CHUNK = Integer.getInteger("smile.glm.vision.chunk", 1024);

    @Override
    public Tensor prefillMultimodal(int requestId, smile.llm.engine.MultimodalPrompt prompt, int from, int to) {
        if (!(prompt instanceof GlmMultimodal mm)) {
            throw new IllegalArgumentException("a GLM-5.3 multimodal prompt is required");
        }
        if (visionArgs == null) {
            throw new IllegalStateException("vision encoder not loaded");
        }
        int[] ids = mm.inputIds();
        if (from < 0 || to < from || to > ids.length) {
            throw new IllegalArgumentException("invalid prefill range [" + from + ", " + to + ") for len " + ids.length);
        }
        if (from == to) {
            return null;
        }
        final int imageId = visionArgs.imageTokenId();
        int imageCount = 0;
        for (int id : ids) {
            if (id == imageId) {
                imageCount++;
            }
        }
        final int imageTokens = imageCount;
        int merge2 = visionArgs.mergeSize() * visionArgs.mergeSize();
        long patches = 0;
        for (int[] g : mm.imageGrids()) {
            patches += (long) g[0] * g[1] * g[2];
        }
        if (imageTokens != patches / merge2) {
            throw new IllegalArgumentException("the prompt has " + imageTokens + " image tokens for " + patches / merge2 + " vision features");
        }
        GlmModel.Session[] s = session(requestId);
        if (s[0].length() != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + s[0].length() + ", not " + from);
        }
        final boolean last = to == ids.length;
        int before = 0;
        for (int i = 0; i < from; i++) {
            if (ids[i] == imageId) {
                before++;
            }
        }
        final int imagesBefore = before;
        List<Tensor> results = gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                GlmModel m = replicas[rank];
                Tensor features = imageTokens == 0 ? null : m.encodeImages(visionArgs, mm.imagePixels(), mm.imageGrids());
                try {
                    Tensor out = null;
                    int seen = imagesBefore;
                    for (int start = from; start < to; start += MM_CHUNK) {
                        final int st = start;
                        final int en = Math.min(to, start + MM_CHUNK);
                        int[] chunk = Arrays.copyOfRange(ids, st, en);
                        int nImage = 0;
                        for (int id : chunk) {
                            if (id == imageId) {
                                nImage++;
                            }
                        }
                        final Tensor rows = nImage == 0 ? null : Gx.narrow(features, 0, seen, nImage);
                        seen += nImage;
                        Tensor r = Gx.layerScoped(() -> {
                            Tensor hidden = m.forward(chunk, s[rank], null, rows, imageId);
                            if (m.hasMtp()) {
                                int n = en == ids.length ? chunk.length - 1 : chunk.length;
                                if (n > 0) {
                                    m.mtpAbsorb(s[rank], Arrays.copyOfRange(ids, st + 1, st + 1 + n), hidden);
                                }
                                m.setLastHidden(s[rank], hidden);
                            }
                            Tensor row = Gx.narrow(hidden, 0, hidden.size(0) - 1, 1);
                            return last && en == to ? m.logits(row) : Gx.zeros(smile.deep.tensor.ScalarType.Float, 1);
                        });
                        if (en == to) {
                            out = r;
                        } else {
                            r.close();
                        }
                    }
                    return out;
                } finally {
                    if (features != null) {
                        features.close();
                    }
                }
            }
        });
        for (int r = 1; r < results.size(); r++) {
            results.get(r).close();
        }
        Tensor logits = results.get(0);
        if (!last) {
            logits.close();
            return null;
        }
        return logits;
    }

    @Override
    public Tensor prefill(int requestId, int[] prompt, int prefixLen) {
        return prefillChunk(requestId, prompt, prefixLen, prompt.length);
    }

    /** Positions in {@code (from, to]} where a snapshot is worth taking: powers of two from 4096 and the token before the last. */
    private List<Integer> snapshotCuts(int[] prompt, int from, int to) {
        List<Integer> cuts = new ArrayList<>();
        if (prefixCache == null) {
            return cuts;
        }
        int limit = prompt.length - 1;              // the last token is always processed (its logits are sampled)
        long perToken = 1088L * Math.max(1, replicas[0].mlaLayerCount());
        for (int p = Math.max(from + 1, prefixMinTokens); p <= Math.min(to, limit); p++) {
            boolean wanted = p == limit || (p >= 4096 && Integer.bitCount(p) == 1);
            if (wanted && prefixCache.fits(p * perToken + (24L << 20)) && !prefixCache.has(prompt, p)) {
                cuts.add(p);
            }
            if (p >= 4096 && Integer.bitCount(p) != 1 && p < limit) {
                p = Math.min(Integer.highestOneBit(p) << 1, limit) - 1;     // skip ahead to the next power of two
            }
        }
        return cuts;
    }

    private record ChunkResult(Tensor logits, GlmModel.Snapshot[] snaps) { }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        GlmModel.Session[] s = session(requestId);
        if (s[0].length() != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + s[0].length() + ", not " + from);
        }
        boolean last = to == prompt.length;
        List<Integer> cuts = snapshotCuts(prompt, from, to);
        List<Integer> ends = new ArrayList<>(cuts);
        if (ends.isEmpty() || ends.get(ends.size() - 1) != to) {
            ends.add(to);
        }
        List<ChunkResult> results = gatherAllRanks(rank -> {
            GlmModel.Snapshot[] mine = new GlmModel.Snapshot[cuts.size()];
            try (var guard = Tensor.noGradGuard()) {
                GlmModel m = replicas[rank];
                Tensor out = null;
                int segStart = from;
                for (int end : ends) {
                    final int start = segStart;
                    int[] ids = Arrays.copyOfRange(prompt, start, end);
                    boolean finalSegment = end == to;
                    Tensor r = Gx.layerScoped(() -> {
                        Tensor hidden = m.forward(ids, s[rank], null);
                        if (m.hasMtp()) {
                            // slot q of the MTP layer pairs the hidden row of position q with the token at q + 1
                            int rows = end == prompt.length ? ids.length - 1 : ids.length;
                            if (rows > 0) {
                                m.mtpAbsorb(s[rank], Arrays.copyOfRange(prompt, start + 1, start + 1 + rows), hidden);
                            }
                            m.setLastHidden(s[rank], hidden);
                        }
                        // only the last row of the last chunk is sampled; other chunks return a placeholder
                        Tensor row = Gx.narrow(hidden, 0, hidden.size(0) - 1, 1);
                        return last && finalSegment ? m.logits(row) : Gx.zeros(smile.deep.tensor.ScalarType.Float, 1);
                    });
                    int k = cuts.indexOf(end);
                    if (k >= 0) {
                        mine[k] = m.snapshot(s[rank]);
                    }
                    if (finalSegment) {
                        out = r;
                    } else {
                        r.close();
                    }
                    segStart = end;
                }
                return new ChunkResult(out, mine);
            } catch (RuntimeException | Error e) {
                for (GlmModel.Snapshot snap : mine) {
                    if (snap != null) {
                        snap.close();
                    }
                }
                throw e;
            }
        });
        for (int r = 1; r < results.size(); r++) {
            results.get(r).logits().close();
        }
        for (int k = 0; k < cuts.size(); k++) {
            GlmModel.Snapshot[] ranks = new GlmModel.Snapshot[workers.length];
            for (int r = 0; r < ranks.length; r++) {
                ranks[r] = results.get(r).snaps()[k];
            }
            prefixCache.put(new GlmPrefixCache.Entry(Arrays.copyOf(prompt, cuts.get(k)), ranks));
        }
        Tensor logits = results.get(0).logits();
        if (!last) {
            logits.close();
            return null;
        }
        return logits;
    }

    /**
     * Whether the MTP layer is loaded (speculation available).
     *
     * @return {@code true} with MTP.
     */
    public boolean hasMtp() {
        return mtpOn();
    }

    /** A round may verify more rows than a request has tokens left: the engine drops the extra, and the window shape stays fixed. */
    @Override
    public boolean speculationMayOvershoot() {
        return true;
    }

    /** Plain steps keep the MTP caches in sync ({@link #syncMtpBeforePlain}), so speculation can switch per tick. */
    @Override
    public boolean speculationSwitchable() {
        return mtpOn();
    }

    private long plainSteps;
    private long plainSyncNs;
    private long plainAfterNs;
    private long plainStepNs;
    private long plainRows;

    private void notePlain(long syncNs, long afterNs, long stepNs, int rows) {
        plainSteps++;
        plainSyncNs += syncNs;
        plainAfterNs += afterNs;
        plainStepNs += stepNs;
        plainRows += rows;
        if (plainSteps % 200 == 0) {
            logger.info("GLM plain decode: {} steps, avg {} rows, {} ms per step incl. {} ms MTP catch-up before and {} ms bookkeeping after",
                    plainSteps, String.format("%.1f", (double) plainRows / plainSteps),
                    String.format("%.1f", (plainStepNs + plainSyncNs) / 1e6 / plainSteps),
                    String.format("%.2f", plainSyncNs / 1e6 / plainSteps), String.format("%.2f", plainAfterNs / 1e6 / plainSteps));
            plainSteps = 0;
            plainSyncNs = 0;
            plainAfterNs = 0;
            plainStepNs = 0;
            plainRows = 0;
        }
    }

    private boolean mtpOn() {
        return replicas.length > 0 && replicas[0].hasMtp();
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts,
                                 double temperature, double topp) {
        double[] t = new double[requestIds.length];
        double[] p = new double[requestIds.length];
        Arrays.fill(t, temperature);
        Arrays.fill(p, topp);
        return speculateStep(requestIds, lastTokens, positions, numDrafts, t, p);
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts,
                                 double[] temperatures, double[] topps) {
        if (!mtpOn()) {
            throw new UnsupportedOperationException("the GLM checkpoint has no MTP layer loaded");
        }
        if (windowGraphEligible(Math.min(requestIds.length, MAX_WINDOW_BATCH), positions, numDrafts)) {
            // groups of at most MAX_WINDOW_BATCH requests, one graph replay each
            int[][] all = new int[requestIds.length][];
            boolean ok = true;
            for (int from = 0; from < requestIds.length && ok; from += MAX_WINDOW_BATCH) {
                int to = Math.min(requestIds.length, from + MAX_WINDOW_BATCH);
                int[][] part = speculateWindow(Arrays.copyOfRange(requestIds, from, to), Arrays.copyOfRange(lastTokens, from, to),
                        Arrays.copyOfRange(positions, from, to), numDrafts, Arrays.copyOfRange(temperatures, from, to),
                        Arrays.copyOfRange(topps, from, to));
                if (part == null) {
                    ok = false;
                } else {
                    System.arraycopy(part, 0, all, from, part.length);
                }
            }
            if (ok) {
                return all;
            }
        }
        int[][] out = new int[requestIds.length][];
        for (int i = 0; i < out.length; i++) {
            out[i] = speculateOne(requestIds[i], lastTokens[i], positions[i], numDrafts, temperatures[i], topps[i]);
        }
        return out;
    }

    private boolean windowGraphEligible(int n, int[] positions, int drafts) {
        if (!WINDOW_GRAPHS || windowGraphsDisabled || !onCuda || n > MAX_WINDOW_BATCH || !smile.torch.Native.dsGraphAvailable()
                || !smile.torch.Native.windowAvailable() || !smile.torch.Native.kdaAvailable()
                || !smile.torch.Native.mlaDecodeAvailable() || drafts > 6) {
            return false;
        }
        for (int p : positions) {
            if (p + drafts + 1 > args.maxSeqLen()) {
                return false;
            }
        }
        return true;
    }

    private boolean prepareMtpGraph(int rank, int nb, java.util.concurrent.CyclicBarrier barrier) {
        return prepareMtpGraph(rank, nb, barrier, true);
    }

    /** Graph key of an MTP bucket: the catch-up variant without the head is stored under {@code nb + 1000}. */
    private static int mtpKey(int nb, boolean head) {
        return head ? nb : nb + 1000;
    }

    private boolean prepareMtpGraph(int rank, int nb, java.util.concurrent.CyclicBarrier barrier, boolean head) {
        try {
            if (mtpGraphs[rank].containsKey(mtpKey(nb, head))) {
                return true;
            }
            long t0 = System.nanoTime();
            long maxPools = ((args.maxSeqLen() + 1L) / 4 + 511) / 512 * 512;
            mtpGraphs[rank].put(mtpKey(nb, head), GlmMtpGraph.capture(replicas[rank], nb, maxPools, args.dim(), Gx.device().index(), barrier, head));
            if (rank == 0) {
                logger.info("Captured GLM MTP graph for {} request(s) in {} ms", nb, (System.nanoTime() - t0) / 1_000_000L);
            }
            return true;
        } catch (Throwable t) {
            logger.warn("GLM MTP graph capture failed on rank {}: {}", rank, t.toString());
            if (System.getenv("SMILE_GLM_DEBUG") != null) {
                t.printStackTrace();
            }
            barrier.reset();
            return false;
        }
    }

    private boolean prepareWindowGraph(int rank, int nb, int rows, java.util.concurrent.CyclicBarrier barrier) {
        int key = nb * 16 + rows;
        try {
            if (windowGraphs[rank].containsKey(key)) {
                return true;
            }
            long t0 = System.nanoTime();
            long maxPools = ((args.maxSeqLen() + 1L) / 4 + 511) / 512 * 512;
            windowGraphs[rank].put(key, GlmWindowGraph.capture(replicas[rank], nb, rows, maxPools, Gx.device().index(), barrier));
            if (rank == 0) {
                logger.info("Captured GLM verify-window graph for {} request(s) x {} rows in {} ms", nb, rows,
                        (System.nanoTime() - t0) / 1_000_000L);
            }
            return true;
        } catch (Throwable t) {
            logger.warn("GLM verify-window graph capture failed on rank {}: {}", rank, t.toString());
            if (System.getenv("SMILE_GLM_DEBUG") != null) {
                t.printStackTrace();
            }
            barrier.reset();
            return false;
        }
    }

    /**
     * The speculative round of a group of requests with the verify window in a CUDA graph: the MTP layer drafts for every
     * request (eager), one graph replay scores all windows, the accepted rows are kept by the commit kernels (no model
     * rerun on a partial accept), and the kept rows are absorbed into the MTP caches. Returns {@code null} if the graph
     * cannot be used (capture failed).
     */
    private int[][] speculateWindow(int[] requestIds, int[] lastTokens, int[] positions, int n, double[] temperatures,
                                    double[] topps) {
        long t0 = System.nanoTime();
        int b = requestIds.length;
        int rows = Math.max(1, n) + 1;
        int drafts = rows - 1;
        GlmModel.Session[][] mine = new GlmModel.Session[workers.length][b];
        for (int i = 0; i < b; i++) {
            GlmModel.Session[] s = session(requestIds[i]);
            if (s[0].length() != positions[i]) {
                throw new IllegalStateException("request " + requestIds[i] + " is at position " + s[0].length() + ", not " + positions[i]);
            }
            for (int r = 0; r < workers.length; r++) {
                mine[r][i] = s[r];
            }
        }
        long tp = System.nanoTime();
        int[][] tokens = new int[b][rows];
        final int mnb = b <= 1 ? 1 : Integer.highestOneBit(b - 1) << 1;
        boolean mtpGraph = MTP_GRAPHS && !mtpGraphsDisabled;
        if (mtpGraph && !readyGraphs.contains("m" + mnb)) {
            java.util.concurrent.CyclicBarrier mb = new java.util.concurrent.CyclicBarrier(workers.length);
            for (boolean ok : gatherAllRanks(rank -> prepareMtpGraph(rank, mnb, mb))) {
                mtpGraph &= ok;
            }
            if (mtpGraph) {
                readyGraphs.add("m" + mnb);
            } else {
                mtpGraphsDisabled = true;
                logger.warn("GLM MTP graph capture failed on a rank; using the eager MTP passes");
            }
        }
        final boolean useMtpGraph = mtpGraph;
        List<int[][]> dr = gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return replicas[rank].mtpDraftBatch(mine[rank], lastTokens, drafts, useMtpGraph ? mtpGraphs[rank].get(mnb) : null);
            }
        });
        int[][] d = dr.get(0);
        for (int i = 0; i < b; i++) {
            if (d[i] == null) {
                throw new UnsupportedOperationException("the MTP state of request " + requestIds[i] + " does not follow its position");
            }
            tokens[i][0] = lastTokens[i];
            System.arraycopy(d[i], 0, tokens[i], 1, drafts);
        }
        specPhase.addAndGet(0, System.nanoTime() - tp);
        tp = System.nanoTime();
        int nb = b <= 1 ? 1 : Integer.highestOneBit(b - 1) << 1;
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(workers.length);
        boolean ready = true;
        if (!readyGraphs.contains("w" + nb + "x" + rows)) {
            for (boolean ok : gatherAllRanks(rank -> prepareWindowGraph(rank, nb, rows, barrier))) {
                ready &= ok;
            }
            if (ready) {
                readyGraphs.add("w" + nb + "x" + rows);
            }
        }
        if (!ready) {
            windowGraphsDisabled = true;
            logger.warn("GLM verify-window graph capture failed on a rank; using the eager verify");
            return null;
        }
        final int key = nb * 16 + rows;
        List<Tensor> lg = gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return windowGraphs[rank].get(key).replay(tokens, mine[rank]);
            }
        });
        for (int r = 1; r < lg.size(); r++) {
            lg.get(r).close();
        }
        specPhase.addAndGet(1, System.nanoTime() - tp);
        tp = System.nanoTime();
        int[][] emitted = new int[b][];
        int[] accept = new int[b];
        try (Tensor logits = lg.get(0)) {
            for (int i = 0; i < b; i++) {
                int[] sampled;
                try (Tensor slice = smile.torch.Aten.one("aten::narrow", logits, 0L, (long) i * rows, (long) rows)) {
                    sampled = Sampling.sampleTokenIds(slice, temperatures[i], topps[i]);
                }
                int k = 0;
                while (k < drafts && sampled[k] == tokens[i][k + 1]) {
                    k++;
                }
                accept[i] = k + 1;
                emitted[i] = Arrays.copyOf(sampled, k + 1);
            }
        }
        specPhase.addAndGet(2, System.nanoTime() - tp);
        tp = System.nanoTime();
        gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                windowGraphs[rank].get(key).commit(mine[rank], accept);
                replicas[rank].mtpFinishBatch(mine[rank], emitted, useMtpGraph ? mtpGraphs[rank].get(mnb) : null);
                for (int i = 0; i < b; i++) {
                    replicas[rank].endRound(mine[rank][i]);
                }
                return null;
            }
        });
        specPhase.addAndGet(3, System.nanoTime() - tp);
        specRounds.addAndGet(b);
        specDrafts.addAndGet((long) b * drafts);
        for (int i = 0; i < b; i++) {
            specAccepted.addAndGet(accept[i] - 1);
        }
        specNanos.addAndGet(System.nanoTime() - t0);
        return emitted;
    }

    private final java.util.concurrent.atomic.AtomicLong specRounds = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong specDrafts = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong specAccepted = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong specNanos = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLongArray specPhase = new java.util.concurrent.atomic.AtomicLongArray(4);

    /**
     * Speculation counters.
     *
     * @return {rounds, drafts proposed, drafts accepted, nanoseconds}.
     */
    public long[] speculationCounters() {
        return new long[]{specRounds.get(), specDrafts.get(), specAccepted.get(), specNanos.get()};
    }

    /**
     * Speculation statistics.
     *
     * @return rounds, drafts proposed, drafts accepted.
     */
    public String speculationStats() {
        long r = specRounds.get();
        double per = r == 0 ? 0 : 1e-6 / r;
        return String.format("%d rounds, %d drafts proposed, %d accepted (%.2f per round, %.1f%%), %.1f ms per round "
                        + "(draft %.1f, verify %.1f, sample %.1f, rollback+absorb %.1f)",
                r, specDrafts.get(), specAccepted.get(), r == 0 ? 0 : (double) specAccepted.get() / r,
                specDrafts.get() == 0 ? 0 : 100.0 * specAccepted.get() / specDrafts.get(),
                specNanos.get() * per, specPhase.get(0) * per, specPhase.get(1) * per, specPhase.get(2) * per, specPhase.get(3) * per)
                + "; window graph host: " + GlmWindowGraph.hostTimes();
    }

    /**
     * One draft + verify round of a request: the MTP layer drafts {@code n} greedy tokens, the main model scores
     * {@code [last, drafts]} in one pass, the longest prefix of drafts that equals what the main model samples is kept
     * together with the sample that follows it. A partly accepted window is rolled back and the kept rows replayed (the
     * KDA state cannot be rewound otherwise). Sampling from the target at every row keeps the output distribution exact
     * for the point-mass (greedy) draft.
     */
    private int[] speculateOne(int requestId, int last, int p, int n, double temperature, double topp) {
        long t0 = System.nanoTime();
        GlmModel.Session[] s = session(requestId);
        if (s[0].length() != p) {
            throw new IllegalStateException("request " + requestId + " is at position " + s[0].length() + ", not " + p);
        }
        n = Math.max(1, n);
        final int drafts = n;
        long tp = System.nanoTime();
        List<int[]> dr = gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return replicas[rank].mtpDraft(s[rank], last, drafts);
            }
        });
        int[] d = dr.get(0);
        if (d == null) {
            throw new UnsupportedOperationException("the MTP state of request " + requestId + " does not follow its position");
        }
        specPhase.addAndGet(0, System.nanoTime() - tp);
        tp = System.nanoTime();
        int[] ids = new int[n + 1];
        ids[0] = last;
        System.arraycopy(d, 0, ids, 1, n);
        List<Tensor> lg = gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                GlmModel m = replicas[rank];
                m.keepMark(s[rank], m.mark(s[rank]));
                return Gx.layerScoped(() -> {
                    Tensor hidden = m.forward(ids, s[rank], null);
                    m.keepPending(s[rank], hidden);
                    return m.logits(hidden);
                });
            }
        });
        for (int r = 1; r < lg.size(); r++) {
            lg.get(r).close();
        }
        specPhase.addAndGet(1, System.nanoTime() - tp);
        tp = System.nanoTime();
        int[] sampled;
        try (Tensor logits = lg.get(0)) {
            sampled = Sampling.sampleTokenIds(logits, temperature, topp);
        }
        int k = 0;
        while (k < n && sampled[k] == d[k]) {
            k++;
        }
        final int accepted = k;
        specPhase.addAndGet(2, System.nanoTime() - tp);
        tp = System.nanoTime();
        int[] emitted = Arrays.copyOf(sampled, k + 1);
        gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                GlmModel m = replicas[rank];
                GlmModel.Session ss = s[rank];
                if (accepted < drafts) {
                    m.rewind(ss, m.markOf(ss));
                    int[] replay = Arrays.copyOf(ids, accepted + 1);
                    Gx.layerScoped(() -> {
                        m.forward(replay, ss, null);
                        return Gx.zeros(smile.deep.tensor.ScalarType.Float, 1);
                    }).close();
                }
                m.mtpFinish(ss, emitted);
                m.endRound(ss);
                return null;
            }
        });
        specPhase.addAndGet(3, System.nanoTime() - tp);
        specRounds.incrementAndGet();
        specDrafts.addAndGet(n);
        specAccepted.addAndGet(k);
        specNanos.addAndGet(System.nanoTime() - t0);
        return emitted;
    }

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        int n = requestIds.length;
        GlmModel.Session[][] mine = new GlmModel.Session[workers.length][n];
        for (int i = 0; i < n; i++) {
            GlmModel.Session[] s = session(requestIds[i]);
            if (s[0].length() != positions[i]) {
                throw new IllegalStateException("request " + requestIds[i] + " is at position " + s[0].length()
                        + ", not " + positions[i]);
            }
            for (int r = 0; r < workers.length; r++) {
                mine[r][i] = s[r];
            }
        }
        final boolean mtp = mtpOn();
        long syncT0 = System.nanoTime();
        if (mtp) {
            syncMtpBeforePlain(mine, lastTokens);
        }
        long syncNs = System.nanoTime() - syncT0;
        long stepT0 = System.nanoTime();
        if (graphEligible(n, positions)) {
            int nb = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
            boolean ready = readyGraphs.contains("d" + nb);
            if (!ready) {
                ready = true;
                java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(workers.length);
                for (boolean ok : gatherAllRanks(rank -> prepareGraph(rank, nb, barrier))) {
                    ready &= ok;
                }
                if (ready) {
                    readyGraphs.add("d" + nb);
                }
            }
            if (ready) {
                return onAllRanks(rank -> {
                    try (var guard = Tensor.noGradGuard()) {
                        GlmDecodeGraph g = graphs[rank].get(nb);
                        Tensor logits = g.replay(lastTokens, mine[rank]);
                        long a0 = System.nanoTime();
                        if (mtp) {
                            replicas[rank].mtpAfterPlain(mine[rank], g.hidden());
                        }
                        if (rank == 0) {
                            notePlain(syncNs, System.nanoTime() - a0, System.nanoTime() - stepT0, n);
                        }
                        return logits;
                    }
                }, Tensor::close);
            }
            graphsDisabled = true;
            logger.warn("GLM decode CUDA graph capture failed on a rank; using eager decode");
        }
        return onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                GlmModel m = replicas[rank];
                return Gx.layerScoped(() -> {
                    Tensor hidden = m.forwardBatch(lastTokens, mine[rank]);
                    if (mtp) {
                        m.mtpAfterPlain(mine[rank], hidden);
                    }
                    return m.logits(hidden);
                });
            }
        }, Tensor::close);
    }

    /**
     * Keeps the MTP caches in sync with plain decoding: the requests whose MTP cache lags (after a prefill or an earlier
     * plain step) absorb the slot of their previous position now that the step's input token is known, so speculation
     * can start again at any tick.
     */
    private void syncMtpBeforePlain(GlmModel.Session[][] mine, int[] lastTokens) {
        int pending = 0;
        for (int i = 0; i < mine[0].length; i++) {
            if (replicas[0].mtpPending(mine[0][i])) {
                pending++;
            }
        }
        if (pending == 0) {
            return;
        }
        final int nb = pending <= 1 ? 1 : Integer.highestOneBit(pending - 1) << 1;
        boolean graph = MTP_GRAPHS && !mtpGraphsDisabled && onCuda && nb <= 32;
        if (graph && !readyGraphs.contains("c" + nb)) {
            java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(workers.length);
            for (boolean ok : gatherAllRanks(rank -> prepareMtpGraph(rank, nb, barrier, false))) {
                graph &= ok;
            }
            if (graph) {
                readyGraphs.add("c" + nb);
            } else {
                mtpGraphsDisabled = true;
                logger.warn("GLM MTP graph capture failed on a rank; using the eager MTP passes");
            }
        }
        final boolean useGraph = graph;
        gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                replicas[rank].mtpBeforePlain(mine[rank], lastTokens, useGraph ? mtpGraphs[rank].get(mtpKey(nb, false)) : null);
                return null;
            }
        });
    }

    private boolean graphEligible(int n, int[] positions) {
        if (!GRAPHS || graphsDisabled || !onCuda || n > MAX_GRAPH_BATCH || !smile.torch.Native.dsGraphAvailable()
                || !smile.torch.Native.kdaAvailable() || !smile.torch.Native.mlaDecodeAvailable()) {
            return false;
        }
        for (int p : positions) {
            if (p + 1 > args.maxSeqLen()) {
                return false;
            }
        }
        return true;
    }

    /** Captures the graph of a batch bucket on a rank (all ranks together: the captured step has collectives). */
    private boolean prepareGraph(int rank, int nb, java.util.concurrent.CyclicBarrier barrier) {
        try {
            if (graphs[rank].containsKey(nb)) {
                return true;
            }
            long t0 = System.nanoTime();
            long maxPools = ((args.maxSeqLen() + 1L) / 4 + 511) / 512 * 512;
            graphs[rank].put(nb, GlmDecodeGraph.capture(replicas[rank], nb, maxPools, Gx.device().index(), barrier));
            if (rank == 0) {
                logger.info("Captured GLM decode graph for batch {} in {} ms", nb, (System.nanoTime() - t0) / 1_000_000L);
            }
            return true;
        } catch (Throwable t) {
            logger.warn("GLM decode graph capture failed on rank {}: {}", rank, t.toString());
            if (System.getenv("SMILE_GLM_DEBUG") != null) {
                t.printStackTrace();
            }
            barrier.reset();        // do not leave the other ranks waiting for this one
            return false;
        }
    }

    private <T> List<T> gatherAllRanks(java.util.function.IntFunction<T> task) {
        List<Future<T>> futures = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            final int rank = r;
            Callable<T> c = () -> task.apply(rank);
            futures.add(workers[r].submit(c));
        }
        await(futures);
        List<T> out = new ArrayList<>();
        try {
            for (Future<T> f : futures) {
                out.add(f.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    @Override
    public void finish(int requestId, int[] sequenceTokens) {
        evict(requestId);
    }

    @Override
    public void evict(int requestId) {
        GlmPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit != null) {
            prefixCache.unpin(hit);
        }
        GlmModel.Session[] s = sessions.remove(requestId);
        if (s != null) {
            release(s);
        }
    }

    /** Frees the sessions on their rank threads (device memory is released by the owning thread). */
    private void release(GlmModel.Session[] s) {
        List<Future<?>> frees = new ArrayList<>();
        for (int r = 0; r < s.length; r++) {
            final GlmModel.Session mine = s[r];
            if (mine != null) {
                frees.add(workers[r].submit(() -> {
                    mine.close();
                    return null;
                }));
            }
        }
        await(frees);
    }

    /**
     * Tokens processed so far for a request (the position of its next token).
     *
     * @param requestId the request id from {@link #bind}.
     * @return the position.
     */
    public int positionOf(int requestId) {
        return (int) session(requestId)[0].length();
    }

    /**
     * Number of requests the model can hold at once.
     *
     * @return the number of slots.
     */
    public int maxSessions() {
        return maxSessions;
    }

    @Override
    public void close() throws IOException {
        // captured graphs hold NCCL kernels: destroy them on their rank threads before the communicators go
        List<Future<?>> teardown = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            final int rank = r;
            teardown.add(workers[r].submit(() -> {
                for (GlmDecodeGraph g : graphs[rank].values()) {
                    g.close();
                }
                graphs[rank].clear();
                for (GlmWindowGraph g : windowGraphs[rank].values()) {
                    g.close();
                }
                windowGraphs[rank].clear();
                for (GlmMtpGraph g : mtpGraphs[rank].values()) {
                    g.close();
                }
                mtpGraphs[rank].clear();
                return null;
            }));
        }
        try {
            await(teardown);
        } catch (RuntimeException e) {
            logger.warn("GLM graph teardown failed: {}", e.toString());
        }
        if (prefixCache != null) {
            try {
                prefixCache.clear();
            } catch (RuntimeException e) {
                logger.warn("GLM prefix cache teardown failed: {}", e.toString());
            }
        }
        for (ExecutorService w : workers) {
            w.shutdownNow();
        }
        for (GlmModel m : replicas) {
            if (m != null) {
                m.close();
            }
        }
        if (group != null) {
            group.close();
        }
    }
}
