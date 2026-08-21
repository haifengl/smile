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
package smile.chat;

import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import smile.llm.GenerationListener;

/**
 * Logs rolling generation throughput ({@code tok/s}) about every
 * {@link #DEFAULT_INTERVAL_MS} milliseconds while tokens are being produced.
 *
 * <p>Logging is driven by token events (no background timer): a line is emitted
 * only when at least one token arrived in the current window and the window
 * duration has reached the interval.
 *
 * @author Haifeng Li
 */
public final class TokenThroughputLogger implements GenerationListener {
    private static final Logger logger = Logger.getLogger(TokenThroughputLogger.class);

    /** Default reporting interval (3 seconds). */
    public static final long DEFAULT_INTERVAL_MS = 3_000L;

    /** Receives a throughput sample for logging or tests. */
    @FunctionalInterface
    public interface Reporter {
        /**
         * @param rateTokPerSec tokens per second over the window.
         * @param tokens        tokens counted in the window.
         * @param seconds       window duration in seconds.
         */
        void report(double rateTokPerSec, int tokens, double seconds);
    }

    private final long intervalNanos;
    private final Reporter reporter;
    private long windowStartNanos;
    private int windowTokens;

    /**
     * Creates a logger with {@link #DEFAULT_INTERVAL_MS}.
     */
    public TokenThroughputLogger() {
        this(DEFAULT_INTERVAL_MS);
    }

    /**
     * Creates a logger with a custom interval.
     *
     * @param intervalMs minimum window length before a throughput line may be logged.
     */
    public TokenThroughputLogger(long intervalMs) {
        this(intervalMs, (rate, tokens, seconds) ->
                logger.infof("Generation throughput: %.1f tok/s (%d tokens in %.2fs)",
                        rate, tokens, seconds));
    }

    /**
     * Creates a logger with a custom interval and reporter (for tests).
     *
     * @param intervalMs minimum window length before a sample may be reported.
     * @param reporter   sink for throughput samples.
     */
    public TokenThroughputLogger(long intervalMs, Reporter reporter) {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("intervalMs must be >= 1");
        }
        if (reporter == null) {
            throw new IllegalArgumentException("reporter must not be null");
        }
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMs);
        this.reporter = reporter;
    }

    @Override
    public synchronized void onGeneratedTokens(int count) {
        if (count <= 0) {
            return;
        }
        long now = System.nanoTime();
        if (windowStartNanos == 0L) {
            windowStartNanos = now;
        }
        windowTokens += count;

        long elapsed = now - windowStartNanos;
        if (elapsed >= intervalNanos && windowTokens > 0) {
            reportWindow(windowTokens, elapsed);
            windowStartNanos = now;
            windowTokens = 0;
        }
    }

    /**
     * Flushes a final throughput line for any tokens still in the open window.
     * No-op when the window is empty.
     */
    public synchronized void finish() {
        if (windowTokens > 0 && windowStartNanos > 0L) {
            long elapsed = System.nanoTime() - windowStartNanos;
            if (elapsed > 0L) {
                reportWindow(windowTokens, elapsed);
            }
            windowTokens = 0;
        }
    }

    private void reportWindow(int tokens, long elapsedNanos) {
        double seconds = elapsedNanos / 1_000_000_000.0;
        double rate = tokens / seconds;
        reporter.report(rate, tokens, seconds);
    }
}
