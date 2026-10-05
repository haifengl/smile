/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Runtime telemetry and operational metrics for an inference model.
 *
 * @author Haifeng Li
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelMetrics {
    private final long loadedAtEpochSeconds;
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder totalExecutionTimeNanos = new LongAdder();
    private final LongAdder minLatencyNanos = new LongAdder();
    private final LongAdder maxLatencyNanos = new LongAdder();

    public ModelMetrics() {
        this.loadedAtEpochSeconds = Instant.now().getEpochSecond();
    }

    /**
     * Marks the beginning of an inference execution.
     *
     * @return start timestamp in nanoseconds.
     */
    public long startExecution() {
        inFlightRequests.incrementAndGet();
        return System.nanoTime();
    }

    /**
     * Records completion of an inference execution.
     *
     * @param startNanos start timestamp from {@link #startExecution()}.
     * @param success    whether the execution completed without error.
     */
    public void finishExecution(long startNanos, boolean success) {
        long durationNanos = Math.max(0L, System.nanoTime() - startNanos);
        inFlightRequests.decrementAndGet();
        totalRequests.increment();
        if (!success) {
            failedRequests.increment();
        }
        totalExecutionTimeNanos.add(durationNanos);

        updateMinMax(durationNanos);
    }

    private void updateMinMax(long durationNanos) {
        long currentMin = minLatencyNanos.sum();
        if (currentMin == 0 || durationNanos < currentMin) {
            minLatencyNanos.reset();
            minLatencyNanos.add(durationNanos);
        }
        long currentMax = maxLatencyNanos.sum();
        if (durationNanos > currentMax) {
            maxLatencyNanos.reset();
            maxLatencyNanos.add(durationNanos);
        }
    }

    @JsonProperty("loaded_at")
    public long loadedAt() {
        return loadedAtEpochSeconds;
    }

    @JsonProperty("uptime_seconds")
    public long uptimeSeconds() {
        return Math.max(0, Instant.now().getEpochSecond() - loadedAtEpochSeconds);
    }

    @JsonProperty("in_flight_requests")
    public int inFlightRequests() {
        return inFlightRequests.get();
    }

    @JsonProperty("total_requests")
    public long totalRequests() {
        return totalRequests.sum();
    }

    @JsonProperty("failed_requests")
    public long failedRequests() {
        return failedRequests.sum();
    }

    @JsonProperty("successful_requests")
    public long successfulRequests() {
        return Math.max(0, totalRequests.sum() - failedRequests.sum());
    }

    @JsonProperty("mean_latency_ms")
    public double meanLatencyMs() {
        long count = totalRequests.sum();
        if (count == 0) return 0.0;
        return (totalExecutionTimeNanos.sum() / (double) count) / 1_000_000.0;
    }

    @JsonProperty("min_latency_ms")
    public double minLatencyMs() {
        long min = minLatencyNanos.sum();
        return min == 0 ? 0.0 : min / 1_000_000.0;
    }

    @JsonProperty("max_latency_ms")
    public double maxLatencyMs() {
        return maxLatencyNanos.sum() / 1_000_000.0;
    }

    @JsonProperty("throughput_qps")
    public double throughputQps() {
        long uptime = uptimeSeconds();
        if (uptime <= 0) return totalRequests.sum();
        return (double) totalRequests.sum() / uptime;
    }
}
