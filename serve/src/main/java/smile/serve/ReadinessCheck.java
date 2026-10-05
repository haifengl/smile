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

/**
 * Readiness probe for the serve API server, exposed at {@code /q/health/ready}.
 *
 * <p>Reports whether the server is ready to accept inference requests. The
 * classic SMILE and ONNX model catalogs are loaded during {@code @Startup}, so
 * by the time this check runs the load has completed; the loaded model counts
 * are surfaced as response data for operators. A server with zero loaded models
 * is still ready — inference requests for unknown ids return HTTP 404.
 *
 * @author Haifeng Li
 */
@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    private final InferenceService inferenceService;
    private final OnnxService onnxService;

    /**
     * Constructor.
     *
     * @param inferenceService the classic SMILE model service.
     * @param onnxService      the ONNX model service.
     */
    @Inject
    public ReadinessCheck(InferenceService inferenceService, OnnxService onnxService) {
        this.inferenceService = inferenceService;
        this.onnxService = onnxService;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("smile-serve-readiness");
        builder.withData("smile_models", inferenceService.models().size());
        builder.withData("onnx_models", onnxService.models().size());
        return builder.up().build();
    }
}
