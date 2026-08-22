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

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InferenceEngine} queue / abort / continuous-batching behavior.
 */
public class InferenceEngineTest {

    @Test
    public void testGivenSubmitWhenCompletedThenReturnsCompletion() throws Exception {
        try (var engine = new InferenceEngine(new StubExecutor(), 2)) {
            var handle = engine.submit(GenerationRequest.ofTokens(
                    new int[]{1, 2, 3}, 4, 0.0, 0.9, false, 0, null));
            ChatCompletion c = handle.future().get(5, TimeUnit.SECONDS);
            assertEquals("ok", c.content());
            assertEquals(FinishReason.stop, c.reason());
        }
    }

    @Test
    public void testGivenQueuedAbortWhenDrainedThenCancelled() throws Exception {
        StubExecutor stub = new StubExecutor();
        stub.blockFirst = true;
        try (var engine = new InferenceEngine(stub, 1)) {
            var first = engine.submit(GenerationRequest.ofTokens(
                    new int[]{1}, 2, 0.0, 0.9, false, 0, null));
            var second = engine.submit(GenerationRequest.ofTokens(
                    new int[]{2}, 2, 0.0, 0.9, false, 0, null));
            stub.awaitStarted(2, TimeUnit.SECONDS);
            second.abort();
            stub.release();
            first.future().get(5, TimeUnit.SECONDS);
            assertTrue(second.future().isCompletedExceptionally());
            try {
                second.future().get(1, TimeUnit.SECONDS);
                fail("expected cancellation");
            } catch (Exception e) {
                assertTrue(e.getCause() instanceof CancellationException
                        || e instanceof CancellationException);
            }
        }
    }

    @Test
    public void testGivenRunningAbortWhenCancelRequestedThenStopsCooperatively() throws Exception {
        StubExecutor stub = new StubExecutor();
        stub.loopUntilCancel = true;
        try (var engine = new InferenceEngine(stub, 1)) {
            var handle = engine.submit(GenerationRequest.ofTokens(
                    new int[]{1}, 100, 0.0, 0.9, false, 0, null));
            stub.awaitStarted(2, TimeUnit.SECONDS);
            handle.abort();
            try {
                handle.future().get(5, TimeUnit.SECONDS);
                fail("expected cancellation");
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                assertTrue(c instanceof CancellationException, "got " + c);
            }
            assertTrue(stub.steps.get() < 50,
                    "cooperative cancel should stop well before 50 steps, got " + stub.steps.get());
        }
    }

    @Test
    public void testGivenMaxInFlightWhenConfiguredThenExposed() {
        try (var engine = new InferenceEngine(new StubExecutor(), 4)) {
            assertEquals(4, engine.maxInFlight());
        }
    }

    @Test
    public void testGivenStepApiWhenTwoRequestsThenBothComplete() throws Exception {
        StepStub stub = new StepStub();
        try (var engine = new InferenceEngine(stub, 2, 2, 64, 5_000)) {
            var a = engine.submit(GenerationRequest.ofTokens(
                    new int[]{1, 2}, 3, 0.0, 0.9, false, 0, null));
            var b = engine.submit(GenerationRequest.ofTokens(
                    new int[]{3, 4}, 3, 0.0, 0.9, false, 0, null));
            ChatCompletion ca = a.future().get(10, TimeUnit.SECONDS);
            ChatCompletion cb = b.future().get(10, TimeUnit.SECONDS);
            assertEquals(FinishReason.stop, ca.reason());
            assertEquals(FinishReason.stop, cb.reason());
            assertTrue(stub.maxConcurrentBound.get() >= 2,
                    "expected overlapping binds, got " + stub.maxConcurrentBound.get());
            assertTrue(stub.decodeCalls.get() >= 1);
        }
    }

