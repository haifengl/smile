/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import smile.serve.model.ModelCatalog;
import smile.serve.model.ModelObject;

/**
 * Readiness probe for the serve API server, exposed at {@code /q/health/ready}.
 *
 * <p>Reports UP only when the server can actually answer inference requests,
 * i.e. at least one SMILE or ONNX model is in the {@code ACTIVE} state, or a
 * chat model is loaded. A server whose every model has been unloaded (or is
 * draining) reports DOWN, so load balancers and benchmark harnesses stop
 * routing to it.
 *
 * <p>In dynamic-load mode ({@code smile.serve.allow-empty=true}) the server is
 * ready as soon as it accepts management requests, because clients must be able
 * to reach {@code POST /api/v1/models/load} before any model exists.
 *
 * <p>Active and loaded model counts are surfaced as response data for operators.
 *
 * @author Haifeng Li
 */
@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {
    /** The health check name. */
    static final String NAME = "smile-serve-readiness";

    private final InferenceService inferenceService;
    private final OnnxService onnxService;
    private final ModelCatalog catalog;
    private final InferenceServiceConfig config;

    /**
     * Constructor.
     *
     * @param inferenceService the classic SMILE model service.
     * @param onnxService      the ONNX model service.
     * @param catalog          the aggregated model catalog (includes chat models).
     * @param config           the inference service configuration.
     */
    @Inject
    public ReadinessCheck(InferenceService inferenceService, OnnxService onnxService,
                          ModelCatalog catalog, InferenceServiceConfig config) {
        this.inferenceService = inferenceService;
        this.onnxService = onnxService;
        this.catalog = catalog;
        this.config = config;
    }

    @Override
    public HealthCheckResponse call() {
        long activeSmile = inferenceService.models().values().stream()
                .filter(m -> m.state() == InferenceModel.State.ACTIVE).count();
        long activeOnnx = onnxService.models().values().stream()
                .filter(m -> m.state() == OnnxModel.State.ACTIVE).count();
        long llm = catalog.list().stream()
                .filter(m -> ModelObject.KIND_LLM.equals(m.kind())).count();

        HealthCheckResponseBuilder builder = HealthCheckResponse.named(NAME);
        builder.withData("smile_models", inferenceService.models().size());
        builder.withData("onnx_models", onnxService.models().size());
        builder.withData("active_models", activeSmile + activeOnnx + llm);
        return builder.status(isReady(activeSmile + activeOnnx + llm, config.allowEmpty())).build();
    }

    /**
     * Readiness policy, extracted as a pure function for unit testing.
     *
     * @param activeModels the number of models able to serve requests.
     * @param allowEmpty   whether dynamic-load mode is enabled.
     * @return {@code true} when the server should report UP.
     */
    static boolean isReady(long activeModels, boolean allowEmpty) {
        return activeModels > 0 || allowEmpty;
    }
}
