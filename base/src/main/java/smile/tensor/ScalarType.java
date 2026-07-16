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
package smile.tensor;

/** The data type of scalar value. */
public enum ScalarType {
    /**
     * 8-bit quantized unsigned tensor type which represents a compressed
     * floating point tensor.
     */
    QUInt8(1),
    /**
     * 8-bit quantized signed tensor type which represents a compressed
     * floating point tensor.
     */
    QInt8(1),
    /** 8-bit integer. */
    Int8(1),
    /** 16-bit integer. */
    Int16(2),
    /** 32-bit integer. */
    Int32(4),
    /** 64-bit integer. */
    Int64(8),
    /**
     * The bfloat16 (brain floating point) floating-point format occupies 16 bits.
     * This format is a shortened version of the 32-bit IEEE 754 single-precision
     * floating-point format. It preserves the approximate dynamic range of 32-bit
     * floating-point numbers by retaining 8 exponent bits, but supports only an
     * 8-bit precision rather than the 24-bit significand of the single precision.
     */
    BFloat16(2),
    /**
     * Half-precision floating-point number. It contains 5 exponent bits and 11
     * 11-bit precision (10 explicitly stored).
     */
    Float16(2),
    /** Single-precision floating-point number. */
    Float32(4),
    /** Double-precision floating-point number. */
    Float64(8);

    /** The scalar type size in bytes. */
    private final int byteSize;

    /**
     * Constructor.
     * @param byteSize the scalar type size in bytes.
     */
    ScalarType(int byteSize) {
        this.byteSize = byteSize;
    }

    /**
     * Returns the scalar type size in bytes.
     * @return the scalar type size in bytes.
     */
    public int byteSize() { return byteSize; }

    /**
     * Returns true if this is a floating-point type (Float16, BFloat16, Float32, Float64).
     * @return true if floating-point.
     */
    public boolean isFloating() {
        return this == Float16 || this == BFloat16 || this == Float32 || this == Float64;
    }

    /**
     * Returns true if this is an integer type (Int8, Int16, Int32, Int64, QInt8, QUInt8).
     * @return true if integer.
     */
    public boolean isInteger() {
        return this == Int8 || this == Int16 || this == Int32 || this == Int64
                || this == QInt8 || this == QUInt8;
    }

    /**
     * Returns true if this scalar type is compatible with (i.e., identical to) another.
     * @param other the other scalar type.
     * @return true if compatible.
     */
    public boolean isCompatible(ScalarType other) {
        return this == other;
    }
}
