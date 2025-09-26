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
import static smile.linalg.blas.cblas_openblas_h.*;

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
        super(memory, m, n, m, null, null);
        assert(m == 1 || n == 1);
    }

    /**
     * Returns the number of elements.
     * @return the number of elements.
     */
    public abstract int size();

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
     * Sets {@code A[i] += x}.
     * @param i the index.
     * @param x the operand.
     */
    public abstract void add(int i, double x);

    /**
     * Sets {@code A[i] -= x}.
     * @param i the index.
     * @param x the operand.
     */
    public abstract void sub(int i, double x);

    /**
     * Sets {@code A[i] *= x}.
     * @param i the index.
     * @param x the operand.
     */
    public abstract void mul(int i, double x);

    /**
     * Sets {@code A[i] /= x}.
     * @param i the index.
     * @param x the operand.
     */
    public abstract void div(int i, double x);

    /**
     * Returns a slice of vector, which shares the data storage.
     * @param from the initial index of the range to be copied, inclusive.
     * @param to the final index of the range to be copied, exclusive.
     * @return a slice of this vector.
     */
    public abstract Vector slice(int from, int to);

    @Override
    public Vector copy() {
        return copy(0, size());
    }

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
     * Copies the specified range of the vector into another vector.
     * @param pos starting position in this vector.
     * @param dest the destination vector.
     * @param destPos starting position in the destination vector.
     * @param length the number of vector elements to be copied.
     */
    public abstract void copy(int pos, Vector dest, int destPos, int length);

    /**
     * Returns an array containing all the elements in this vector.
     * @param a the array into which the elements of the vector are to be stored
     *          if it is big enough; otherwise, a new array is allocated.
     * @return an array containing the elements of the vector.
     */
    public abstract double[] toArray(double[] a);

    /**
     * Returns an array containing all the elements in this vector.
     * @param a the array into which the elements of the vector are to be stored,
     *          if it is big enough; otherwise, a new array is allocated.
     * @return an array containing the elements of the vector.
     */
    public abstract float[] toArray(float[] a);

    /**
     * Assigns the specified value to each element of the specified vector.
     * @param value the value to be stored in all elements of the vector.
     */
    public abstract void fill(double value);

    /**
     * Assigns the specified value to each element of the specified range of
     * the specified vector.
     * @param from the index of the first element (inclusive) to be filled
     *             with the specified value.
     * @param to the index of the last element (exclusive) to be filled
     *           with the specified value.
     * @param value the value to be stored in specified range of the vector.
     */
    public abstract void fill(int from, int to, double value);

    /**
     * Returns the matrix with the elements of this vector as the diagonal.
     * @return the diagonal matrix.
     */
    public DenseMatrix diagflat() {
        int n = size();
        DenseMatrix matrix = DenseMatrix.zeros(scalarType(), n, n);
        for (int i = 0; i < n; i++) {
            matrix.set(i, i, get(i));
        }
        return matrix;
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
        return Vector64.column(array, 0, array.length);
    }

    /**
     * Creates a column vector.
     * @param array the primitive array backing the vector.
     * @return a column vector.
     */
    public static Vector column(float[] array) {
        return Vector32.column(array, 0, array.length);
    }

    /**
     * Creates a row vector.
     * @param array the primitive array backing the vector.
     * @return a row vector.
     */
    public static Vector row(double[] array) {
        return Vector64.row(array, 0, array.length);
    }

    /**
     * Creates a row vector.
     * @param array the primitive array backing the vector.
     * @return a row vector.
     */
    public static Vector row(float[] array) {
        return Vector32.row(array, 0, array.length);
    }

    /**
     * Returns a zero column vector.
     * @param scalarType the scalar type.
     * @param length the length of vector.
     * @return a zero column vector.
     */
    public static Vector zeros(ScalarType scalarType, int length) {
        return switch (scalarType) {
            case Float64 -> column(new double[length]);
            case Float32 -> column(new float[length]);
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
                yield column(array);
            }
            case Float32 -> {
                float[] array = new float[length];
                Arrays.fill(array, 1.0f);
                yield column(array);
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

    /**
     * Sums the absolute values of the elements of a vector.
     * @return Sum of the absolute values of the elements of the vector x.
     */
    public double asum() {
        return switch(scalarType()) {
            case Float64 -> cblas_dasum(size(), memory, 1);
            case Float32 -> cblas_sasum(size(), memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };
    }

    /**
     * Computes a constant alpha times a vector x plus a vector y.
     * The result overwrites the initial values of vector y.
     *
     * @param alpha If {@code alpha = 0} this routine returns without any computation.
     *
     * @param y Input and output vector.
     *          Before calling the routine, y contains the vector to be summed.
     *          After the routine ends, y contains the result of the summation.
     */
    public void axpy(double alpha, Vector y) {
        if (scalarType() != y.scalarType()) {
            throw new UnsupportedOperationException("Different scala type: " + scalarType() + " != " + y.scalarType());
        }
        if (size() != y.size()) {
            throw new UnsupportedOperationException("Different vector size: " + size() + " != "  + y.size());
        }

        switch(scalarType()) {
            case Float64 -> cblas_daxpy(size(), alpha, memory, 1, y.memory, 1);
            case Float32 -> cblas_saxpy(size(), (float) alpha, memory, 1, y.memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }
    }

    /**
     * Computes the dot product of two vectors.
     *
     * @param y Input vector contains the second vector operand.
     *
     * @return dot product.
     */
    public double dot(Vector y) {
        if (scalarType() != y.scalarType()) {
            throw new UnsupportedOperationException("Different scala type: " + scalarType() + " != " + y.scalarType());
        }
        if (size() != y.size()) {
            throw new UnsupportedOperationException("Different vector size: " + size() + " != "  + y.size());
        }

        return switch(scalarType()) {
            case Float64 -> cblas_ddot(size(), memory, 1, y.memory, 1);
            case Float32 -> cblas_sdot(size(), memory, 1, y.memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };
    }

    /**
     * Computes the Euclidean (L2) norm of a vector.
     *
     * @return Euclidean norm.
     */
    public double norm2() {
        return switch(scalarType()) {
            case Float64 -> cblas_dnrm2(size(), memory, 1);
            case Float32 -> cblas_snrm2(size(), memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };
    }

    /**
     * Computes the L1 norm of a vector.
     *
     * @return L1 norm.
     */
    public double norm1() {
        return switch(scalarType()) {
            case Float64 -> cblas_dasum(size(), memory, 1);
            case Float32 -> cblas_sasum(size(), memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };
    }

    /**
     * Computes the L-Infinity norm of a vector.
     *
     * @return L-Infinity norm.
     */
    public double normInf() {
        return get((int) iamax());
    }

    @Override
    public Vector scale(double alpha) {
        switch(scalarType()) {
            case Float64 -> cblas_dscal(size(), alpha, memory, 1);
            case Float32 -> cblas_sscal(size(), (float) alpha, memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }
        return this;
    }

    /**
     * Swaps two vectors.
     *
     * @param y Vector to be swapped.
     */
    public void swap(Vector y) {
        switch(scalarType()) {
            case Float64 -> cblas_dswap(size(), memory, 1, y.memory, 1);
            case Float32 -> cblas_sswap(size(), memory, 1, y.memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }
    }

    /**
     * Searches a vector for the first occurrence of the maximum absolute
     * value.
     *
     * @return The first index of the maximum absolute value of vector x.
     */
    public long iamax() {
        return switch(scalarType()) {
            case Float64 -> cblas_idamax(size(), memory, 1);
            case Float32 -> cblas_isamax(size(), memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };
    }
}
