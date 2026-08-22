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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.llm.ChatCompletion;
import smile.llm.LanguageModel;
import smile.llm.cache.KvCachePool;

/**
 * Request-oriented inference runtime.
 *
 * <p>Clients submit one prompt per {@link GenerationRequest}. The engine owns
 * admission ({@linkplain #maxInFlight Fluid Injection} up to
 * {@code maxInFlight}), a single GPU worker thread, and
 * {@linkplain GenerationHandle#abort() Instant Eviction} of queued work.
 * Prefill and decode for each admitted job currently run to completion via
 * {@link LanguageModel#generate} (Phase Coexistence of multiple decodes in one
 * forward is layered on the same worker loop once {@link ModelExecutor} grows
 * multi-request step APIs).
 *
 * @author Haifeng Li
 */
public final class InferenceEngine implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(InferenceEngine.class);

    private final ModelExecutor executor;
    private final int maxInFlight;
    private final LinkedBlockingQueue<Queued> waiting = new LinkedBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong nextId = new AtomicLong(1);
    private final AtomicInteger queuedCount = new AtomicInteger();
    private final Thread worker;
    private volatile boolean running = true;

    /**
     * @param executor   model execution surface.
     * @param maxInFlight max concurrent admitted jobs ({@code smile.chat.max-batch-size}).
     */
    public InferenceEngine(ModelExecutor executor, int maxInFlight) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be >= 1");
        }
        this.maxInFlight = maxInFlight;
        this.worker = new Thread(this::loop, "smile-inference-engine");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Underlying language model. */
    public LanguageModel model() {
        return executor.model();
    }

    /** Max in-flight generations (Fluid Injection cap). */
    public int maxInFlight() {
        return maxInFlight;
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

    /**
     * Enqueues a generation request.
     *
     * @param request generation parameters.
     * @return handle with future + abort.
     */
    public GenerationHandle submit(GenerationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!running) {
            throw new IllegalStateException("InferenceEngine is closed");
        }
        long id = nextId.getAndIncrement();
        CompletableFuture<ChatCompletion> future = new CompletableFuture<>();
        GenerationHandle handle = new GenerationHandle(id, future);
        Queued q = new Queued(handle, request);
        waiting.add(q);
        queuedCount.incrementAndGet();
        return handle;
    }

    private void loop() {
        List<Queued> admitted = new ArrayList<>();
        while (running || !waiting.isEmpty() || inFlight.get() > 0) {
            try {
                // Instant Eviction: drop aborted queue entries.
                drainAbortedFromQueue();

                // Fluid Injection: admit while under maxInFlight.
                while (inFlight.get() + admitted.size() < maxInFlight) {
                    Queued next = waiting.poll(admitted.isEmpty() ? 50 : 0, TimeUnit.MILLISECONDS);
                    if (next == null) {
                        break;
                    }
                    queuedCount.decrementAndGet();
                    if (next.handle.isAborted()) {
                        next.handle.future().completeExceptionally(
                                new java.util.concurrent.CancellationException("aborted"));
                        continue;
                    }
                    admitted.add(next);
                }

                if (admitted.isEmpty()) {
                    continue;
                }

                // Phase Coexistence (lite): run prefill+decode for each admitted
                // job on this worker before injecting more. Batched decode across
                // jobs lands when ModelExecutor exposes multi-request steps.
                Iterator<Queued> it = admitted.iterator();
                while (it.hasNext()) {
                    Queued job = it.next();
                    it.remove();
                    if (job.handle.isAborted()) {
                        job.handle.future().completeExceptionally(
                                new java.util.concurrent.CancellationException("aborted"));
                        continue;
                    }
                    inFlight.incrementAndGet();
                    try {
                        ChatCompletion result = runOne(job.request);
                        if (!job.handle.isAborted()) {
                            job.handle.future().complete(result);
                        }
                    } catch (Throwable t) {
                        if (!job.handle.future().isDone()) {
                            job.handle.future().completeExceptionally(t);
                        }
                        logger.warn("Generation failed requestId={}: {}",
                                job.handle.requestId(), t.toString());
                    } finally {
                        inFlight.decrementAndGet();
                        // Instant Eviction of KV is handled inside LanguageModel.generate
                        // (unbindRequests) and by KvCachePool.unbindRequest when used.
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                logger.error("InferenceEngine worker error", t);
            }
        }
    }

    private void drainAbortedFromQueue() {
        for (Queued q : waiting) {
            if (q.handle.isAborted() && waiting.remove(q)) {
                queuedCount.decrementAndGet();
                q.handle.future().completeExceptionally(
                        new java.util.concurrent.CancellationException("aborted"));
            }
        }
    }

    private ChatCompletion runOne(GenerationRequest request) {
        LanguageModel lm = executor.model();
        int[] tokens = request.promptTokens();
        if (tokens == null) {
            tokens = lm.encodeChat(request.dialog());
        }
        return lm.generate(tokens, request.maxGenLen(), request.temperature(),
                request.topp(), request.logprobs(), request.seed(), request.listener());
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
    }

    private record Queued(GenerationHandle handle, GenerationRequest request) {}
}
