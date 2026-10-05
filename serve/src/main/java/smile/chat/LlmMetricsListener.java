/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import smile.llm.GenerationListener;

/**
 * Per-request {@link GenerationListener} that feeds LLM serving metrics.
 *
 * <p>One instance is created per chat request and composed into the listener
 * chain in {@link ChatService#submitCompletion}. It captures the timing and
 * token counts that only the generation callbacks can observe — time to first
 * token, inter-token latency, and prompt/generated token counts — and exposes
 * them so the completion hook can record the remaining end-to-end metrics.
 *
 * <p>Callbacks arrive on the engine worker thread; the counters are atomic so
 * the completion hook can read them safely.
 *
 * @author Haifeng Li
 */
public final class LlmMetricsListener implements GenerationListener {
    private final LlmMetrics metrics;
    private final long startNanos;
    private final AtomicInteger promptTokens = new AtomicInteger();
    private final AtomicInteger cachedInputTokens = new AtomicInteger();
    private final AtomicInteger generatedTokens = new AtomicInteger();
    private final AtomicLong firstTokenNanos = new AtomicLong(-1L);
    private final AtomicLong lastTokenNanos = new AtomicLong(-1L);

    /**
     * Constructor.
     *
     * @param metrics the LLM metrics sink.
     */
    public LlmMetricsListener(LlmMetrics metrics) {
        this.metrics = metrics;
        this.startNanos = System.nanoTime();
    }

    @Override
    public void onInputTokens(int count) {
        if (count >= 0) {
            promptTokens.set(count);
        }
    }

    @Override
    public void onCachedInputTokens(int count) {
        if (count >= 0) {
            cachedInputTokens.set(count);
        }
    }

    @Override
    public void onGeneratedTokens(int count) {
        if (count <= 0) {
            return;
        }
        long now = System.nanoTime();
        if (firstTokenNanos.compareAndSet(-1L, now)) {
            metrics.recordDuration("time_to_first_token", now - startNanos);
        } else {
            long previous = lastTokenNanos.get();
            if (previous > 0L) {
                metrics.recordDuration("inter_token_latency", now - previous);
            }
        }
        lastTokenNanos.set(now);
        generatedTokens.addAndGet(count);
    }

    /** @return prompt token count reported for this request. */
    public int promptTokens() {
        return promptTokens.get();
    }

    /** @return input tokens served from the prefix cache. */
    public int cachedInputTokens() {
        return cachedInputTokens.get();
    }

    /** @return generated token count so far. */
    public int generatedTokens() {
        return generatedTokens.get();
    }

    /** @return nanoseconds from request start to the first generated token, or {@code -1}. */
    public long timeToFirstTokenNanos() {
        long first = firstTokenNanos.get();
        return first < 0L ? -1L : first - startNanos;
    }

    /** @return nanoseconds from request start to now. */
    public long elapsedNanos() {
        return System.nanoTime() - startNanos;
    }
}
