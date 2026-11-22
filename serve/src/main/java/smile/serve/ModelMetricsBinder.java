/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

/**
 * SPI for external telemetry plugins (such as Prometheus / Micrometer) that
 * observe classic SMILE and ONNX model lifecycle and {@link ModelMetrics}.
 *
 * <p>Implementations are discovered via CDI ({@code @All List<ModelMetricsBinder>});
 * when no telemetry plugin jar is present in {@code serve/plugins/}, model load
 * and unload proceed without external metric registration.
 *
 * @author Haifeng Li
 */
public interface ModelMetricsBinder {
    /** Namespace for classic SMILE models. */
    String SMILE_NAMESPACE = "serve.smile";
    /** Namespace for ONNX models. */
    String ONNX_NAMESPACE = "serve.onnx";

    /**
     * Registers telemetry meters for a loaded model.
     *
     * @param namespace the metric namespace ({@link #SMILE_NAMESPACE} or {@link #ONNX_NAMESPACE}).
     * @param modelId   the model id.
     * @param metrics   the model's live metrics instance.
     */
    void register(String namespace, String modelId, ModelMetrics metrics);

    /**
     * Registers telemetry meters for a versioned model.
     *
     * <p>The default implementation delegates to {@link #register(String, String, ModelMetrics)}
     * with {@code slug + ":" + version} so multiple versions of the same slug do not collide
     * in plugins that only implement the unversioned signature.
     *
     * @param namespace the metric namespace ({@link #SMILE_NAMESPACE} or {@link #ONNX_NAMESPACE}).
     * @param slug      the model slug.
     * @param version   the model version.
     * @param metrics   the model's live metrics instance.
     */
    default void register(String namespace, String slug, String version, ModelMetrics metrics) {
        register(namespace, slug + ":" + version, metrics);
    }

    /**
     * Removes telemetry meters for an unloaded model.
     *
     * @param namespace the metric namespace ({@link #SMILE_NAMESPACE} or {@link #ONNX_NAMESPACE}).
     * @param modelId   the model id.
     */
    void unregister(String namespace, String modelId);

    /**
     * Removes telemetry meters for an unloaded model version.
     *
     * @param namespace the metric namespace ({@link #SMILE_NAMESPACE} or {@link #ONNX_NAMESPACE}).
     * @param slug      the model slug.
     * @param version   the model version.
     */
    default void unregister(String namespace, String slug, String version) {
        unregister(namespace, slug + ":" + version);
    }
}
