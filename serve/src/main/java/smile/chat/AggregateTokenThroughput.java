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
 * <p>Each reported window includes token-weighted mean cache length
 * ({@code prompt + generated so far}) and mean generated-token count so
 * short-context peaks are not confused with long-context steady state.
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
         * @param meanCacheLen    token-weighted mean {@code promptLen + generatedSoFar}.
         * @param meanGenerated   token-weighted mean generated tokens so far.
         */
        void report(double rateTokPerSec, int tokens, double seconds, int activeRequests,
                    double meanCacheLen, double meanGenerated);
    }

    private final long intervalNanos;
    private final Reporter reporter;
    private final AtomicInteger activeRequests = new AtomicInteger();
    private long windowStartNanos;
    private int windowTokens;
    private long windowCacheLenWeighted;
    private long windowGeneratedWeighted;

    /** Creates an aggregator with {@link #DEFAULT_INTERVAL_MS}. */
    public AggregateTokenThroughput() {
        this(DEFAULT_INTERVAL_MS);
    }

    /**
     * @param intervalMs minimum window length before a throughput line may be logged.
     */
    public AggregateTokenThroughput(long intervalMs) {
        this(intervalMs, (rate, tokens, seconds, active, meanCache, meanGen) ->
                logger.infof("Aggregate generation throughput: %.1f tok/s "
                                + "(%d tokens in %.2fs, %d active, meanCacheLen=%.0f, "
                                + "meanGenerated=%.0f)",
                        rate, tokens, seconds, active, meanCache, meanGen));
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
    public void onGeneratedTokens(int count) {
        onGeneratedTokens(count, 0, 0);
    }

    /**
     * Records generated tokens with context length for the contributing request.
     *
     * @param count          newly generated tokens ({@code > 0}).
     * @param cacheLen       {@code promptLen + generatedSoFar} after this update.
     * @param generatedSoFar total generated tokens for that request so far.
     */
    public synchronized void onGeneratedTokens(int count, int cacheLen, int generatedSoFar) {
        if (count <= 0) {
            return;
        }
        long now = System.nanoTime();
        if (windowStartNanos == 0L) {
            windowStartNanos = now;
        }
        windowTokens += count;
        windowCacheLenWeighted += (long) Math.max(0, cacheLen) * count;
        windowGeneratedWeighted += (long) Math.max(0, generatedSoFar) * count;
        long elapsed = now - windowStartNanos;
        if (elapsed >= intervalNanos && windowTokens > 0) {
            reportWindow(windowTokens, elapsed);
            windowStartNanos = now;
            windowTokens = 0;
            windowCacheLenWeighted = 0L;
            windowGeneratedWeighted = 0L;
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
            windowCacheLenWeighted = 0L;
            windowGeneratedWeighted = 0L;
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
        double meanCache = tokens > 0 ? (double) windowCacheLenWeighted / tokens : 0.0;
        double meanGen = tokens > 0 ? (double) windowGeneratedWeighted / tokens : 0.0;
        reporter.report(rate, tokens, seconds, Math.max(0, activeRequests.get()),
                meanCache, meanGen);
    }
}
