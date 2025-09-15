/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

/**
 * Mathematical vector interface. Vectors are a specialized case of matrices.
 * Specifically, a vector is a matrix with either one row (a row-vector) or
 * one column (a column-vector). Column-vectors are the more common
 * representation and are often simply referred to as vectors.
 *
 * @author Haifeng Li
 */
public abstract class Vector extends DenseMatrix {
    /**
     * Constructor.
     * @param memory the memory segment of data.
     * @param m the number of rows.
     * @param n the number of columns.
     */
    Vector(MemorySegment memory, int m, int n) {
        super(memory, m, n, 1, null, null);
        assert(m == 1 || n == 1);
    }

    /**
     * Returns a slice of vector, which shares the data storage.
     * @param from the initial index of the range to be copied, inclusive.
     * @param to the final index of the range to be copied, exclusive.
     * @return a slice of this vector.
     */
    public abstract Vector slice(int from, int to);

    /**
     * Copies the specified range of the vector into a new vector.
     * The final index of the range may be greater than vector length,
     * in which case 0 is placed in all extra elements.
     * @param from the initial index of the range to be copied, inclusive.
     * @param to the final index of the range to be copied, exclusive.
     *           This index may lie outside the array.
     * @return a copy of this vector.
     */
    public abstract Vector copy(int from, int to);

    /**
     * Returns the number of elements.
     * @return the number of elements.
     */
    public abstract int size();

    /**
     * Sets {@code A[i] = x}.
     * @param i the index.
     * @param x the cell value.
     */
    public abstract void set(int i, double x);

    /**
     * Sets {@code A[i] = x} for Scala users.
     * @param i the index.
     * @param x the cell value.
     */
    public void update(int i, double x) {
        set(i, x);
    }

    /**
     * Returns {@code A[i]}.
     * @param i the row index.
     * @return the cell value.
     */
    public abstract double get(int i);

    /**
     * Returns {@code A[i]} for Scala users.
     * @param i the index.
     * @return the cell value.
     */
    public double apply(int i) {
        return get(i);
    }

    /**
     * Returns a memory segment, at the given offset with the size specified
     * by the given argument.
     * @param array the primitive array backing the heap segment.
     * @param offset the base offset.
     * @param length the length.
     * @return the memory segment.
     */
    static MemorySegment memory(float[] array, int offset, int length) {
        var memory = MemorySegment.ofArray(array);
        if (offset != 0 || length != array.length) {
            long byteSize = memory.byteSize();
            memory = memory.asSlice(offset * byteSize, length * byteSize);
        }
        return memory;
    }

    /**
     * Returns a memory segment, at the given offset with the size specified
     * by the given argument.
     * @param array the primitive array backing the heap segment.
     * @param offset the base offset.
     * @param length the length.
     * @return the memory segment.
     */
    static MemorySegment memory(double[] array, int offset, int length) {
        var memory = MemorySegment.ofArray(array);
        if (offset != 0 || length != array.length) {
            long byteSize = memory.byteSize();
            memory = memory.asSlice(offset * byteSize, length * byteSize);
        }
        return memory;
    }

    /**
     * Creates a column vector.
     * @param array the primitive array backing the vector.
     * @return a column vector.
     */
    public static Vector column(double[] array) {
        return new Vector64(array, 0, array.length);
    }

    /**
     * Creates a column vector.
     * @param array the primitive array backing the vector.
     * @return a column vector.
     */
    public static Vector column(float[] array) {
        return new Vector32(array, 0, array.length);
    }

    /**
     * Creates a row vector.
     * @param array the primitive array backing the vector.
     * @return a row vector.
     */
    public static Vector row(double[] array) {
        return new Vector64(array, 0, array.length);
    }

    /**
     * Creates a row vector.
     * @param array the primitive array backing the vector.
     * @return a row vector.
     */
    public static Vector row(float[] array) {
        return new Vector32(array, 0, array.length);
    }

    /**
     * Returns a zero column vector.
     * @param scalarType the scalar type.
     * @param length the length of vector.
     * @return a zero column vector.
     */
    public static Vector zeros(ScalarType scalarType, int length) {
        return switch (scalarType) {
            case Float64 -> new Vector64(new double[length], 0, length);
            case Float32 -> new Vector32(new float[length], 0, length);
            default -> throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType);
        };
    }

    /**
     * Returns a zero column vector of the same scalar type as this vector.
     * @param length the length of vector.
     * @return a zero column vector.
     */
    public Vector zeros(int length) {
        return zeros(scalarType(), length);
    }

    /**
     * Returns a one column vector.
     * @param scalarType the scalar type.
     * @param length the length of vector.
     * @return a one column vector.
     */
    public static Vector ones(ScalarType scalarType, int length) {
        return switch (scalarType) {
            case Float64 -> {
                double[] array = new double[length];
                Arrays.fill(array, 1.0);
                yield new Vector64(array, 0, length);
            }
            case Float32 -> {
                float[] array = new float[length];
                Arrays.fill(array, 1.0f);
                yield new Vector32(array, 0, length);
            }
            default -> throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType);
        };
    }

    /**
     * Returns a one column vector of the same scalar type as this vector.
     * @param length the length of vector.
     * @return a one column vector.
     */
    public Vector ones(int length) {
        return ones(scalarType(), length);
    }
}
