/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import smile.llm.FinishReason;
import smile.llm.cache.KvCachePool;
import smile.llm.engine.InferenceEngine;

/**
 * Prometheus meters for LLM model serving, exposed under the
 * {@code serve.llm.*} namespace (rendered as {@code serve_llm_*}).
 *
 * <p>Gauges and function counters read live values from the bound
 * {@link InferenceEngine} and its {@link KvCachePool}; per-request counters and
 * histograms are recorded by {@link LlmMetricsListener}. Every series carries a
 * {@code model_id} label.
 *
 * <p>The chat model can be swapped at runtime, so meters are bound on model load
 * and removed on unload ({@link #bind} / {@link #unbind}). This keeps the
 * {@code model_id} label bounded and avoids stale engine references.
 *
 * <h2>Registration timing</h2>
 * <p>Meters are registered against the Micrometer registry only once the
 * application has started ({@link StartupEvent}). Registering during
 * {@code @Startup} bean construction is unsafe: the composite registry has not
 * yet attached the Prometheus child, and Micrometer does not back-propagate
 * meters to children added later, so the series would be silently missing from
 * {@code /q/metrics}. A {@link #bind} before startup records the desired state
 * and is applied when the application starts.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class LlmMetrics {
    /** Metric namespace; Micrometer renders it as {@code serve_llm_*}. */
    public static final String NAMESPACE = "serve.llm";
    /** Label value used when no chat model is loaded. */
    public static final String UNKNOWN_MODEL = "unknown";
    /**
     * Per-request duration histograms. Names omit the unit: Micrometer's
     * Prometheus convention appends {@code _seconds} to timers, so
     * {@code serve.llm.time_to_first_token} renders as
     * {@code serve_llm_time_to_first_token_seconds}.
     */
    private static final List<String> DURATION_METRICS = List.of(
            "time_to_first_token",
            "inter_token_latency",
            "request_time_per_output_token",
            "e2e_request_latency",
            "request_prefill_time",
            "request_decode_time");

    private final MeterRegistry registry;
    /** Meters for the currently bound model; empty before startup. */
    private volatile List<Meter> bound = List.of();
    /** Model id of the currently bound meters. */
    private volatile String modelId = UNKNOWN_MODEL;
    /** Engine of the currently bound model; {@code null} for the serial GenAI path. */
    private volatile InferenceEngine engine;
    /** Whether the application has started and meters may be registered. */
    private volatile boolean started;

    /**
     * Constructor.
     *
     * @param registry the Micrometer registry.
     */
    @Inject
    public LlmMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Binds the meters to a loaded model, replacing any previous binding.
     *
     * <p>If the application has not started yet, the binding is recorded and
     * applied at {@link StartupEvent}; otherwise the meters are (re)registered
     * immediately.
     *
     * @param modelId the public model id, emitted as the {@code model_id} label.
     * @param engine  the inference engine, or {@code null} for the serial GenAI path.
     */
    public synchronized void bind(String modelId, InferenceEngine engine) {
        this.modelId = (modelId == null || modelId.isBlank()) ? UNKNOWN_MODEL : modelId;
        this.engine = engine;
        if (started) {
            register();
        }
    }

    /**
     * Clears the model binding and re-registers the default (no model loaded)
     * series so a scrape stays deterministic.
     */
    public synchronized void unbind() {
        this.modelId = UNKNOWN_MODEL;
        this.engine = null;
        if (started) {
            register();
        }
    }

    /**
     * Returns the model id of the current binding.
     *
     * @return the bound model id, or {@link #UNKNOWN_MODEL}.
     */
    public String modelId() {
        return modelId;
    }

    /**
     * Registers the meters once the application has started.
     *
     * @param event the startup event.
     */
    void onStart(@Observes StartupEvent event) {
        start();
    }

    /**
     * Marks the application as started and registers the current binding.
     * Package-private so tests can drive registration without a Quarkus runtime.
     */
    synchronized void start() {
        started = true;
        register();
    }

    private void register() {
        bound.forEach(registry::remove);
        bound = List.of();

        Tags tags = Tags.of("model_id", modelId);
        List<Meter> meters = new ArrayList<>();
        InferenceEngine eng = engine;
        if (eng != null) {
            KvCachePool pool = eng.kvCachePool();
            meters.add(Gauge.builder(NAMESPACE + ".num_requests_running", eng, InferenceEngine::inFlight)
                    .description("Number of chat requests currently running").tags(tags).register(registry));
            meters.add(Gauge.builder(NAMESPACE + ".num_requests_waiting", eng, InferenceEngine::queueSize)
                    .description("Number of chat requests waiting for admission").tags(tags).register(registry));
            if (pool != null) {
                meters.add(Gauge.builder(NAMESPACE + ".kv_cache_usage_ratio", pool, LlmMetrics::kvUsageRatio)
                        .description("Fraction of KV cache pages in use").tags(tags).register(registry));
                meters.add(FunctionCounter.builder(NAMESPACE + ".prefix_cache_queries_total", pool,
                                KvCachePool::prefixPromptTokens)
                        .description("Total prompt tokens offered to the prefix cache").tags(tags).register(registry));
                meters.add(FunctionCounter.builder(NAMESPACE + ".prefix_cache_hits_total", pool,
                                KvCachePool::prefixMatchTokens)
                        .description("Total prompt tokens served from the prefix cache").tags(tags).register(registry));
            }
        }
        // Per-request meters are registered eagerly so the series exist (at zero)
        // before the first request and are visible to a scrape immediately.
        meters.add(Counter.builder(NAMESPACE + ".prompt_tokens_total")
                .description("Total prompt tokens processed").tags(tags).register(registry));
        meters.add(Counter.builder(NAMESPACE + ".generation_tokens_total")
                .description("Total generated tokens").tags(tags).register(registry));
        meters.add(DistributionSummary.builder(NAMESPACE + ".request_prompt_tokens")
                .description("Prompt token count per request").tags(tags).register(registry));
        meters.add(DistributionSummary.builder(NAMESPACE + ".request_generation_tokens")
                .description("Generated token count per request").tags(tags).register(registry));
        for (String name : DURATION_METRICS) {
            meters.add(Timer.builder(NAMESPACE + "." + name)
                    .description("Chat request " + name.replace('_', ' ')).tags(tags).register(registry));
        }
        for (FinishReason reason : FinishReason.values()) {
            meters.add(Counter.builder(NAMESPACE + ".request_success_total")
                    .description("Successful chat requests by finish reason")
                    .tags(Tags.of("model_id", modelId, "finished_reason", reason.name()))
                    .register(registry));
        }
        bound = List.copyOf(meters);
    }

    private static double kvUsageRatio(KvCachePool pool) {
        int pageSize = pool.pageSize();
        if (pageSize <= 0) {
            return 0.0;
        }
        int numPages = pool.numSlots() / pageSize;
        if (numPages <= 0) {
            return 0.0;
        }
        return 1.0 - (double) pool.freePages() / numPages;
    }

    private Tags tags() {
        return Tags.of("model_id", modelId);
    }

    /**
     * Records the prompt token count for a completed request.
     *
     * @param tokens prompt token count.
     */
    void recordPromptTokens(int tokens) {
        if (tokens <= 0) {
            return;
        }
        Counter.builder(NAMESPACE + ".prompt_tokens_total")
                .description("Total prompt tokens processed").tags(tags()).register(registry)
                .increment(tokens);
        DistributionSummary.builder(NAMESPACE + ".request_prompt_tokens")
                .description("Prompt token count per request").tags(tags()).register(registry)
                .record(tokens);
    }

    /**
     * Records the generated token count for a completed request.
     *
     * @param tokens generated token count.
     */
    void recordGenerationTokens(int tokens) {
        if (tokens <= 0) {
            return;
        }
        Counter.builder(NAMESPACE + ".generation_tokens_total")
                .description("Total generated tokens").tags(tags()).register(registry)
                .increment(tokens);
        DistributionSummary.builder(NAMESPACE + ".request_generation_tokens")
                .description("Generated token count per request").tags(tags()).register(registry)
                .record(tokens);
    }

    /**
     * Records a successful request, tagged by finish reason.
     *
     * @param reason the finish reason.
     */
    void recordSuccess(FinishReason reason) {
        String value = reason == null ? "unknown" : reason.name();
        Counter.builder(NAMESPACE + ".request_success_total")
                .description("Successful chat requests by finish reason")
                .tags(Tags.of("model_id", modelId, "finished_reason", value))
                .register(registry)
                .increment();
    }

    /**
     * Records a duration sample for one of the per-request histograms.
     *
     * @param name  the metric name suffix without the unit (e.g.
     *              {@code time_to_first_token}); Micrometer appends
     *              {@code _seconds} in the Prometheus rendering.
     * @param nanos the duration in nanoseconds.
     */
    void recordDuration(String name, long nanos) {
        if (nanos < 0) {
            return;
        }
        Timer.builder(NAMESPACE + "." + name)
                .description("Chat request " + name.replace('_', ' '))
                .tags(tags())
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }
}
