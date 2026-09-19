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
 * The data type of individual tensor elements, corresponding to
 * {@code ONNXTensorElementDataType} in the ONNX Runtime C API.
 *
 * @author Haifeng Li
 */
public enum ElementType {
    /** Undefined type. */
    UNDEFINED(0),
    /** 32-bit floating point. */
    FLOAT(1),
    /** Unsigned 8-bit integer. */
    UINT8(2),
    /** Signed 8-bit integer. */
    INT8(3),
    /** Unsigned 16-bit integer. */
    UINT16(4),
    /** Signed 16-bit integer. */
    INT16(5),
    /** Signed 32-bit integer. */
    INT32(6),
    /** Signed 64-bit integer. */
    INT64(7),
    /** String. */
    STRING(8),
    /** Boolean. */
    BOOL(9),
    /** 16-bit floating point (IEEE 754). */
    FLOAT16(10),
    /** 64-bit floating point. */
    DOUBLE(11),
    /** Unsigned 32-bit integer. */
    UINT32(12),
    /** Unsigned 64-bit integer. */
    UINT64(13),
    /** 64-bit complex number (real + imaginary float). */
    COMPLEX64(14),
    /** 128-bit complex number (real + imaginary double). */
    COMPLEX128(15),
    /** 16-bit brain floating point (bfloat16). */
    BFLOAT16(16),
    /** 8-bit floating point E4M3FN. */
    FLOAT8E4M3FN(17),
    /** 8-bit floating point E4M3FNUZ. */
    FLOAT8E4M3FNUZ(18),
    /** 8-bit floating point E5M2. */
    FLOAT8E5M2(19),
    /** 8-bit floating point E5M2FNUZ. */
    FLOAT8E5M2FNUZ(20),
    /** 4-bit unsigned integer. */
    UINT4(21),
    /** 4-bit signed integer. */
    INT4(22);

    /** The ORT integer code. */
    private final int value;

    /**
     * Constructor.
     * @param value the ORT integer code.
     */
    ElementType(int value) {
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
     * Returns the ElementType corresponding to the given ORT integer code.
     * @param value the ORT integer code.
     * @return the ElementType.
     */
    public static ElementType of(int value) {
        for (ElementType type : values()) {
            if (type.value == value) return type;
        }
        return UNDEFINED;
    }

    /**
     * Returns the Java type associated with this element type, or
     * {@code null} for types without a direct Java primitive mapping.
     * @return the Java class, or null.
     */
    public Class<?> javaType() {
        return switch (this) {
            case FLOAT       -> float.class;
            case DOUBLE      -> double.class;
            case INT8        -> byte.class;
            case INT16       -> short.class;
            case INT32       -> int.class;
            case INT64       -> long.class;
            case UINT8, BOOL -> byte.class;
            case UINT16      -> short.class;
            case UINT32      -> int.class;
            case UINT64      -> long.class;
            case STRING      -> String.class;
            default          -> null;
        };
    }
}

