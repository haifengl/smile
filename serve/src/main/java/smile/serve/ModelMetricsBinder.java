/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Registers and removes Micrometer meters for classic SMILE and ONNX models.
 *
 * <p>Models are loaded and unloaded dynamically, so meters are bound on load
 * and removed on unload. This keeps the {@code model_id} label bounded and
 * prevents stale time series from lingering after a model is gone. The meters
 * read from the model's existing {@link ModelMetrics} instance, which remains
 * the single source of truth for the counters.
 *
 * <p>Metric names use the {@code serve.smile.*} / {@code serve.onnx.*}
 * namespaces; Micrometer's Prometheus naming convention renders them as
 * {@code serve_smile_*} / {@code serve_onnx_*} (counters gain a {@code _total}
 * suffix).
 *
 * <h2>Registration timing</h2>
 * <p>Models are loaded during {@code @Startup} bean construction, before the
 * composite Micrometer registry has attached the Prometheus child. Registering
 * then would silently drop the series from {@code /q/metrics}, so registrations
 * are buffered and flushed at {@link StartupEvent}.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class ModelMetricsBinder {
    /** Namespace for classic SMILE models. */
    public static final String SMILE_NAMESPACE = "serve.smile";
    /** Namespace for ONNX models. */
    public static final String ONNX_NAMESPACE = "serve.onnx";

    private final MeterRegistry registry;
    /** Registered meters keyed by {@code namespace|modelId}. */
    private final Map<String, List<Meter>> registered = new LinkedHashMap<>();
    /** Registrations requested before startup, keyed by {@code namespace|modelId}. */
    private final Map<String, ModelMetrics> pending = new LinkedHashMap<>();
    /** Whether the application has started and meters may be registered. */
    private boolean started;

    /**
     * Constructor.
     *
     * @param registry the Micrometer registry.
     */
    @Inject
    public ModelMetricsBinder(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Registers the meters for a model. Idempotent: a second call for the same
     * namespace and model id is a no-op. Before startup the registration is
     * buffered and applied at {@link StartupEvent}.
     *
     * @param namespace the metric namespace ({@link #SMILE_NAMESPACE} or {@link #ONNX_NAMESPACE}).
     * @param modelId   the model id, emitted as the {@code model_id} label.
     * @param metrics   the model's live metrics instance.
     */
    public synchronized void register(String namespace, String modelId, ModelMetrics metrics) {
        String key = key(namespace, modelId);
        if (!started) {
            pending.putIfAbsent(key, metrics);
            return;
        }
        if (registered.containsKey(key)) {
            return;
        }
        registered.put(key, createMeters(namespace, modelId, metrics));
    }

    /**
     * Removes the meters for a model, if registered. Also drops any buffered
     * registration so a model unloaded before startup is not re-registered.
     *
     * @param namespace the metric namespace.
     * @param modelId   the model id.
     */
    public synchronized void unregister(String namespace, String modelId) {
        String key = key(namespace, modelId);
        pending.remove(key);
        List<Meter> meters = registered.remove(key);
        if (meters != null) {
            meters.forEach(registry::remove);
        }
    }

    /**
     * Flushes buffered registrations once the application has started.
     *
     * @param event the startup event.
     */
    void onStart(@Observes StartupEvent event) {
        start();
    }

    /**
     * Marks the application as started and flushes buffered registrations.
     * Package-private so tests can drive registration without a Quarkus runtime.
     */
    synchronized void start() {
        started = true;
        pending.forEach((key, metrics) -> {
            if (!registered.containsKey(key)) {
                int sep = key.indexOf('|');
                registered.put(key, createMeters(key.substring(0, sep), key.substring(sep + 1), metrics));
            }
        });
        pending.clear();
    }

    private List<Meter> createMeters(String namespace, String modelId, ModelMetrics metrics) {
        Tags tags = Tags.of("model_id", modelId);
        List<Meter> meters = new ArrayList<>();
        meters.add(FunctionCounter.builder(namespace + ".requests", metrics, ModelMetrics::totalRequests)
                .description("Total inference requests").tags(tags).register(registry));
        meters.add(FunctionCounter.builder(namespace + ".failed_requests", metrics, ModelMetrics::failedRequests)
                .description("Failed inference requests").tags(tags).register(registry));
        meters.add(Gauge.builder(namespace + ".successful_requests", metrics, ModelMetrics::successfulRequests)
                .description("Successful inference requests").tags(tags).register(registry));
        meters.add(Gauge.builder(namespace + ".in_flight_requests", metrics, ModelMetrics::inFlightRequests)
                .description("In-flight inference requests").tags(tags).register(registry));
        meters.add(Gauge.builder(namespace + ".uptime_seconds", metrics, ModelMetrics::uptimeSeconds)
                .description("Seconds since the model was loaded").tags(tags).register(registry));
        meters.add(Gauge.builder(namespace + ".throughput_requests_per_second", metrics, ModelMetrics::throughputQps)
                .description("Average requests per second since load").tags(tags).register(registry));
        meters.add(Gauge.builder(namespace + ".mean_latency_seconds", metrics, m -> m.meanLatencyMs() / 1000.0)
                .description("Mean inference latency in seconds").tags(tags).register(registry));
        meters.add(Gauge.builder(namespace + ".min_latency_seconds", metrics, m -> m.minLatencyMs() / 1000.0)
                .description("Minimum inference latency in seconds").tags(tags).register(registry));
        meters.add(Gauge.builder(namespace + ".max_latency_seconds", metrics, m -> m.maxLatencyMs() / 1000.0)
                .description("Maximum inference latency in seconds").tags(tags).register(registry));
        return meters;
    }

    private static String key(String namespace, String modelId) {
        return namespace + "|" + modelId;
    }
}
