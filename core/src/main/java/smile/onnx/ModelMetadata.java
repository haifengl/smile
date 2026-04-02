/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.onnx;

import java.util.Map;

/**
 * Metadata associated with an ONNX model. This includes producer information,
 * graph description, domain, version, and any user-defined custom metadata
 * key-value pairs embedded in the model.
 *
 * @param producerName  the tool or framework that produced the model.
 * @param graphName     the name of the main computation graph.
 * @param graphDescription a human-readable description of the graph.
 * @param domain        the model domain (e.g. {@code "ai.onnx"}).
 * @param description   a human-readable description of the model.
 * @param version       the model version integer.
 * @param customMetadata a map of user-defined string key-value pairs stored
 *                       in the model.
 *
 * @author Haifeng Li
 */
public record ModelMetadata(
        String producerName,
        String graphName,
        String graphDescription,
        String domain,
        String description,
        long version,
        Map<String, String> customMetadata) {

    @Override
    public String toString() {
        return "ModelMetadata{" +
                "producerName='" + producerName + '\'' +
                ", graphName='" + graphName + '\'' +
                ", domain='" + domain + '\'' +
                ", version=" + version +
                '}';
    }
}

