/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat;

import smile.llm.FinishReason;
import smile.llm.engine.InferenceEngine;

/**
 * SPI for external telemetry plugins (such as Prometheus / Micrometer) that
 * observe LLM model serving metrics.
 *
 * <p>Implementations are discovered via CDI ({@code @All List<LlmMetrics>});
 * when no telemetry plugin jar is present in {@code serve/plugins/}, chat
 * completions run without external metric registration.
 *
 * @author Haifeng Li
 */
public interface LlmMetrics {
    /** Metric namespace ({@code serve.llm}). */
    String NAMESPACE = "serve.llm";
    /** Label value used when no chat model is loaded. */
    String UNKNOWN_MODEL = "unknown";

    /**
     * Binds telemetry meters to a loaded chat model.
     *
     * @param modelId the public model id.
     * @param engine  the inference engine, or {@code null} for the serial GenAI path.
     */
    void bind(String modelId, InferenceEngine engine);

    /**
     * Clears the current chat model binding.
     */
    void unbind();

    /**
     * Records the prompt token count for a completed request.
     *
     * @param tokens prompt token count.
     */
    void recordPromptTokens(int tokens);

    /**
     * Records the generated token count for a completed request.
     *
     * @param tokens generated token count.
     */
    void recordGenerationTokens(int tokens);

    /**
     * Records a successful request, tagged by finish reason.
     *
     * @param reason the finish reason.
     */
    void recordSuccess(FinishReason reason);

    /**
     * Records a duration sample for one of the per-request histograms.
     *
     * @param name  the metric name suffix without the unit (e.g. {@code time_to_first_token}).
     * @param nanos the duration in nanoseconds.
     */
    void recordDuration(String name, long nanos);
}
