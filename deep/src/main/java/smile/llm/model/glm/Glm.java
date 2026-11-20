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
 * decode step over {@code B} requests is one batched pass. Not yet: prefix reuse, CUDA graphs, speculation (MTP),
 * images.
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
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Glm.class);
    private static final boolean GRAPHS = !"0".equals(System.getenv("SMILE_GLM_GRAPH"));
    private static final int MAX_GRAPH_BATCH = Integer.getInteger("smile.glm.graph.maxbatch", 32);
    private final Map<Integer, GlmDecodeGraph>[] graphs;
    private final boolean onCuda;
    private volatile boolean graphsDisabled;

    @SuppressWarnings("unchecked")
    private Glm(String name, GlmModelArgs args, GlmTokenizer tokenizer, Path dir, byte[] devices, int maxSessions) {
        this.name = name;
        this.args = args;
        this.tokenizer = tokenizer;
        this.maxSessions = maxSessions;
        int ranks = devices == null ? 1 : devices.length;
        this.group = ranks > 1 ? new TensorParallelGroup(ParallelConfig.tensorParallel(devices)) : null;
        this.workers = new ExecutorService[ranks];
        this.replicas = new GlmModel[ranks];
        this.graphs = new Map[ranks];
        for (int r = 0; r < ranks; r++) {
            graphs[r] = new java.util.HashMap<>();
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
                throw new IllegalArgumentException("GLM-5.3 image input is not supported yet");
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
        return prefillChunk(requestId, prompt, 0, prompt.length);
    }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        GlmModel.Session[] s = session(requestId);
        if (s[0].length() != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + s[0].length() + ", not " + from);
        }
        int[] ids = Arrays.copyOfRange(prompt, from, to);
        boolean last = to == prompt.length;
        Tensor logits = onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                GlmModel m = replicas[rank];
                return Gx.layerScoped(() -> {
                    Tensor hidden = m.forward(ids, s[rank], null);
                    // only the last row of the last chunk is sampled; other chunks return a placeholder
                    Tensor row = Gx.narrow(hidden, 0, hidden.size(0) - 1, 1);
                    return last ? m.logits(row) : Gx.zeros(smile.deep.tensor.ScalarType.Float, 1);
                });
            }
        }, Tensor::close);
        if (!last) {
            logits.close();
            return null;
        }
        return logits;
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
        if (graphEligible(n, positions)) {
            int nb = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
            boolean ready = true;
            java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(workers.length);
            for (boolean ok : gatherAllRanks(rank -> prepareGraph(rank, nb, barrier))) {
                ready &= ok;
            }
            if (ready) {
                return onAllRanks(rank -> {
                    try (var guard = Tensor.noGradGuard()) {
                        return graphs[rank].get(nb).replay(lastTokens, mine[rank]);
                    }
                }, Tensor::close);
            }
            graphsDisabled = true;
            logger.warn("GLM decode CUDA graph capture failed on a rank; using eager decode");
        }
        return onAllRanks(rank -> {
            try (var guard = Tensor.noGradGuard()) {
                GlmModel m = replicas[rank];
                return Gx.layerScoped(() -> m.logits(m.forwardBatch(lastTokens, mine[rank])));
            }
        }, Tensor::close);
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
                return null;
            }));
        }
        try {
            await(teardown);
        } catch (RuntimeException e) {
            logger.warn("GLM graph teardown failed: {}", e.toString());
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
