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

/**
 * Describes a model input or output node, including its name, ONNX value
 * type, and (for tensor nodes) its tensor type/shape information.
 *
 * @param name       the node name as used in the session run call.
 * @param onnxType   the ONNX value type (e.g. TENSOR, SEQUENCE, MAP).
 * @param tensorInfo tensor type and shape information; non-null only when
 *                   {@code onnxType == OnnxType.TENSOR}.
 *
 * @author Haifeng Li
 */
public record NodeInfo(String name, OnnxType onnxType, TensorInfo tensorInfo) {

    /**
     * Convenience constructor for a tensor node.
     * @param name the node name.
     * @param tensorInfo the tensor type and shape.
     */
    public NodeInfo(String name, TensorInfo tensorInfo) {
        this(name, OnnxType.TENSOR, tensorInfo);
    }

    /**
     * Returns {@code true} if this node carries a tensor value.
     * @return true if this is a tensor node.
     */
    public boolean isTensor() {
        return onnxType == OnnxType.TENSOR;
    }

    @Override
    public String toString() {
        if (isTensor()) {
            return "NodeInfo{name='" + name + "', type=TENSOR, tensorInfo=" + tensorInfo + "}";
        }
        return "NodeInfo{name='" + name + "', type=" + onnxType + "}";
    }
}

