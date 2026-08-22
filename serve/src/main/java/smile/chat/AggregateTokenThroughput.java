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
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;

/**
 * Process-wide rolling generation throughput across all in-flight chat
 * requests. Useful for comparing continuous-batching aggregate tok/s to a
 * single-request baseline.
 *
 * @author Haifeng Li
 */
public final class AggregateTokenThroughput {
    private static final Logger logger = Logger.getLogger(AggregateTokenThroughput.class);

    /** Default reporting interval (3 seconds). */
    public static final long DEFAULT_INTERVAL_MS = TokenThroughputLogger.DEFAULT_INTERVAL_MS;

    /** Receives an aggregate throughput sample for logging or tests. */
    @FunctionalInterface
    public interface Reporter {
        /**
         * @param rateTokPerSec   tokens per second over the window.
         * @param tokens          tokens counted in the window.
         * @param seconds         window duration in seconds.
         * @param activeRequests  requests currently contributing tokens.
         */
        void report(double rateTokPerSec, int tokens, double seconds, int activeRequests);
    }

    private final long intervalNanos;
    private final Reporter reporter;
    private final AtomicInteger activeRequests = new AtomicInteger();
    private long windowStartNanos;
    private int windowTokens;

    /** Creates an aggregator with {@link #DEFAULT_INTERVAL_MS}. */
    public AggregateTokenThroughput() {
        this(DEFAULT_INTERVAL_MS);
    }

    /**
     * @param intervalMs minimum window length before a throughput line may be logged.
     */
    public AggregateTokenThroughput(long intervalMs) {
        this(intervalMs, (rate, tokens, seconds, active) ->
                logger.infof("Aggregate generation throughput: %.1f tok/s "
                                + "(%d tokens in %.2fs, %d active requests)",
                        rate, tokens, seconds, active));
    }

    /**
     * @param intervalMs minimum window length before a sample may be reported.
     * @param reporter   sink for aggregate throughput samples.
     */
    public AggregateTokenThroughput(long intervalMs, Reporter reporter) {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("intervalMs must be >= 1");
        }
        if (reporter == null) {
            throw new IllegalArgumentException("reporter must not be null");
        }
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMs);
        this.reporter = reporter;
    }

    /** Marks one chat generation as contributing tokens. */
    public void requestStarted() {
        activeRequests.incrementAndGet();
    }

    /** Marks one chat generation finished (after its final flush). */
    public void requestFinished() {
        int remaining = activeRequests.decrementAndGet();
        if (remaining == 0) {
            flush();
        }
    }

    /**
     * Records generated tokens from any request and may emit an aggregate line.
     *
     * @param count newly generated tokens ({@code > 0}).
     */
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

    /** Flushes any open window (e.g. when the last active request ends). */
    public synchronized void flush() {
        if (windowTokens > 0 && windowStartNanos > 0L) {
            long elapsed = System.nanoTime() - windowStartNanos;
            if (elapsed > 0L) {
                reportWindow(windowTokens, elapsed);
            }
            windowTokens = 0;
            windowStartNanos = 0L;
        }
    }

    /** @return tokens in the current open window (for tests). */
    synchronized int currentWindowTokens() {
        return windowTokens;
    }

    private void reportWindow(int tokens, long elapsedNanos) {
        double seconds = elapsedNanos / 1_000_000_000.0;
        double rate = tokens / seconds;
        reporter.report(rate, tokens, seconds, Math.max(0, activeRequests.get()));
    }
}