    @Test
    public void testGivenStepApiWhenAbortRunningThenEvicts() throws Exception {
        StepStub stub = new StepStub();
        stub.blockDecode = true;
        try (var engine = new InferenceEngine(stub, 2, 2, 64, 5_000)) {
            var handle = engine.submit(GenerationRequest.ofTokens(
                    new int[]{1, 2}, 50, 0.0, 0.9, false, 0, null));
            stub.awaitDecode(2, TimeUnit.SECONDS);
            handle.abort();
            stub.releaseDecode();
            try {
                handle.future().get(5, TimeUnit.SECONDS);
                fail("expected cancellation");
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                assertTrue(c instanceof CancellationException, "got " + c);
            }
            assertTrue(stub.evicted.get() >= 1);
        }
    }

    @Test
    public void testGivenFairBindWhenMaxInFlightThenCapsPerRequestShare() {
        // numSlots=1000, maxInFlight=4 → soft share page-aligned 240; desired 8000 → 240
        assertEquals(240, InferenceEngine.fairBindCapacity(
                100, 8000, 1000, 1000, 16, 4, 0));
        // After one admit, free=750, remaining admits=3 → fair=240
        assertEquals(240, InferenceEngine.fairBindCapacity(
                100, 8000, 750, 1000, 16, 4, 1));
        // Explicit small max_tokens wins
        assertEquals(164, InferenceEngine.fairBindCapacity(
                100, 164, 1000, 1000, 16, 4, 0));
        // maxInFlight=1 keeps full desired
        assertEquals(8000, InferenceEngine.fairBindCapacity(
                100, 8000, 1000, 1000, 16, 1, 0));
    }

    /**
     * CPU step-API stub that returns peaked logits so greedy sampling is deterministic.
     */
    static final class StepStub implements ModelExecutor {
        final AtomicInteger nextId = new AtomicInteger(1);
        final AtomicInteger bound = new AtomicInteger();
        final AtomicInteger maxConcurrentBound = new AtomicInteger();
        final AtomicInteger decodeCalls = new AtomicInteger();
        final AtomicInteger evicted = new AtomicInteger();
        volatile boolean blockDecode;
        private final Object decodeLock = new Object();
        private boolean decodeStarted;
        private boolean decodeReleased;

        void awaitDecode(long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            synchronized (decodeLock) {
                while (!decodeStarted) {
                    long rem = deadline - System.nanoTime();
                    if (rem <= 0) {
                        throw new InterruptedException("timeout waiting for decode");
                    }
                    decodeLock.wait(rem / 1_000_000L, (int) (rem % 1_000_000L));
                }
            }
        }

        void releaseDecode() {
            synchronized (decodeLock) {
                decodeReleased = true;
                decodeLock.notifyAll();
            }
        }

        private smile.deep.tensor.Tensor peakedLogits(int batch, int tokenId) {
            float[] data = new float[batch * 16];
            for (int b = 0; b < batch; b++) {
                data[b * 16 + tokenId] = 10f;
            }
            return smile.deep.tensor.Tensor.of(data, batch, 16);
        }

