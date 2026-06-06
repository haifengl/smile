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
package smile.deep.tensor;

import java.util.Optional;

/**
 * The data type of the elements stored in the tensor. The enum constants map
 * to the {@code ST_DType} codes exposed by the {@code smile_torch} native API,
 * which in turn mirror {@code torch::ScalarType}.
 */
public enum ScalarType {
    /**
     * 8-bit unsigned integer (maps to PyTorch {@code torch.uint8} / {@code torch.Byte}).
     * Note: PyTorch uses {@code Byte} for the <em>unsigned</em> 8-bit type, so Java
     * {@code byte} values are interpreted in the range [0, 255] when converting to/from
     * this type.
     */
    UInt8(0),
    Int8(1),
    /** 16-bit integer. */
    Int16(2),
    /** 32-bit integer. */
    Int32(3),
    /** 64-bit integer. */
    Int64(4),
    /**
     * Half-precision floating-point number. It contains 5 exponent bits and 11
     * 11-bit precision (10 explicitly stored).
     */
    Float16(5),
    /** Single-precision floating-point number. */
    Float32(6),
    /** Double-precision floating-point number. */
    Float64(7),
    Complex16(8),
    Complex32(9),
    Complex64(10),
    Bool(11),
    /**
     * 8-bit quantized unsigned tensor type which represents a compressed
     * floating point tensor.
     */
    QUInt8(12),
    /**
     * 8-bit quantized signed tensor type which represents a compressed
     * floating point tensor.
     */
    QInt8(13),
    /**
     * 32-bit quantized signed tensor type which represents a compressed
     * floating point tensor.
     */
    QInt32(14),
    /**
     * The bfloat16 (brain floating point) floating-point format occupies 16 bits.
     * This format is a shortened version of the 32-bit IEEE 754 single-precision
     * floating-point format. It preserves the approximate dynamic range of 32-bit
     * floating-point numbers by retaining 8 exponent bits, but supports only an
     * 8-bit precision rather than the 24-bit significand of the single precision.
     */
    BFloat16(15);

    /** The native {@code ST_DType} code. */
    final int code;

    /** Constructor. */
    ScalarType(int code) {
        this.code = code;
    }

    /**
     * Returns the native {@code ST_DType} code.
     * @return the native {@code ST_DType} code.
     */
    public int code() {
        return code;
    }

    /**
     * Returns the scalar type for a native {@code ST_DType} code.
     * @param code the native {@code ST_DType} code.
     * @return the matching scalar type, or empty if the code has no mapping.
     */
    static Optional<ScalarType> of(int code) {
        for (ScalarType dtype : values()) {
            if (dtype.code == code) {
                return Optional.of(dtype);
            }
        }
        return Optional.empty();
    }
}
