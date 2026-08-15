/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.onnx.ModelMetadata;
import smile.serve.OnnxModelInfo;

/**
 * ONNX graph details returned by {@code GET /models/{id}}.
 *
 * @param producerName     tool/framework that produced the model.
 * @param domain           model domain (e.g. {@code ai.onnx}).
 * @param graphName        main graph name.
 * @param graphDescription graph description from the file.
 * @param description      model description from the file.
 * @param version          embedded model version integer.
 * @param inputs           input node descriptors.
 * @param outputs          output node descriptors.
 * @param customMetadata   user-defined string pairs from the ONNX file.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OnnxModelDetails(
        String producerName,
        String domain,
        String graphName,
        String graphDescription,
        String description,
        Long version,
        List<OnnxModelInfo.NodeDescriptor> inputs,
        List<OnnxModelInfo.NodeDescriptor> outputs,
        Map<String, String> customMetadata) {

    /**
     * Builds details from ORT metadata and the serve info DTO.
     *
     * @param meta ORT model metadata.
     * @param info serve ONNX info (inputs/outputs).
     * @return onnx details for retrieve responses.
     */
    public static OnnxModelDetails of(ModelMetadata meta, OnnxModelInfo info) {
        return new OnnxModelDetails(
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
