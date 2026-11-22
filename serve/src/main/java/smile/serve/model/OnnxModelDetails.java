/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve.model;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.onnx.ModelMetadata;
import smile.serve.OnnxModelInfo;

/**
 * ONNX graph details returned by {@code GET /v1/models/{id}}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OnnxModelDetails(
        String version,
        String defaultVersion,
        List<String> versions,
        Map<String, String> aliases,
        String producerName,
        String domain,
        String graphName,
        String graphDescription,
        String description,
        Long onnxVersion,
        List<OnnxModelInfo.NodeDescriptor> inputs,
        List<OnnxModelInfo.NodeDescriptor> outputs,
        Map<String, String> customMetadata) {

    /**
     * Builds details from ORT metadata, serve info, and registry version summary.
     */
    public static OnnxModelDetails of(ModelMetadata meta,
                                      OnnxModelInfo info,
                                      String defaultVersion,
                                      List<String> versions,
                                      Map<String, String> aliases) {
        return new OnnxModelDetails(
                info.version(),
                defaultVersion,
                versions,
                (aliases == null || aliases.isEmpty()) ? null : aliases,
                meta.producerName(),
                meta.domain(),
                meta.graphName(),
                meta.graphDescription(),
                meta.description(),
                meta.version(),
                info.inputs(),
                info.outputs(),
                meta.customMetadata());
    }
}
