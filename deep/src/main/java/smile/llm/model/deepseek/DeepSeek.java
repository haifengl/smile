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

/**
 * DeepSeek-V4.1 behind the continuous-batching engine: a {@link LanguageModel} and {@link ModelExecutor} over
 * {@link DeepSeekModel}.
 *
 * <p>With several devices every device holds a replica of the dense layers and {@code 1/n} of each layer's routed
 * experts (expert parallelism, partial outputs summed with NCCL); each device is driven by its own thread because the
 * collectives need all ranks to be inside the same layer at once. One request's state is a {@link DeepSeekModel.Session}
 * per rank.
 *
 * <p>Current limits (the model code was written for validation first): one sequence per forward pass, so a decode
 * step over {@code B} requests is {@code B} forward passes; a prompt is prefilled in one pass (the engine's chunks are
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
                return Dsv.scoped(() -> m.forward(ids, startPos, null));
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
        List<Future<?>> allocation = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            final int rank = r;
            allocation.add(workers[r].submit(() -> {
                s[rank] = replicas[rank].newSession();
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

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        Tensor[] rows = new Tensor[requestIds.length];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = forward(requestIds[i], new int[]{lastTokens[i]}, positions[i]);
        }
        if (rows.length == 1) {
            return rows[0];
        }
        Tensor all = onAllRanksFirst(() -> Dsv.cat(0, rows));
        for (Tensor t : rows) {
            t.close();
        }
        return all;
    }

    private Tensor onAllRanksFirst(java.util.function.Supplier<Tensor> task) {
        try {
            return workers[0].submit(task::get).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
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
