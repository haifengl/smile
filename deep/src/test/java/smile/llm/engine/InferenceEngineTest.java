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
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InferenceEngine} queue / abort behavior (no GPU).
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
            // Wait until first is running, then abort the queued second.
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
                        || e instanceof CancellationException
                        || e.getCause() instanceof java.util.concurrent.CancellationException);
            }
        }
    }

    @Test
    public void testGivenMaxInFlightWhenConfiguredThenExposed() {
        try (var engine = new InferenceEngine(new StubExecutor(), 4)) {
            assertEquals(4, engine.maxInFlight());
        }
    }

    /** Minimal ModelExecutor that does not touch CUDA. */
    static final class StubExecutor implements ModelExecutor {
        volatile boolean blockFirst;
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
                                               GenerationListener listener) {
                    if (blockFirst) {
                        synchronized (lock) {
                            started = true;
                            lock.notifyAll();
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
                                           GenerationListener listener) {
                    return generate(encodeChat(dialog), maxGenLen, temperature, topp,
                            logprobs, seed, listener);
                }
            };
        }

        @Override public KvCachePool kvCachePool() { return null; }
        @Override public int padToken() { return 0; }
        @Override public int[] stopTokens() { return new int[]{2}; }
        @Override public String decode(int[] tokens) { return ""; }
        @Override public String tryDecode(int[] tokens, boolean skipSpecial) { return ""; }
    }
}
