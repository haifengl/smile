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
package smile.llm.model.deepseek;

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
import smile.llm.template.DeepSeekChatTemplate;
import smile.torch.Native;

/**
 * DeepSeek-V4.1 behind the continuous-batching engine: a {@link LanguageModel} and {@link ModelExecutor} over
 * {@link DeepSeekModel}.
 *
 * <p>With several devices every device holds a replica of the dense layers and {@code 1/n} of each layer's routed
 * experts (expert parallelism, partial outputs summed with NCCL); each device is driven by its own thread because the
 * collectives need all ranks to be inside the same layer at once. One request's state is a {@link DeepSeekModel.Session}
 * per rank.
 *
 * <p>Current limits (the model code was written for validation first): a decode step over {@code B} requests is one batched
 * pass (the sequence-independent math runs once, attention state per row); a prompt is prefilled in one pass (the engine's chunks are
 * collected and the work happens with the last one); no prefix reuse, CUDA graphs, speculation or images.
 *
 * @author Haifeng Li
 */
public final class DeepSeek implements LanguageModel, ModelExecutor, AutoCloseable {
    private final String name;
    private final DeepSeekModelArgs args;
    private final DeepSeekTokenizer tokenizer;
    private final DeepSeekChatTemplate template = new DeepSeekChatTemplate();
    private final DeepSeekCheckpoint rowsSource;
    private final TensorParallelGroup group;
    private final ExecutorService[] workers;
    private final DeepSeekModel[] replicas;
    private final Map<Integer, DeepSeekModel.Session[]> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DeepSeek.class);
    /** Decode CUDA graphs ({@code SMILE_DS_GRAPH=0} disables them): the step is host-bound without them. */
    private static final boolean GRAPHS = !"0".equals(System.getenv("SMILE_DS_GRAPH"));
    /** Largest batch a graph is captured for (rounded up to a power of two). */
    private static final int MAX_GRAPH_BATCH = Integer.getInteger("smile.ds.graph.maxbatch",
            System.getenv("SMILE_DS_MAX_GRAPH_BATCH") != null ? Integer.parseInt(System.getenv("SMILE_DS_MAX_GRAPH_BATCH")) : 128);
    /** Captured graphs by rank, keyed by (batch bucket, position capacity); only touched on the rank's own thread. */
    private final Map<Long, DeepSeekDecodeGraph>[] graphs;
    /** Per rank: the dummy sequence that padding rows of a captured graph run on. */
    private final DeepSeekModel.Session[] pads;
    /** Released sessions kept for the next request (each holds the caches of every layer on every rank). */
    private final java.util.concurrent.ConcurrentLinkedQueue<DeepSeekModel.Session[]> sessionPool = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_POOLED_SESSIONS = 64;
    private volatile boolean graphsDisabled;
    private final int minGraphPos;
    private final boolean onCuda;

    private DeepSeek(String name, DeepSeekModelArgs args, DeepSeekTokenizer tokenizer, Path dir, byte[] devices,
                     boolean marlin, java.util.function.IntFunction<DeepSeekEngram.Rows> engramRows) throws IOException {
        this.name = name;
        this.args = args;
        this.tokenizer = tokenizer;
        this.rowsSource = new DeepSeekCheckpoint(dir);
        int ranks = devices == null ? 1 : devices.length;
        this.group = ranks > 1 ? new TensorParallelGroup(ParallelConfig.tensorParallel(devices)) : null;
        this.workers = new ExecutorService[ranks];
        this.replicas = new DeepSeekModel[ranks];
        @SuppressWarnings("unchecked")
        Map<Long, DeepSeekDecodeGraph>[] gm = new Map[ranks];
        for (int r = 0; r < ranks; r++) {
            gm[r] = new java.util.HashMap<>();
        }
        this.graphs = gm;
        this.pads = new DeepSeekModel.Session[ranks];
        int maxRatio = 1;
        for (int l = 0; l < args.numLayers(); l++) {
            maxRatio = Math.max(maxRatio, args.compressRatio(l));
        }
        this.minGraphPos = Math.max(1, maxRatio - 1);
        this.onCuda = devices != null;
        for (int r = 0; r < ranks; r++) {
            // a rank that fails must not leave the others blocked in a collective: daemon threads
            workers[r] = Executors.newSingleThreadExecutor(runnable -> {
                Thread t = new Thread(runnable, "deepseek-rank");
                t.setDaemon(true);
                return t;
            });
        }
        Dsv.marlin = marlin;
        List<Future<?>> loading = new ArrayList<>();
        for (int r = 0; r < ranks; r++) {
            final int rank = r;
            loading.add(workers[r].submit(() -> {
                Dsv.setThreadDevice(devices == null ? Device.CPU() : Device.CUDA(devices[rank]));
                Dsv.setEp(group == null ? null : new Dsv.Ep(group, rank, ranks));
                replicas[rank] = new DeepSeekModel(args, dir, engramRows != null ? engramRows : l -> new DeepSeekEngram.FileRows(rowsSource, l));
                return null;
            }));
        }
        await(loading);
        startEngramPrefetch();
    }

    /**
     * Pulls the engram tables into the page cache in the background ({@code SMILE_DS_ENGRAM_PREFETCH=0} disables):
     * decode reads a few random rows of them per token, and each cold page is a network round trip.
     */
    private void startEngramPrefetch() {
        if (rowsSource == null || args.engramLayers().length == 0
                || "0".equals(System.getenv("SMILE_DS_ENGRAM_PREFETCH"))) {
            return;
        }
        for (int layer : args.engramLayers()) {
            Thread t = new Thread(() -> {
                long t0 = System.nanoTime();
                long bytes = 0;
                for (String part : new String[]{"scale", "weight"}) {
                    bytes += rowsSource.prefetch("layers." + layer + ".engram.embed." + part);
                }
                logger.info("Engram table of layer {} read into the page cache: {} GB in {} s", layer,
                        bytes >> 30, (System.nanoTime() - t0) / 1_000_000_000L);
                // then map every page into the process: a random row read is then a memory access, not a page fault
                // (faults of 8 rank threads in the same file serialize: 6 ms per lookup instead of 0.2 ms)
                t0 = System.nanoTime();
                long mapped = 0;
                for (String part : new String[]{"scale", "weight"}) {
                    mapped += rowsSource.populate("layers." + layer + ".engram.embed." + part);
                }
                logger.info("Engram table of layer {} mapped: {} GB in {} s", layer, mapped >> 30,
                        (System.nanoTime() - t0) / 1_000_000_000L);
            }, "deepseek-engram-prefetch");
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Loads a checkpoint.
     *
     * @param checkpointDir directory with {@code config.json}, {@code tokenizer.json} and the safetensors shards.
     * @param layers        number of layers to load ({@code <= 0}: all); a truncated model is for testing only.
     * @param maxSeqLen     maximum context length ({@code <= 0}: the checkpoint's).
     * @param devices       CUDA device ids, one rank each (routed experts are split over them), or {@code null} for CPU.
     * @return the model.
     * @throws IOException if the checkpoint is incomplete.
     */
    public static DeepSeek build(String checkpointDir, int layers, int maxSeqLen, byte[] devices) throws IOException {
        return build(checkpointDir, layers, maxSeqLen, devices, null);
    }

    /** As {@link #build(String, int, int, byte[])} with the engram rows supplied by the caller (tests). */
    static DeepSeek build(String checkpointDir, int layers, int maxSeqLen, byte[] devices,
                          java.util.function.IntFunction<DeepSeekEngram.Rows> engramRows) throws IOException {
        Path dir = Path.of(checkpointDir);
        DeepSeekModelArgs args = DeepSeekModelArgs.load(dir.resolve("config.json").toFile());
        if (layers > 0) {
            args = args.slice(layers);
        }
        if (maxSeqLen > 0) {
            args = args.withMaxSeqLen(maxSeqLen);
        }
        return new DeepSeek(dir.getFileName().toString(), args, DeepSeekTokenizer.of(checkpointDir), dir, devices,
                devices != null, engramRows);
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

    /** Runs {@code task(rank)} on every rank thread; returns rank 0's result, the others are discarded. */
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

    private Tensor forward(int requestId, int[] ids, int startPos) {
        DeepSeekModel.Session[] s = sessions.get(requestId);
        if (s == null) {
            throw new IllegalArgumentException("unknown request " + requestId);
        }
        return onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                DeepSeekModel m = replicas[rank];
                m.use(s[rank]);
                return Dsv.scoped(() -> {
                    Tensor logits = m.forward(ids, startPos, null);
                    if (startPos == 0) {
                        Tensor tap = m.takeTap();
                        if (tap != null) {
                            m.seedDraft(s[rank], tap);
                        }
                    }
                    return logits;
                });
            }
        }, Tensor::close);
    }

    // ------------------------------------------------------------------ LanguageModel

    @Override
    public String family() {
        return "deepseek";
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
                throw new IllegalArgumentException("DeepSeek-V4.1 image input is not supported yet");
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
        DeepSeekModel.Session[] s = new DeepSeekModel.Session[workers.length];
        DeepSeekModel.Session[] reuse = sessionPool.poll();
        List<Future<?>> allocation = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            final int rank = r;
            allocation.add(workers[r].submit(() -> {
                if (reuse != null) {
                    // a released sequence's caches: resetting them costs a few launches, new ones hundreds of allocations
                    reuse[rank].reset();
                    s[rank] = reuse[rank];
                } else {
                    s[rank] = replicas[rank].newSession();
                }
                return null;
            }));
        }
        await(allocation);
        int id = nextId.getAndIncrement();
        sessions.put(id, s);
        return id;
    }

    @Override
    public int prefixLen(int requestId) {
        return 0;
    }

    @Override
    public Tensor prefill(int requestId, int[] prompt, int prefixLen) {
        if (prefixLen != 0) {
            throw new UnsupportedOperationException("prefix reuse is not supported");
        }
        return forward(requestId, prompt, 0);
    }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        // the model prefills a whole prompt in one pass: do the work with the last chunk
        return to < prompt.length ? null : prefill(requestId, prompt, 0);
    }

    /** Whether the model has a DSpark draft head (and it is not turned off with {@code SMILE_DS_DRAFT=0}). */
    public boolean hasDraft() {
        return replicas[0] != null && replicas[0].hasDraft();
    }

    /** Tokens the DSpark head drafts per round (0 without a draft head). */
    public int draftBlock() {
        return hasDraft() ? replicas[0].draft().block() : 0;
    }

    /**
     * Drafts the block of tokens that follows {@code lastTokens[i]} for requests whose prompt was prefilled (greedy).
     * Experimental baseline: the draft state is only seeded by prefill.
     */
    DeepSeekDraft.Result[] draft(int[] requestIds, int[] lastTokens) {
        int n = requestIds.length;
        if (GRAPHS && !draftGraphsDisabled && onCuda && Native.dsGraphAvailable() && n <= MAX_GRAPH_BATCH) {
            DeepSeekDraft.Result[] r = draftGraphed(requestIds, lastTokens);
            if (r != null) {
                return r;
            }
        }
        DeepSeekModel.Session[][] mine = new DeepSeekModel.Session[workers.length][n];
        for (int i = 0; i < n; i++) {
            DeepSeekModel.Session[] s = sessions.get(requestIds[i]);
            for (int r = 0; r < workers.length; r++) {
                mine[r][i] = s[r];
            }
        }
        return onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return Dsv.scoped0(() -> replicas[rank].draftBatch(lastTokens, mine[rank]));
            }
        }, r -> { });
    }

    /**
     * One eager decode step of a single request that also advances its draft state (the hidden state of the processed
     * position becomes the draft's latest). Returns the logits {@code [1, vocab]}.
     */
    private Tensor decodeTapped(int requestId, int token, int pos) {
        DeepSeekModel.Session[] s = sessions.get(requestId);
        return onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return Dsv.scoped(() -> {
                    Tensor logits = replicas[rank].forwardBatch(new int[]{token}, new int[]{pos}, new DeepSeekModel.Session[]{s[rank]});
                    Tensor tap = replicas[rank].takeTap();
                    if (tap != null) {
                        replicas[rank].advanceDraft(s[rank], tap, pos);
                    }
                    return logits;
                });
            }
        }, Tensor::close);
    }

    /**
     * DSpark baseline: drafts a block and verifies it one token after the other with the ordinary eager step, so no state
     * needs rolling back. It returns exactly what plain greedy decoding would and is slower than it; it exists to check the
     * draft protocol (acceptance, draft-state upkeep) before the batched verify. Greedy only.
     */
    int[][] speculateSequential(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts) {
        int[][] out = new int[requestIds.length][];
        for (int i = 0; i < out.length; i++) {
            int id = requestIds[i];
            DeepSeekDraft.Result d = draft(new int[]{id}, new int[]{lastTokens[i]})[0];
            int k = Math.min(Math.max(numDrafts, 0), d.tokens().length);
            List<Integer> accepted = new ArrayList<>();
            int cur = lastTokens[i];
            int pos = positions[i];
            for (int j = 0; j <= k; j++) {
                float[] lg = Dsv.host(decodeTapped(id, cur, pos)).floatArray();
                int best = 0;
                for (int v = 1; v < lg.length; v++) {
                    if (lg[v] > lg[best]) {
                        best = v;
                    }
                }
                accepted.add(best);
                if (j < k && best == d.tokens()[j]) {
                    cur = best;
                    pos++;
                } else {
                    break;
                }
            }
            out[i] = accepted.stream().mapToInt(Integer::intValue).toArray();
            lastDrafted = d;
            lastAccepted = out[i].length - 1;
        }
        return out;
    }

    /**
     * One DSpark round for several sequences: drafts a block per sequence and verifies the blocks of all of them in one
     * CUDA-graph pass of the main model, then keeps each sequence's accepted prefix plus one token of the model's own
     * (greedy argmax, or sampled as in {@link DeepSeekSampler}). Sequences whose draft state is out of step (they took a
     * plain decode step) and rounds that cannot use a graph fall back to plain steps.
     */
    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts,
                                 double[] temperatures, double[] topps) {
        if (!hasDraft()) {
            throw new UnsupportedOperationException("no DSpark draft head");
        }
        int total = requestIds.length;
        int[][] out = new int[total][];
        // sequences whose draft is in step with their position speculate; the others take one plain step
        List<Integer> spec = new ArrayList<>();
        List<Integer> plain = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            DeepSeekModel.Session s0 = sessions.get(requestIds[i])[0];
            (s0.draft != null && s0.lastTap != null && s0.draftPos == positions[i] - 1 ? spec : plain).add(i);
        }
        if (!plain.isEmpty()) {
            plainRound(requestIds, lastTokens, positions, temperatures, topps, plain, out);
        }
        if (!spec.isEmpty()) {
            int[] ix = spec.stream().mapToInt(Integer::intValue).toArray();
            int[][] r = verifyRound(pick(requestIds, ix), pick(lastTokens, ix), pick(positions, ix), numDrafts,
                    pick(temperatures, ix), pick(topps, ix));
            for (int j = 0; j < ix.length; j++) {
                out[ix[j]] = r[j];
            }
        }
        return out;
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts,
                                 double temperature, double topp) {
        double[] t = new double[requestIds.length];
        double[] p = new double[requestIds.length];
        java.util.Arrays.fill(t, temperature);
        java.util.Arrays.fill(p, topp);
        return speculateStep(requestIds, lastTokens, positions, numDrafts, t, p);
    }

    private static int[] pick(int[] a, int[] ix) {
        int[] r = new int[ix.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = a[ix[i]];
        }
        return r;
    }

    private static double[] pick(double[] a, int[] ix) {
        double[] r = new double[ix.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = a[ix[i]];
        }
        return r;
    }

    /** One ordinary decode step for the sequences {@code which} (their draft state is not advanced: they stay plain). */
    private void plainRound(int[] ids, int[] last, int[] pos, double[] temps, double[] topps, List<Integer> which, int[][] out) {
        int[] ix = which.stream().mapToInt(Integer::intValue).toArray();
        try (Tensor logits = decodeStep(pick(ids, ix), pick(last, ix), pick(pos, ix))) {
            final double[] t = pick(temps, ix);
            final double[] p = pick(topps, ix);
            final int[] none = filled(ix.length, -1);
            Future<long[][]> f = workers[0].submit(() -> {
                try (var guard = Tensor.noGradGuard()) {
                    return Dsv.scoped0(() -> DeepSeekSampler.verify(logits, none, t, p));
                }
            });
            long[][] r;
            try {
                r = f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            for (int j = 0; j < ix.length; j++) {
                out[ix[j]] = new int[]{(int) r[1][j]};
            }
        }
    }

    private static int[] filled(int n, int v) {
        int[] a = new int[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    /** Row counts the verify graphs are captured for (a step uses the smallest that holds its rows). */
    private static final int[] ROW_BUCKETS = {8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384};

    /**
     * Drafts to verify per sequence. Every draft position has a cumulative acceptance probability, the product of
     * {@code sigmoid(confidence)} up to it (the confidence head predicts it well: the hit rate of a position given its
     * predecessors were accepted follows the sigmoid). A step runs on a graph whose row count is a bucket, so for each bucket
     * the rows left after one per sequence go to the most probable positions, and the bucket with the most expected tokens
     * per millisecond under a cost of {@link #SPEC_FIXED_MS} per round plus {@link #SPEC_ROW_MS} per row wins.
     */
    static int[] chooseDrafts(DeepSeekDraft.Result[] drafts, int kMax) {
        int n = drafts.length;
        int[] k = new int[n];
        if (!SPEC_DYNAMIC) {
            java.util.Arrays.fill(k, kMax);
            return k;
        }
        List<double[]> cand = new ArrayList<>();                 // {cumulative probability, sequence, position}
        for (int i = 0; i < n; i++) {
            double cum = 1.0;
            for (int j = 0; j < kMax; j++) {
                cum *= 1.0 / (1.0 + Math.exp(-drafts[i].confidence()[j]));
                cand.add(new double[]{cum, i, j});
            }
        }
        cand.sort((x, y) -> x[0] != y[0] ? Double.compare(y[0], x[0]) : Double.compare(x[2], y[2]));
        double[] prefix = new double[cand.size() + 1];
        for (int m = 0; m < cand.size(); m++) {
            prefix[m + 1] = prefix[m] + cand.get(m)[0];
        }
        int bestRows = n;
        int bestM = 0;
        double bestRate = -1;
        for (int bucket : ROW_BUCKETS) {
            if (bucket < n) {
                continue;
            }
            int m = Math.min(bucket - n, cand.size());
            double rate = (n + prefix[m]) / (SPEC_FIXED_MS + SPEC_ROW_MS * bucket);
            if (rate > bestRate) {
                bestRate = rate;
                bestM = m;
            }
            if (bucket - n >= cand.size()) {
                break;
            }
        }
        for (int m = 0; m < bestM; m++) {
            k[(int) cand.get(m)[1]]++;
        }
        return k;
    }

    private static final boolean SPEC_DYNAMIC = !"0".equals(System.getenv("SMILE_DS_SPEC_DYNAMIC"));
    private static final double SPEC_FIXED_MS = Double.parseDouble(System.getProperty("smile.ds.spec.fixed.ms", "34"));
    private static final double SPEC_ROW_MS = Double.parseDouble(System.getProperty("smile.ds.spec.row.ms", "0.3"));

    private int[][] verifyRound(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts, double[] temperatures,
                                double[] topps) {
        boolean greedy = true;
        for (double t : temperatures) {
            greedy &= t == 0.0;
        }
        int n = requestIds.length;
        int kMax = Math.min(Math.max(numDrafts, 1), replicas[0].draft().block());
        int maxPos = 0;
        boolean eligible = GRAPHS && !verifyGraphsDisabled && onCuda && Native.dsGraphAvailable() && Native.dsStateAvailable()
                && n * (kMax + 1) <= ROW_BUCKETS[ROW_BUCKETS.length - 1];
        for (int p : positions) {
            maxPos = Math.max(maxPos, p);
            eligible &= p >= minGraphPos && p + kMax + 1 < args.maxSeqLen();
        }
        if (!eligible) {
            if (!greedy) {
                return plainFallback(requestIds, lastTokens, positions, temperatures, topps);
            }
            return speculateSequential(requestIds, lastTokens, positions, numDrafts);
        }
        long t0 = System.nanoTime();
        DeepSeekDraft.Result[] drafts = draft(requestIds, lastTokens);
        specNs[0] += System.nanoTime() - t0;
        int[] k = chooseDrafts(drafts, kMax);
        int[] base = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            base[i] = total;
            total += k[i] + 1;
        }
        int nb = ROW_BUCKETS[ROW_BUCKETS.length - 1];
        for (int bucket : ROW_BUCKETS) {
            if (bucket >= total) {
                nb = bucket;
                break;
            }
        }
        int lenCap = 512;
        while (lenCap < maxPos + kMax + 1) {
            lenCap <<= 2;
        }
        final long key = (1L << 56) | ((long) nb << 32) | lenCap;
        final int flen = lenCap;
        final int fnb = nb;
        boolean ready = true;
        for (boolean ok : gatherAllRanks(rank -> prepareGraph(rank, key, fnb, flen, true))) {
            ready &= ok;
        }
        if (!ready) {
            verifyGraphsDisabled = true;
            logger.warn("DeepSeek verify graph capture failed on a rank; speculating one token at a time");
            return greedy ? speculateSequential(requestIds, lastTokens, positions, numDrafts) : plainFallback(requestIds,
                    lastTokens, positions, temperatures, topps);
        }
        int[] toks = new int[nb];
        int[] poss = new int[nb];
        int[] jRow = new int[nb];
        int[] seqOf = new int[nb];
        java.util.Arrays.fill(poss, 1);
        java.util.Arrays.fill(seqOf, -1);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= k[i]; j++) {
                int r = base[i] + j;
                toks[r] = j == 0 ? lastTokens[i] : drafts[i].tokens()[j - 1];
                poss[r] = positions[i] + j;
                jRow[r] = j;
                seqOf[r] = i;
            }
        }
        DeepSeekModel.Session[][] mine = new DeepSeekModel.Session[workers.length][n];
        for (int i = 0; i < n; i++) {
            DeepSeekModel.Session[] s = sessions.get(requestIds[i]);
            for (int r = 0; r < workers.length; r++) {
                mine[r][i] = s[r];
            }
        }
        // per row: the drafted token to test (-1 for the row after a sequence's last draft), and its sampling parameters
        int[] draftOf = new int[total];
        double[] tRow = new double[total];
        double[] pRow = new double[total];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= k[i]; j++) {
                draftOf[base[i] + j] = j < k[i] ? drafts[i].tokens()[j] : -1;
                tRow[base[i] + j] = temperatures[i];
                pRow[base[i] + j] = topps[i];
            }
        }
        final boolean sample = !greedy;
        final int rowsTotal = total;
        long tv = System.nanoTime();
        Object[] verdict = onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                DeepSeekModel.Session[] per = new DeepSeekModel.Session[fnb];
                for (int r = 0; r < fnb; r++) {
                    per[r] = seqOf[r] >= 0 ? mine[rank][seqOf[r]] : pads[rank];
                }
                return Dsv.scoped0(() -> {
                    long[] argmax = graphs[rank].get(key).replayVerify(toks, poss, jRow, per, pads[rank]);
                    long[][] drawn = null;
                    if (sample && rank == 0) {
                        drawn = DeepSeekSampler.verify(graphs[rank].get(key).rowLogits(rowsTotal), draftOf, tRow, pRow);
                    }
                    return new Object[]{argmax, drawn};
                });
            }
        }, r -> { });
        specNs[1] += System.nanoTime() - tv;
        long[] argmax = (long[]) verdict[0];
        long[][] drawn = (long[][]) verdict[1];
        int[][] out = new int[n][];
        int[] accepted = new int[n];
        for (int i = 0; i < n; i++) {
            int a = 0;
            if (greedy) {
                while (a < k[i] && argmax[base[i] + a] == drafts[i].tokens()[a]) {
                    a++;
                }
                out[i] = new int[a + 1];
                for (int j = 0; j <= a; j++) {
                    out[i][j] = (int) argmax[base[i] + j];
                }
            } else {
                while (a < k[i] && drawn[0][base[i] + a] == 1) {
                    a++;
                }
                out[i] = new int[a + 1];
                for (int j = 0; j < a; j++) {
                    out[i][j] = drafts[i].tokens()[j];
                }
                out[i][a] = (int) drawn[1][base[i] + a];
            }
            accepted[i] = a;
        }
        long tc = System.nanoTime();
        gatherAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                Dsv.scoped0(() -> {
                    graphs[rank].get(key).commit(base, accepted, mine[rank], positions);
                    return null;
                });
            }
            return null;
        });
        specNs[2] += System.nanoTime() - tc;
        specRounds++;
        specRows += total;
        specTokens += java.util.Arrays.stream(out).mapToInt(x -> x.length).sum();
        if (calibration != null) {
            synchronized (calibration) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j <= Math.min(accepted[i], k[i] - 1); j++) {
                        calibration.add(new float[]{j, drafts[i].confidence()[j], j < accepted[i] ? 1f : 0f});
                    }
                }
            }
        }
        lastDrafted = drafts[0];
        lastAccepted = accepted[0];
        return out;
    }

    /** Test hook: when set, every verified draft position is recorded as {@code {position, confidence, hit}}. */
    java.util.List<float[]> calibration;

    /** Rows verified and tokens produced by speculative rounds (reset by {@link #specStats}). */
    long specRows;
    long specTokens;

    /** Nanoseconds spent drafting, verifying (replay + sampling) and committing, over {@link #specRounds} rounds. */
    final long[] specNs = new long[3];
    int specRounds;

    String specStats() {
        String s = String.format("rounds %d: draft %.1f ms, verify %.1f ms, commit %.1f ms per round", specRounds,
                specNs[0] / 1e6 / Math.max(1, specRounds), specNs[1] / 1e6 / Math.max(1, specRounds), specNs[2] / 1e6 / Math.max(1, specRounds));
        s += String.format("; %.2f rows and %.2f tokens per round per sequence-slot (rows %d, tokens %d)", (double) specRows / Math.max(1, specRounds),
                (double) specTokens / Math.max(1, specRounds), specRows, specTokens);
        specRows = 0;
        specTokens = 0;
        s += String.format("; pointer rebuilds %d (%.1f ms each, per rank thread)", DeepSeekDecodeGraph.FILLS.get() / Math.max(1, workers.length),
                DeepSeekDecodeGraph.FILL_NS.get() / 1e6 / Math.max(1, DeepSeekDecodeGraph.FILLS.get()));
        long[] vn = DeepSeekDecodeGraph.VERIFY_NS;
        int ranks = Math.max(1, workers.length);
        s += String.format("; replay host (per rank): inputs %.1f ms, engram %.1f ms, replay+wait %.1f ms", vn[0] / 1e6 / ranks / Math.max(1, specRounds),
                vn[1] / 1e6 / ranks / Math.max(1, specRounds), vn[2] / 1e6 / ranks / Math.max(1, specRounds));
        java.util.Arrays.fill(vn, 0L);
        DeepSeekDecodeGraph.FILLS.set(0);
        DeepSeekDecodeGraph.FILL_NS.set(0);
        java.util.Arrays.fill(specNs, 0L);
        specRounds = 0;
        return s;
    }

    private int[][] plainFallback(int[] ids, int[] last, int[] pos, double[] temps, double[] topps) {
        int[][] o = new int[ids.length][];
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            all.add(i);
        }
        plainRound(ids, last, pos, temps, topps, all, o);
        return o;
    }

    /** Verify rows (sequences times block rows) above which the sequential path is used instead of a graph. */
    private static final int MAX_VERIFY_ROWS = 384;
    private volatile boolean verifyGraphsDisabled;

    /** Statistics hook of the baseline (tests): the last request's draft and how many drafts were accepted. */
    DeepSeekDraft.Result lastDrafted;
    int lastAccepted;

    @Override
    public boolean speculationMayOvershoot() {
        return true;
    }

    /** Draft graphs by rank and slot count ({@code rank * 1024 + slots}). */
    private final Map<Integer, DeepSeekDraftGraph> draftGraphs = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean draftGraphsDisabled;

    /** The draft of {@link #draft} as a captured graph per slot count; {@code null} when it cannot be captured. */
    private DeepSeekDraft.Result[] draftGraphed(int[] requestIds, int[] lastTokens) {
        int n = requestIds.length;
        int slots = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
        boolean ready = true;
        for (boolean ok : gatherAllRanks(rank -> {
            try {
                if (draftGraphs.containsKey(rank * 1024 + slots)) {
                    return true;
                }
                if (pads[rank] == null) {
                    pads[rank] = replicas[rank].newSession();
                }
                long t0 = System.nanoTime();
                draftGraphs.put(rank * 1024 + slots, DeepSeekDraftGraph.capture(replicas[rank], pads[rank], slots,
                        3 * args.dim(), Dsv.device().index()));
                if (rank == 0) {
                    logger.info("Captured DeepSeek draft graph: {} slots in {} ms", slots, (System.nanoTime() - t0) / 1_000_000L);
                }
                return true;
            } catch (Throwable t) {
                logger.warn("DeepSeek draft graph capture failed on rank {}: {}", rank, t.toString());
                return false;
            }
        })) {
            ready &= ok;
        }
        if (!ready) {
            draftGraphsDisabled = true;
            return null;
        }
        int[] startPos = new int[n];
        DeepSeekModel.Session[][] mine = new DeepSeekModel.Session[workers.length][n];
        for (int i = 0; i < n; i++) {
            DeepSeekModel.Session[] s = sessions.get(requestIds[i]);
            if (s[0].draft == null || s[0].lastTap == null) {
                throw new IllegalStateException("sequence has no draft state (prefill it first)");
            }
            startPos[i] = s[0].draftPos;
            for (int r = 0; r < workers.length; r++) {
                mine[r][i] = s[r];
            }
        }
        return onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return Dsv.scoped0(() -> draftGraphs.get(rank * 1024 + slots).replay(lastTokens, startPos, mine[rank], pads[rank]));
            }
        }, r -> { });
    }

    @Override
    public boolean supportsBatchedPrefill() {
        return true;
    }

    @Override
    public Tensor[] prefillBatch(int[] requestIds, int[][] prompts) {
        int n = requestIds.length;
        if (n == 1) {
            return new Tensor[]{prefill(requestIds[0], prompts[0], 0)};
        }
        DeepSeekModel.Session[][] mine = new DeepSeekModel.Session[workers.length][n];
        for (int i = 0; i < n; i++) {
            DeepSeekModel.Session[] s = sessions.get(requestIds[i]);
            if (s == null) {
                throw new IllegalArgumentException("unknown request " + requestIds[i]);
            }
            for (int r = 0; r < workers.length; r++) {
                mine[r][i] = s[r];
            }
        }
        Tensor all = onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return Dsv.scoped(() -> replicas[rank].forwardPrefillBatch(prompts, mine[rank]));
            }
        }, Tensor::close);
        Tensor[] out = new Tensor[n];
        for (int i = 0; i < n; i++) {
            out[i] = smile.torch.Aten.one("aten::clone", smile.torch.Aten.one("aten::narrow", all, 0L, (long) i, 1L));
        }
        all.close();
        return out;
    }

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        int n = requestIds.length;
        DeepSeekModel.Session[][] mine = new DeepSeekModel.Session[workers.length][n];
        for (int i = 0; i < n; i++) {
            DeepSeekModel.Session[] s = sessions.get(requestIds[i]);
            if (s == null) {
                throw new IllegalArgumentException("unknown request " + requestIds[i]);
            }
            for (int r = 0; r < workers.length; r++) {
                mine[r][i] = s[r];
            }
        }
        if (graphEligible(n, positions)) {
            int nb = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
            int maxPos = 0;
            for (int p : positions) {
                maxPos = Math.max(maxPos, p);
            }
            int lenCap = 512;
            while (lenCap < maxPos + 1) {
                lenCap <<= 2;
            }
            final long key = ((long) nb << 32) | lenCap;
            final int fnb = nb;
            final int flen = lenCap;
            boolean ready = true;
            for (boolean ok : gatherAllRanks(rank -> prepareGraph(rank, key, fnb, flen))) {
                ready &= ok;
            }
            if (ready) {
                return onAllRanks(rank -> {
                    try (var guard = Tensor.noGradGuard()) {
                        return Dsv.scoped(() -> graphs[rank].get(key).replay(lastTokens, positions, mine[rank], pads[rank]));
                    }
                }, Tensor::close);
            }
            graphsDisabled = true;
            logger.warn("DeepSeek decode CUDA graph capture failed on a rank; using eager decode");
        }
        return onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                return Dsv.scoped(() -> replicas[rank].forwardBatch(lastTokens, positions, mine[rank]));
            }
        }, Tensor::close);
    }

    /** The GPU section times of the captured graphs ({@code SMILE_DS_STAMP=1}): rank 0 in full, the others' totals. */
    String stampReport() {
        List<String> reports = gatherAllRanks(rank -> {
            StringBuilder sb = new StringBuilder();
            graphs[rank].forEach((k, g) -> sb.append("rank ").append(rank).append(" graph batch ").append((k >> 32) & 0xffffff)
                    .append(" cap ").append(k & 0xffffffffL).append('\n').append(g.stampReport()));
            return sb.toString();
        });
        StringBuilder out = new StringBuilder(reports.get(0));
        for (int r = 1; r < reports.size(); r++) {
            for (String line : reports.get(r).split("\n")) {
                if (line.contains("moe.allreduce") || line.contains("TOTAL") || line.startsWith("rank")) {
                    out.append("RANK").append(r).append(' ').append(line).append('\n');
                }
            }
        }
        return out.toString();
    }

    /** Starts or stops the CUDA profiler range on every rank (for {@code ncu --profile-from-start off}). */
    void profilerRange(boolean start) {
        gatherAllRanks(rank -> {
            Native.dsProfiler(start);
            return Boolean.TRUE;
        });
    }

    private boolean graphEligible(int n, int[] positions) {
        if (!GRAPHS || graphsDisabled || !onCuda || n > MAX_GRAPH_BATCH || !Native.dsGraphAvailable()) {
            return false;
        }
        for (int p : positions) {
            if (p < minGraphPos) {
                return false;
            }
        }
        return true;
    }

    /** Captures the graph for {@code key} on this rank when it does not exist yet (on the rank's own thread). */
    private boolean prepareGraph(int rank, long key, int nb, int lenCap) {
        return prepareGraph(rank, key, nb, lenCap, false);
    }

    private boolean prepareGraph(int rank, long key, int nb, int lenCap, boolean verify) {
        try {
            if (graphs[rank].containsKey(key)) {
                return true;
            }
            if (pads[rank] == null) {
                pads[rank] = replicas[rank].newSession();
            }
            long t0 = System.nanoTime();
            long free0 = rank == 0 ? smile.torch.Native.cudaMemGetInfo(Dsv.device().index())[0] : 0;
            graphs[rank].put(key, DeepSeekDecodeGraph.capture(replicas[rank], pads[rank], nb, lenCap, args.hcMult(),
                    Dsv.device().index(), verify));
            if (rank == 0) {
                logger.info("Captured DeepSeek {} graph: batch {} positions up to {} in {} ms, {} MB of GPU memory",
                        verify ? "verify" : "decode", nb, lenCap, (System.nanoTime() - t0) / 1_000_000L,
                        (free0 - smile.torch.Native.cudaMemGetInfo(Dsv.device().index())[0]) >> 20);
            }
            return true;
        } catch (Throwable t) {
            logger.warn("DeepSeek decode graph capture failed on rank {}: {}", rank, t.toString());
            return false;
        }
    }

    /** Runs {@code task(rank)} on every rank's thread and returns the results in rank order. */
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
        DeepSeekModel.Session[] s = sessions.remove(requestId);
        if (s == null) {
            return;
        }
        if (sessionPool.size() < MAX_POOLED_SESSIONS) {
            sessionPool.add(s);
            return;
        }
        List<Future<?>> frees = new ArrayList<>();
        for (int r = 0; r < s.length; r++) {
            final DeepSeekModel.Session mine = s[r];
            frees.add(workers[r].submit(() -> {
                mine.close();
                return null;
            }));
        }
        await(frees);
    }

    @Override
    public void close() throws IOException {
        for (ExecutorService w : workers) {
            w.shutdownNow();
        }
        for (DeepSeekModel m : replicas) {
            if (m != null) {
                m.close();
            }
        }
        rowsSource.close();
        if (group != null) {
            group.close();
        }
    }
}
