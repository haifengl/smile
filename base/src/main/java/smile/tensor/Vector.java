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

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import static smile.linalg.blas.cblas_h.*;

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
     * Default constructor for readObject.
     */
    Vector() {

    }

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
    public Vector column(int j) {
        throw new UnsupportedOperationException();
    }

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
     * @param src the destination vector.
     * @param pos starting position in this vector.
     * @param dest the destination vector.
     * @param destPos starting position in the destination vector.
     * @param length the number of vector elements to be copied.
     */
    public static void copy(Vector src, int pos, Vector dest, int destPos, int length) {
        if (src instanceof Vector32 a && dest instanceof Vector32 b) {
            System.arraycopy(a.array, pos, b.array, destPos, length);
        } else if (src instanceof Vector64 a && dest instanceof Vector64 b) {
            System.arraycopy(a.array, pos, b.array, destPos, length);
        } else {
            for (int i = 0; i < length; i++) {
                dest.set(destPos + i, src.get(pos + i));
            }
        }
    }

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
            long byteSize = 4;
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
            long byteSize = 8;
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
     * Returns the minimal elements of the vector.
     * @return the minimal elements of the vector.
     */
    public double min() {
        int length = size();
        double min = Double.MAX_VALUE;
        for (int i = 0; i < length; i++) {
            min = Math.min(min, get(i));
        }
        return min;
    }

    /**
     * Returns the maximal elements of the vector.
     * @return the maximal elements of the vector.
     */
    public double max() {
        int length = size();
        double max = Double.MIN_VALUE;
        for (int i = 0; i < length; i++) {
            max = Math.max(max, get(i));
        }
        return max;
    }

    /**
     * Sums the elements of the vector.
     * @return Sum of the elements of the vector.
     */
    public double sum() {
        int length = size();
        double s = 0;
        for (int i = 0; i < length; i++) {
            s += get(i);
        }
        return s;
    }

    /**
     * Returns the mean of the elements of the vector.
     * @return the mean of the elements of the vector.
     */
    public double mean() {
        int length = size();
        return length > 0 ? sum() / length : 0;
    }

    /**
     * The softmax function without overflow. The function normalizes
     * the vector into a probability distribution proportional to the
     * exponentials of the input numbers. That is, prior to applying
     * softmax, some vector components could be negative, or greater
     * than one; and might not sum to 1; but after applying softmax,
     * each component will be in the interval (0,1), and the components
     * will add up to 1, so that they can be interpreted as probabilities.
     * Furthermore, the larger input components will correspond to larger
     * probabilities.
     *
     * @return the index of largest posteriori probability.
     */
    public abstract int softmax();

    /**
     * Sums the absolute values of the elements of the vector.
     * @return Sum of the absolute values of the elements of the vector.
     */
    public double asum() {
        return switch(scalarType()) {
            case Float64 -> cblas_dasum(size(), memory, 1);
            case Float32 -> cblas_sasum(size(), memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        };
    }

    /**
     * Computes a constant alpha times a vector x plus this vector y.
     * The result overwrites the initial values of this vector y.
     *
     * @param alpha If {@code alpha = 0} this routine returns without any computation.
     *
     * @param x Input vector.
     */
    public void axpy(double alpha, Vector x) {
        if (scalarType() != x.scalarType()) {
            throw new IllegalArgumentException("Different scalar type: " + x.scalarType());
        }
        if (size() != x.size()) {
            throw new IllegalArgumentException("Different vector size: " + x.size());
        }

        switch(scalarType()) {
            case Float64 -> cblas_daxpy(size(), alpha, x.memory, 1, memory, 1);
            case Float32 -> cblas_saxpy(size(), (float) alpha, x.memory, 1, memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        }
    }

    /**
     * Computes the dot product of two vectors.
     *
     * @param x Input vector contains the second vector operand.
     *
     * @return dot product.
     */
    public double dot(Vector x) {
        if (scalarType() != x.scalarType()) {
            throw new IllegalArgumentException("Different scalar type: " + x.scalarType());
        }
        if (size() != x.size()) {
            throw new IllegalArgumentException("Different vector size: " + x.size());
        }

        return switch(scalarType()) {
            case Float64 -> cblas_ddot(size(), x.memory, 1, memory, 1);
            case Float32 -> cblas_sdot(size(), x.memory, 1, memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
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
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
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
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        };
    }

    /**
     * Computes the L-Infinity norm of a vector.
     *
     * @return L-Infinity norm.
     */
    public double normInf() {
        return Math.abs(get(iamax()));
    }

    @Override
    public Vector scale(double alpha) {
        switch(scalarType()) {
            case Float64 -> cblas_dscal(size(), alpha, memory, 1);
            case Float32 -> cblas_sscal(size(), (float) alpha, memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        }
        return this;
    }

    /**
     * Swaps two vectors.
     *
     * @param x Vector to be swapped.
     */
    public void swap(Vector x) {
        if (scalarType() != x.scalarType()) {
            throw new IllegalArgumentException("Different scalar type: " + x.scalarType());
        }
        if (size() != x.size()) {
            throw new IllegalArgumentException("Different vector size: " + x.size());
        }

        switch(scalarType()) {
            case Float64 -> cblas_dswap(size(), x.memory, 1, memory, 1);
            case Float32 -> cblas_sswap(size(), x.memory, 1, memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        }
    }

    /**
     * Searches a vector for the first occurrence of the maximum absolute
     * value.
     *
     * @return The first index of the maximum absolute value of vector x.
     */
    public int iamax() {
        return (int) switch(scalarType()) {
            case Float64 -> cblas_idamax(size(), memory, 1);
            case Float32 -> cblas_isamax(size(), memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        };
    }
}
