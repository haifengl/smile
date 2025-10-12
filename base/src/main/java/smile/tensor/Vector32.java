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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.math.MathEx;
import static smile.tensor.ScalarType.*;

/**
 * A vector of single precision floating numbers.
 *
 * @author Haifeng Li
 */
class Vector32 extends Vector {
    /**
     * The on-heap data.
     */
    final float[] array;
    /**
     * The vector base offset.
     */
    final int offset;
    /**
     * The length of vector.
     */
    final int length;

    /**
     * Default constructor for readObject.
     */
    private Vector32() {
        this.array = null;
        this.offset = 0;
        this.length = 0;
    }

    /**
     * Private constructor with MemorySegment.
     * @param memory the memory segment of data.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     * @param m the number of rows.
     * @param n the number of columns.
     */
    private Vector32(MemorySegment memory, float[] array, int offset, int length, int m, int n) {
        if (offset < 0 || offset >= array.length) {
            throw new IllegalArgumentException("Invalid offset: " + offset);
        }
        if (offset + length > array.length) {
            throw new IllegalArgumentException("Invalid length: " + length);
        }
        if (m != 1 && n != 1) {
            throw new IllegalArgumentException("Invalid vector dimension: " + m + " x " + n);
        }
        if (m != length && n != length) {
            throw new IllegalArgumentException("Invalid vector dimension: " + m + " x " + n);
        }

        super(memory, m, n);
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Returns a column vector.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     * @return a column vector.
     */
    public static Vector32 column(float[] array, int offset, int length) {
        return new Vector32(memory(array, offset, length), array, offset, length, length, 1);
    }

    /**
     * Returns of row vector.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     * @return a row vector.
     */
    public static Vector32 row(float[] array, int offset, int length) {
        return new Vector32(memory(array, offset, length), array, offset, length, 1, length);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memory = memory(array, offset, length);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Vector32 b) {
            if (length == b.length) {
                for (int i = 0; i < length; i++) {
                    if (Math.abs(array[offset+i] - b.array[b.offset+i]) > MathEx.FLOAT_EPSILON) {
                        return false;
                    }
                }

                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        String suffix = size() > 10 ?  ", ...]" : "]";
        return IntStream.range(0, length)
                .limit(10)
                .mapToObj(i -> AbstractTensor.format(array[offset+i]))
                .collect(Collectors.joining(", ", "[", suffix));
    }

    @Override
    public ScalarType scalarType() {
        return Float32;
    }

    @Override
    public Vector32 transpose() {
        return new Vector32(memory, array, offset, length, n, m);
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public double get(int i, int j) {
        assert(i == 0 || j == 0);
        return array[offset + i + j];
    }

    @Override
    public void set(int i, int j, double x) {
        assert(i == 0 || j == 0);
        array[offset + i + j] = (float) x;
    }

    @Override
    public void add(int i, int j, double x) {
        assert(i == 0 || j == 0);
        array[offset + i + j] += (float) x;
    }

    @Override
    public void sub(int i, int j, double x) {
        assert(i == 0 || j == 0);
        array[offset + i + j] -= (float) x;
    }

    @Override
    public void mul(int i, int j, double x) {
        assert(i == 0 || j == 0);
        array[offset + i + j] *= (float) x;
    }

    @Override
    public void div(int i, int j, double x) {
        assert(i == 0 || j == 0);
        array[offset + i + j] /= (float) x;
    }

    @Override
    public double get(int i) {
        return array[offset + i];
    }

    @Override
    public void set(int i, double x) {
        array[offset + i] = (float) x;
    }

    @Override
    public void add(int i, double x) {
        array[offset + i] += (float) x;
    }

    @Override
    public void sub(int i, double x) {
        array[offset + i] -= (float) x;
    }

    @Override
    public void mul(int i, double x) {
        array[offset + i] *= (float) x;
    }

    @Override
    public void div(int i, double x) {
        array[offset + i] /= (float) x;
    }

    @Override
    public Vector slice(int from, int to) {
        int length = to - from;
        long byteSize = scalarType().byteSize();
        var slice = memory.asSlice(from * byteSize, length * byteSize);
        int m = nrow() > 1 ? length : 1;
        int n = nrow() > 1 ? 1 : length;
        return new Vector32(slice, array, offset + from, length, m, n);
    }

    @Override
    public Vector copy(int from, int to) {
        var data = Arrays.copyOfRange(array, offset + from, offset + to);
        int length = data.length;
        return nrow() > 1 ? column(data, 0, length) : row(data, 0, length);
    }

    @Override
    public double[] toArray(double[] a) {
        if (a.length < length) {
            a = new double[length];
        }
        for (int i = 0; i < length; i++) {
            a[i] = array[offset + i];
        }
        return a;
    }

    @Override
    public float[] toArray(float[] a) {
        if (a.length < length) {
            a = Arrays.copyOfRange(array, offset, offset + length);
        } else {
            System.arraycopy(array, offset, a, 0, length);
        }
        return a;
    }

    @Override
    public void fill(double value) {
        Arrays.fill(array, offset, offset+length, (float) value);
    }

    @Override
    public void fill(int from, int to, double value) {
        Arrays.fill(array, offset+from, offset+to, (float) value);
    }

    @Override
    public int softmax() {
        int k = length;
        int y = -1;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            if (array[offset+i] > max) {
                max = array[offset+i];
                y = i;
            }
        }

        float Z = 0.0f;
        for (int i = 0; i < k; i++) {
            float out = (float) Math.exp(array[offset+i] - max);
            array[offset+i] = out;
            Z += out;
        }

        for (int i = 0; i < k; i++) {
            array[offset+i] /= Z;
        }

        return y;
    }
}
