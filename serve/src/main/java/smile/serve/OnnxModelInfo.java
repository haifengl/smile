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
package smile.serve;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import smile.onnx.NodeInfo;
import smile.onnx.TensorInfo;

/**
 * Immutable metadata describing a loaded ONNX model exposed by
 * {@link OnnxResource}.
 *
 * @param id          the model identifier derived from the file name.
 * @param graphName   the ONNX graph name embedded in the model file.
 * @param description a human-readable description embedded in the model file.
 * @param version     the model version integer embedded in the model file.
 * @param inputs      ordered list of input node descriptors.
 * @param outputs     ordered list of output node descriptors.
 * @param customMeta  user-defined key-value pairs embedded in the model file.
 *
 * @author Haifeng Li
 */
public record OnnxModelInfo(String id,
                             String graphName,
                             String description,
                             long version,
                             List<NodeDescriptor> inputs,
                             List<NodeDescriptor> outputs,
                             Map<String, String> customMeta) {

    /**
     * Describes a single input or output node of the ONNX graph.
     *
     * @param name       the node name used in run calls.
     * @param onnxType   the ONNX value type (e.g. {@code "TENSOR"}).
     * @param elementType the tensor element type (e.g. {@code "FLOAT"}),
     *                   or {@code null} for non-tensor nodes.
     * @param shape      the tensor shape dimensions; {@code -1} means
     *                   dynamic; {@code null} for non-tensor nodes.
     */
    public record NodeDescriptor(String name,
                                  String onnxType,
                                  String elementType,
                                  long[] shape) {

        /** Builds a NodeDescriptor from a {@link NodeInfo}. */
        static NodeDescriptor of(NodeInfo info) {
            TensorInfo ti = info.tensorInfo();
            return new NodeDescriptor(
                    info.name(),
                    info.onnxType().name(),
                    ti != null ? ti.elementType().name() : null,
                    ti != null ? ti.shape() : null);
        }

        @Override
        public String toString() {
            return "NodeDescriptor{name='" + name + "', onnxType=" + onnxType
                    + ", elementType=" + elementType
                    + ", shape=" + Arrays.toString(shape) + "}";
        }
    }
}

