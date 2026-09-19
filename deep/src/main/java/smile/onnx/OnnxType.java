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
 * The type of an ONNX value, corresponding to {@code ONNXType} in the
 * ONNX Runtime C API.
 *
 * @author Haifeng Li
 */
public enum OnnxType {
    /** Unknown type. */
    UNKNOWN(0),
    /** Dense tensor. */
    TENSOR(1),
    /** Sequence of values. */
    SEQUENCE(2),
    /** Map (key-value pairs). */
    MAP(3),
    /** Opaque type. */
    OPAQUE(4),
    /** Sparse tensor. */
    SPARSE_TENSOR(5),
    /** Optional value. */
    OPTIONAL(6);

    /** The ORT integer code. */
    private final int value;

    /**
     * Constructor.
     * @param value the ORT integer code.
     */
    OnnxType(int value) {
        this.value = value;
    }

    /**
     * Returns the ORT integer code.
     * @return the ORT integer code.
     */
    public int value() {
        return value;
    }

    /**
     * Returns the OnnxType corresponding to the given ORT integer code.
     * @param value the ORT integer code.
     * @return the OnnxType.
     */
    public static OnnxType of(int value) {
        for (OnnxType type : values()) {
            if (type.value == value) return type;
        }
        return UNKNOWN;
    }
}

