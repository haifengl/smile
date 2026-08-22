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
package smile.llm.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.cache.KvCacheExhaustedException;
import smile.llm.cache.KvCachePool;

/**
 * Continuous-batching inference runtime.
 *
 * <p>Fluid Injection admits work up to {@code maxInFlight} while KV pages are
 * free. Each tick prefills (optionally chunked under a token budget), then runs
 * a batched {@link ModelExecutor#decodeStep} over all decoding requests.
 * {@link GenerationHandle#abort()} Instant-Evicts queued and in-flight KV.
 *
 * <p>When {@link ModelExecutor#supportsStepApi()} is {@code false} (test stubs),
 * the engine falls back to serial {@link LanguageModel#generate}.
 *
 * @author Haifeng Li
 */
public final class InferenceEngine implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(InferenceEngine.class);

    /** Default max new-prefill tokens per scheduler tick. */
    public static final int DEFAULT_PREFILL_TOKEN_BUDGET = 2048;
    /** Default max time a job may wait for admission before failing. */
    public static final long DEFAULT_ADMISSION_TIMEOUT_MS = 120_000L;

    private final ModelExecutor executor;
    private final int maxInFlight;
    private final int maxDecodeBatch;
    private final int prefillTokenBudget;
    private final long admissionTimeoutMs;
    private final LinkedBlockingQueue<Queued> waiting = new LinkedBlockingQueue<>();
    private final List<Active> active = new ArrayList<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong nextId = new AtomicLong(1);
    private final AtomicInteger queuedCount = new AtomicInteger();
    private final AtomicLong queueWaitMsTotal = new AtomicLong();
    private final AtomicLong prefillMsTotal = new AtomicLong();
    private final AtomicLong decodeMsTotal = new AtomicLong();
    private final AtomicInteger decodeBatchSamples = new AtomicInteger();
    private final Thread worker;
    private volatile boolean running = true;

    /**
     * @param executor   model execution surface.
     * @param maxInFlight max concurrent admitted jobs ({@code smile.chat.max-batch-size}).
     */
    public InferenceEngine(ModelExecutor executor, int maxInFlight) {
        this(executor, maxInFlight, maxInFlight, DEFAULT_PREFILL_TOKEN_BUDGET,
                DEFAULT_ADMISSION_TIMEOUT_MS);
    }

    /**
     * @param executor            model execution surface.
     * @param maxInFlight         Fluid Injection cap.
     * @param maxDecodeBatch      max requests in one {@code decodeStep} ({@code <= maxInFlight}).
     * @param prefillTokenBudget  max prompt tokens prefilled per tick.
     * @param admissionTimeoutMs  fail waiting jobs after this many ms ({@code <= 0} = never).
     */
    public InferenceEngine(ModelExecutor executor, int maxInFlight, int maxDecodeBatch,
                           int prefillTokenBudget, long admissionTimeoutMs) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be >= 1");
        }
        if (maxDecodeBatch < 1) {
            throw new IllegalArgumentException("maxDecodeBatch must be >= 1");
        }
        if (prefillTokenBudget < 1) {
            throw new IllegalArgumentException("prefillTokenBudget must be >= 1");
        }
        this.maxInFlight = maxInFlight;
        this.maxDecodeBatch = Math.min(maxDecodeBatch, maxInFlight);
        this.prefillTokenBudget = prefillTokenBudget;
        this.admissionTimeoutMs = admissionTimeoutMs;
        this.worker = new Thread(this::loop, "smile-inference-engine");
        this.worker.setDaemon(true);
        this.worker.start();
        KvCachePool pool = executor.kvCachePool();
        logger.info("Continuous batching enabled: stepApi={} maxInFlight={} maxDecodeBatch={} "
                        + "prefillTokenBudget={} admissionTimeoutMs={} kvSlots={} kvFree={}",
                executor.supportsStepApi(), this.maxInFlight, this.maxDecodeBatch,
                this.prefillTokenBudget, this.admissionTimeoutMs,
                pool == null ? -1 : pool.numSlots(),
                pool == null ? -1 : pool.freeSlots());
    }

    /** Underlying language model. */
    public LanguageModel model() {
        return executor.model();
    }

    /** Max in-flight generations (Fluid Injection cap). */
    public int maxInFlight() {
        return maxInFlight;
    }

    /** Max decode batch size per step. */
    public int maxDecodeBatch() {
        return maxDecodeBatch;
    }

    /** Jobs waiting for admission. */
    public int queueSize() {
        return queuedCount.get();
    }

    /** Jobs admitted / running on the worker. */
    public int inFlight() {
        return inFlight.get();
    }

    /** Free KV slots when a pool is present; {@code -1} if none. */
    public int kvFreeSlots() {
        KvCachePool pool = executor.kvCachePool();
        return pool == null ? -1 : pool.freeSlots();
    }

    /** Free KV pages when a pool is present; {@code -1} if none. */
    public int kvFreePages() {
        KvCachePool pool = executor.kvCachePool();
        return pool == null ? -1 : pool.freePages();
    }

    /** Active decode requests on the last tick (approx). */
    public int activeDecodeCount() {
        int n = 0;
        synchronized (active) {
            for (Active a : active) {
                if (a.phase == Phase.DECODING) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Cumulative queue wait milliseconds. */
    public long queueWaitMsTotal() {
        return queueWaitMsTotal.get();
    }

    /** Cumulative prefill milliseconds. */
    public long prefillMsTotal() {
        return prefillMsTotal.get();
    }

    /** Cumulative decode-step milliseconds. */
    public long decodeMsTotal() {
        return decodeMsTotal.get();
    }

    /**
     * Enqueues a generation request.
     *
     * @param request generation parameters.
     * @return handle with future + abort.
     */
    public GenerationHandle submit(GenerationRequest request) {
        return submit(request, h -> {});
    }

    /**
     * Enqueues a generation request.
     *
     * <p>{@code onCreated} runs after the handle exists and <em>before</em> the
     * job is visible to the worker (so listeners can bind {@code requestId}
     * without racing the first decode tokens).
     *
     * @param request   generation parameters.
     * @param onCreated callback with the new handle; must not block.
     * @return handle with future + abort.
     */
    public GenerationHandle submit(GenerationRequest request, Consumer<GenerationHandle> onCreated) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onCreated, "onCreated");
        if (!running) {
            throw new IllegalStateException("InferenceEngine is closed");
        }
        long id = nextId.getAndIncrement();
        CompletableFuture<ChatCompletion> future = new CompletableFuture<>();
        GenerationHandle handle = new GenerationHandle(id, future);
        onCreated.accept(handle);
        Queued q = new Queued(handle, request, System.nanoTime());
        waiting.add(q);
        queuedCount.incrementAndGet();
        return handle;
    }

    private void loop() {
        if (!executor.supportsStepApi()) {
            logger.warn("ModelExecutor does not support step APIs; falling back to serial "
                    + "LanguageModel.generate (no continuous batching)");
            loopLegacyGenerate();
            return;
        }
        while (running || !waiting.isEmpty() || !active.isEmpty()) {
            try {
                drainAborted();
                failTimedOutWaiting();
                admitWaiting();
                if (active.isEmpty()) {
                    Queued peek = waiting.poll(50, TimeUnit.MILLISECONDS);
                    if (peek != null) {
                        queuedCount.decrementAndGet();
                        waiting.add(peek);
                        queuedCount.incrementAndGet();
                    }
                    maybeEmptyDeviceCache();
                    continue;
                }
                runPrefills();
                runDecodeStep();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                logger.error("InferenceEngine worker error", t);
            }
        }
    }

    /** Serial generate path for stubs without step APIs. */
    private void loopLegacyGenerate() {
        while (running || !waiting.isEmpty() || inFlight.get() > 0) {
            try {
                drainAbortedFromQueue();
                Queued next = waiting.poll(50, TimeUnit.MILLISECONDS);
                if (next == null) {
                    continue;
                }
                queuedCount.decrementAndGet();
                if (next.handle.isAborted()) {
                    next.handle.future().completeExceptionally(new CancellationException("aborted"));
                    continue;
                }
                inFlight.incrementAndGet();
                try {
                    recordQueueWait(next.enqueuedAtNanos);
                    ChatCompletion result = runOneGenerate(next);
                    if (!next.handle.isAborted()) {
                        next.handle.future().complete(result);
                    } else if (!next.handle.future().isDone()) {
                        next.handle.future().completeExceptionally(new CancellationException("aborted"));
                    }
                } catch (CancellationException cancel) {
                    if (!next.handle.future().isDone()) {
                        next.handle.future().completeExceptionally(cancel);
                    }
                } catch (Throwable t) {
                    if (!next.handle.future().isDone()) {
                        next.handle.future().completeExceptionally(t);
                    }
                    logger.warn("Generation failed requestId={}: {}",
                            next.handle.requestId(), t.toString());
                } finally {
                    inFlight.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                logger.error("InferenceEngine worker error", t);
            }
        }
    }

    private void drainAborted() {
        drainAbortedFromQueue();
        Iterator<Active> it = active.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (a.handle.isAborted()) {
                it.remove();
                safeEvict(a);
                completeCancel(a);
                inFlight.decrementAndGet();
            }
        }
    }

    private void drainAbortedFromQueue() {
        for (Queued q : waiting) {
            if (q.handle.isAborted() && waiting.remove(q)) {
                queuedCount.decrementAndGet();
                q.handle.future().completeExceptionally(new CancellationException("aborted"));
            }
        }
    }

    private void failTimedOutWaiting() {
        if (admissionTimeoutMs <= 0) {
            return;
        }
        long now = System.nanoTime();
        for (Queued q : waiting) {
            long waitedMs = (now - q.enqueuedAtNanos) / 1_000_000L;
            if (waitedMs >= admissionTimeoutMs && waiting.remove(q)) {
                queuedCount.decrementAndGet();
                q.handle.future().completeExceptionally(new IllegalStateException(
                        "admission timeout after " + waitedMs + " ms"));
            }
        }
    }

    private void admitWaiting() {
        while (active.size() < maxInFlight) {
            Queued next = waiting.peek();
            if (next == null) {
                break;
            }
            if (next.handle.isAborted()) {
                if (waiting.remove(next)) {
                    queuedCount.decrementAndGet();
                    next.handle.future().completeExceptionally(new CancellationException("aborted"));
                }
                continue;
            }
            LanguageModel lm = executor.model();
            int[] prompt = next.request.promptTokens();
            if (prompt == null) {
                prompt = lm.encodeChat(next.request.dialog());
            }
            int promptLen = prompt.length;
            int maxGen = clampMaxGen(promptLen, next.request.maxGenLen(), lm.maxSeqLen());
            // Honor max_tokens and max-seq-len: reserve the full window. If KV cannot
            // fit it, leave the request queued (Fluid Injection waits for Instant Eviction).
            int desired = Math.min(lm.maxSeqLen(), promptLen + maxGen);
            int kvId;
            try {
                kvId = executor.bind(prompt, desired);
            } catch (KvCacheExhaustedException ex) {
                logger.info("KV full; deferring admission (inFlight={}/{} queued={} freeSlots={}): {}",
                        active.size(), maxInFlight, queuedCount.get(), kvFreeSlots(), ex.getMessage());
                break;
            } catch (IllegalStateException | IllegalArgumentException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("KV") || msg.contains("capacity") || msg.contains("exhausted")) {
                    logger.info("KV admission deferred (inFlight={}/{} queued={} freeSlots={}): {}",
                            active.size(), maxInFlight, queuedCount.get(), kvFreeSlots(), msg);
                    break;
                }
                waiting.remove(next);
                queuedCount.decrementAndGet();
                next.handle.future().completeExceptionally(ex);
                continue;
            } catch (RuntimeException ex) {
                waiting.remove(next);
                queuedCount.decrementAndGet();
                next.handle.future().completeExceptionally(ex);
                continue;
            }
            if (!waiting.remove(next)) {
                executor.evict(kvId);
                break;
            }
            queuedCount.decrementAndGet();
            recordQueueWait(next.enqueuedAtNanos);

            int matched = executor.prefixLen(kvId);
            int from = matched;
            // Keep last prompt token for next-token logits when generating.
            if (from > 0 && promptLen < desired && promptLen > 0) {
                from = Math.min(from, promptLen - 1);
            }
            GenerationListener listener = next.request.listener();
            if (listener != null) {
                listener.onInputTokens(promptLen);
                listener.onCachedInputTokens(Math.min(matched, promptLen));
            }
            Active a = new Active(next.handle, next.request, prompt, kvId, from, promptLen,
                    maxGen, desired, next.request.temperature(), next.request.topp(),
                    next.request.seed(), listener);
            active.add(a);
            inFlight.incrementAndGet();
            logger.info("Admitted requestId={} kvId={} promptLen={} capacity={} maxGen={} "
                            + "inFlight={}/{} queued={} kvFree={}",
                    next.handle.requestId(), kvId, promptLen, desired, maxGen,
                    active.size(), maxInFlight, queuedCount.get(), kvFreeSlots());
        }
    }

    private void runPrefills() {
        int budget = prefillTokenBudget;
        for (Active a : active) {
            if (a.phase != Phase.PREFILL || budget <= 0) {
                continue;
            }
            if (a.handle.isAborted()) {
                continue;
            }
            int remaining = a.promptLen - a.prefillFrom;
            if (remaining <= 0) {
                a.phase = Phase.DECODING;
                a.lastToken = a.prompt[a.promptLen - 1];
                continue;
            }
            int chunk = Math.min(remaining, budget);
            int to = a.prefillFrom + chunk;
            long t0 = System.nanoTime();
            try {
                Tensor logits = executor.prefillChunk(a.kvRequestId, a.prompt, a.prefillFrom, to);
                prefillMsTotal.addAndGet((System.nanoTime() - t0) / 1_000_000L);
                budget -= chunk;
                a.prefillFrom = to;
                if (to < a.promptLen) {
                    continue;
                }
                // Full prompt prefilled — sample first generated token from logits.
                if (logits == null) {
                    if (a.maxGenLen == 0) {
                        finishActive(a, FinishReason.length);
                    } else {
                        a.phase = Phase.DECODING;
                        a.lastToken = a.prompt[a.promptLen - 1];
                    }
                    continue;
                }
                try (logits) {
                    if (a.maxGenLen == 0) {
                        finishActive(a, FinishReason.length);
                    } else {
                        sampleAndAppend(a, logits);
                        if (a.phase != Phase.DONE) {
                            a.phase = Phase.DECODING;
                        }
                    }
                }
            } catch (Throwable t) {
                failActive(a, t);
            }
        }
        active.removeIf(a -> a.phase == Phase.DONE);
    }

    private void runDecodeStep() {
        List<Active> decoding = new ArrayList<>();
        for (Active a : active) {
            if (a.phase == Phase.DECODING && !a.handle.isAborted()) {
                decoding.add(a);
                if (decoding.size() >= maxDecodeBatch) {
                    break;
                }
            }
        }
        if (decoding.isEmpty()) {
            return;
        }
        int b = decoding.size();
        int prefills = 0;
        for (Active a : active) {
            if (a.phase == Phase.PREFILL) {
                prefills++;
            }
        }
        logger.debug("Decode step: batch={} inFlight={} prefilling={} queued={} kvFree={}",
                b, active.size(), prefills, queuedCount.get(), kvFreeSlots());
        int[] ids = new int[b];
        int[] toks = new int[b];
        int[] positions = new int[b];
        for (int i = 0; i < b; i++) {
            Active a = decoding.get(i);
            ids[i] = a.kvRequestId;
            toks[i] = a.lastToken;
            // Write position of the last generated (or first sampled) token.
            positions[i] = a.promptLen + a.completion.size() - 1;
        }
        long t0 = System.nanoTime();
        try (Tensor logits = executor.decodeStep(ids, toks, positions)) {
            decodeMsTotal.addAndGet((System.nanoTime() - t0) / 1_000_000L);
            decodeBatchSamples.incrementAndGet();
            for (int i = 0; i < b; i++) {
                Active a = decoding.get(i);
                if (a.phase != Phase.DECODING) {
                    continue;
                }
                if (a.handle.isAborted()) {
                    safeEvict(a);
                    a.phase = Phase.DONE;
                    completeCancel(a);
                    inFlight.decrementAndGet();
                    continue;
                }
                try (var row = Index.of(i);
                     Tensor rowLogits = logits.get(row).unsqueeze(0)) {
                    sampleAndAppend(a, rowLogits);
                } catch (Throwable t) {
                    failActive(a, t);
                }
            }
        } catch (Throwable t) {
            for (Active a : decoding) {
                if (a.phase != Phase.DONE) {
                    failActive(a, t);
                }
            }
        }
        active.removeIf(a -> a.phase == Phase.DONE);
        maybeEmptyDeviceCache();
    }

    private void sampleAndAppend(Active a, Tensor logitsRow) {
        if (a.seed != 0 && a.completion.isEmpty()) {
            smile.torch.smile_torch_h.smile_manual_seed(a.seed);
        }
        try (Tensor next = Sampling.sampleNext(logitsRow, a.temperature, a.topp);
             Tensor cpu = next.to(smile.deep.tensor.Device.CPU())) {
            int token = (int) cpu.longArray()[0];
            a.completion.add(token);
            a.lastToken = token;
            if (a.listener != null) {
                a.listener.onGeneratedTokens(1);
            }
            a.streamer.accept(token);
            a.streamer.maybeEmit(a.listener, false);
            boolean stop = isStop(token);
            if (stop || a.completion.size() >= a.maxGenLen
                    || a.promptLen + a.completion.size() >= a.totalCapacity) {
                finishActive(a, stop ? FinishReason.stop : FinishReason.length);
            }
        }
    }

    private boolean isStop(int token) {
        for (int s : executor.stopTokens()) {
            if (s == token) {
                return true;
            }
        }
        return false;
    }

    private void finishActive(Active a, FinishReason reason) {
        int[] completion = a.completion.stream().mapToInt(Integer::intValue).toArray();
        // Trim stop token from completion text (match LanguageModel.generate).
        if (reason == FinishReason.stop && completion.length > 0) {
            completion = Arrays.copyOf(completion, completion.length - 1);
        }
        a.streamer.maybeEmit(a.listener, true);
        String text = executor.decode(completion);
        // Prefer streamed text if non-empty decode of specials skipped differently.
        ChatCompletion result = new ChatCompletion(
                executor.model().name(), text, a.prompt, completion, reason, null);
        try {
            executor.finish(a.kvRequestId, concat(a.prompt, completion));
        } catch (Throwable t) {
            logger.debug("finishRequest failed: {}", t.toString());
            safeEvict(a);
        }
        a.phase = Phase.DONE;
        a.kvRequestId = -1;
        if (!a.handle.future().isDone()) {
            a.handle.future().complete(result);
        }
        inFlight.decrementAndGet();
    }

    private void failActive(Active a, Throwable t) {
        safeEvict(a);
        a.phase = Phase.DONE;
        a.kvRequestId = -1;
        if (!a.handle.future().isDone()) {
            a.handle.future().completeExceptionally(t);
        }
        inFlight.decrementAndGet();
        logger.warn("Generation failed requestId={}: {}", a.handle.requestId(), t.toString());
    }

    private void completeCancel(Active a) {
        if (!a.handle.future().isDone()) {
            a.handle.future().completeExceptionally(new CancellationException("aborted"));
        }
    }

    private void safeEvict(Active a) {
        if (a.kvRequestId > 0) {
            try {
                executor.evict(a.kvRequestId);
            } catch (Throwable t) {
                logger.debug("evict failed: {}", t.toString());
            }
            a.kvRequestId = -1;
        }
    }

    private void maybeEmptyDeviceCache() {
        if (!active.isEmpty()) {
            return;
        }
        KvCachePool pool = executor.kvCachePool();
        if (pool == null || pool.boundRequestCount() != 0) {
            return;
        }
        try {
            smile.deep.tensor.Device device = pool.device();
            if (device != null) {
                device.emptyCache();
            }
        } catch (Throwable t) {
            logger.debug("emptyCache skipped: {}", t.toString());
        }
    }

    private void recordQueueWait(long enqueuedAtNanos) {
        queueWaitMsTotal.addAndGet(Math.max(0L, (System.nanoTime() - enqueuedAtNanos) / 1_000_000L));
    }

    private static int clampMaxGen(int promptLen, int maxGenLen, int maxSeqLen) {
        int maxAllowed = Math.max(0, maxSeqLen - promptLen);
        if (maxGenLen > maxAllowed) {
            maxGenLen = maxAllowed;
        }
        return Math.max(0, maxGenLen);
    }

    private static int[] concat(int[] prompt, int[] completion) {
        int[] seq = new int[prompt.length + completion.length];
        System.arraycopy(prompt, 0, seq, 0, prompt.length);
        System.arraycopy(completion, 0, seq, prompt.length, completion.length);
        return seq;
    }

    private ChatCompletion runOneGenerate(Queued job) {
        LanguageModel lm = executor.model();
        GenerationRequest request = job.request;
        int[] tokens = request.promptTokens();
        if (tokens == null) {
            tokens = lm.encodeChat(request.dialog());
        }
        return lm.generate(tokens, request.maxGenLen(), request.temperature(),
                request.topp(), request.logprobs(), request.seed(), request.listener(),
                job.handle::isAborted);
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
        try {
            worker.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Queued q : waiting) {
            q.handle.future().completeExceptionally(
                    new IllegalStateException("InferenceEngine closed"));
        }
        waiting.clear();
        queuedCount.set(0);
        for (Active a : active) {
            safeEvict(a);
            if (!a.handle.future().isDone()) {
                a.handle.future().completeExceptionally(
                        new IllegalStateException("InferenceEngine closed"));
            }
        }
        active.clear();
        inFlight.set(0);
    }

    private enum Phase { PREFILL, DECODING, DONE }

    private record Queued(GenerationHandle handle, GenerationRequest request, long enqueuedAtNanos) {}

    private final class Active {
        final GenerationHandle handle;
        final GenerationRequest request;
        final int[] prompt;
        int kvRequestId;
        int prefillFrom;
        final int promptLen;
        final int maxGenLen;
        final int totalCapacity;
        final double temperature;
        final double topp;
        final long seed;
        final GenerationListener listener;
        final List<Integer> completion = new ArrayList<>();
        final TextStreamer streamer;
        Phase phase = Phase.PREFILL;
        int lastToken;

        Active(GenerationHandle handle, GenerationRequest request, int[] prompt, int kvRequestId,
               int prefillFrom, int promptLen, int maxGenLen, int totalCapacity,
               double temperature, double topp, long seed, GenerationListener listener) {
            this.handle = handle;
            this.request = request;
            this.prompt = prompt;
            this.kvRequestId = kvRequestId;
            this.prefillFrom = prefillFrom;
            this.promptLen = promptLen;
            this.maxGenLen = maxGenLen;
            this.totalCapacity = totalCapacity;
            this.temperature = temperature;
            this.topp = topp;
            this.seed = seed;
            this.listener = listener;
            this.streamer = new TextStreamer(Math.max(1, maxGenLen), (tokens, skip) -> {
                try {
                    return executor.tryDecode(tokens, skip);
                } catch (java.nio.charset.CharacterCodingException e) {
                    throw new RuntimeException(e);
                }
            });
            this.lastToken = promptLen > 0 ? prompt[promptLen - 1] : 0;
        }
    }
}