        @Override public boolean supportsStepApi() { return true; }
        @Override public LanguageModel model() {
            return new LanguageModel() {
                @Override public String family() { return "test/step"; }
                @Override public String name() { return "step"; }
                @Override public int maxSeqLen() { return 64; }
                @Override public int[] encodeChat(Message... dialog) { return new int[]{1}; }
                @Override
                public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature,
                                               double topp, boolean logprobs, long seed,
                                               GenerationListener listener,
                                               BooleanSupplier cancelRequested) {
                    throw new UnsupportedOperationException();
                }
                @Override
                public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature,
                                           double topp, boolean logprobs, long seed,
                                           GenerationListener listener,
                                           BooleanSupplier cancelRequested) {
                    throw new UnsupportedOperationException();
                }
            };
        }
        @Override public KvCachePool kvCachePool() { return null; }
        @Override public int padToken() { return 0; }
        @Override public int[] stopTokens() { return new int[]{2}; }
        @Override public String decode(int[] tokens) { return "ok"; }
        @Override public String tryDecode(int[] tokens, boolean skipSpecial) { return "ok"; }
        @Override public int bind(int[] prompt, int totalCapacity) {
            int n = bound.incrementAndGet();
            maxConcurrentBound.updateAndGet(m -> Math.max(m, n));
            return nextId.getAndIncrement();
        }
        @Override public int prefixLen(int requestId) { return 0; }
        @Override public smile.deep.tensor.Tensor prefill(int requestId, int[] prompt, int prefixLen) {
            return peakedLogits(1, 9);
        }
        @Override public smile.deep.tensor.Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
            if (to < prompt.length) {
                return null;
            }
            return peakedLogits(1, 9);
        }
        @Override public smile.deep.tensor.Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
            decodeCalls.incrementAndGet();
            synchronized (decodeLock) {
                decodeStarted = true;
                decodeLock.notifyAll();
                while (blockDecode && !decodeReleased) {
                    try {
                        decodeLock.wait(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            return peakedLogits(requestIds.length, 2);
        }
        @Override public void finish(int requestId, int[] sequenceTokens) {
            bound.decrementAndGet();
        }
        @Override public void evict(int requestId) {
            evicted.incrementAndGet();
            bound.decrementAndGet();
        }
    }

    /** Minimal ModelExecutor that does not touch CUDA (generate fallback). */
    static final class StubExecutor implements ModelExecutor {
        volatile boolean blockFirst;
        volatile boolean loopUntilCancel;
        final AtomicInteger steps = new AtomicInteger();
        private final Object lock = new Object();
        private boolean started;
        private boolean released;

        void awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            synchronized (lock) {
                while (!started) {
                    long rem = deadline - System.nanoTime();
                    if (rem <= 0) {
                        throw new InterruptedException("timeout waiting for start");
                    }
                    lock.wait(rem / 1_000_000L, (int) (rem % 1_000_000L));
                }
            }
        }

        void release() {
            synchronized (lock) {
                released = true;
                lock.notifyAll();
            }
        }

        @Override
        public LanguageModel model() {
            return new LanguageModel() {
                @Override public String family() { return "test/stub"; }
                @Override public String name() { return "stub"; }
                @Override public int maxSeqLen() { return 128; }
                @Override public int[] encodeChat(Message... dialog) { return new int[]{1}; }

                @Override
                public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature,
                                               double topp, boolean logprobs, long seed,
                                               GenerationListener listener,
                                               BooleanSupplier cancelRequested) {
                    synchronized (lock) {
                        started = true;
                        lock.notifyAll();
                    }
                    if (loopUntilCancel) {
                        for (int i = 0; i < 10_000; i++) {
                            if (cancelRequested != null && cancelRequested.getAsBoolean()) {
                                throw new CancellationException("aborted");
                            }
                            steps.incrementAndGet();
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new CancellationException("aborted");
                            }
                        }
                        fail("cancel never observed");
                    }
                    if (blockFirst) {
                        synchronized (lock) {
                            while (!released) {
                                try {
                                    lock.wait(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                        blockFirst = false;
                    }
                    if (listener != null) {
                        listener.onInputTokens(prompt.length);
                        listener.onGeneratedTokens(1);
                    }
                    return new ChatCompletion("stub", "ok", prompt, new int[]{9},
                            FinishReason.stop, null);
                }

                @Override
                public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature,
                                           double topp, boolean logprobs, long seed,
                                           GenerationListener listener,
                                           BooleanSupplier cancelRequested) {
                    return generate(encodeChat(dialog), maxGenLen, temperature, topp,
                            logprobs, seed, listener, cancelRequested);
                }
            };
        }

        @Override public KvCachePool kvCachePool() { return null; }
        @Override public int padToken() { return 0; }
        @Override public int[] stopTokens() { return new int[]{2}; }
        @Override public String decode(int[] tokens) { return ""; }
        @Override public String tryDecode(int[] tokens, boolean skipSpecial) { return ""; }

        @Override public boolean supportsStepApi() { return false; }
        @Override public int bind(int[] prompt, int totalCapacity) {
            throw new UnsupportedOperationException();
        }
        @Override public int prefixLen(int requestId) { return 0; }
        @Override public smile.deep.tensor.Tensor prefill(int requestId, int[] prompt, int prefixLen) {
            throw new UnsupportedOperationException();
        }
        @Override public smile.deep.tensor.Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
            throw new UnsupportedOperationException();
        }
        @Override public void finish(int requestId, int[] sequenceTokens) {}
        @Override public void evict(int requestId) {}
    }
}
