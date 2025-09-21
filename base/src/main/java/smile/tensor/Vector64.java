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
import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A vector of double precision floating numbers.
 *
 * @author Haifeng Li
 */
class Vector64 extends Vector implements Serializable {
    /**
     * The on-heap data.
     */
    final double[] array;
    /**
     * The vector base offset.
     */
    final int offset;
    /**
     * The length of vector.
     */
    final int length;

    /**
     * Private constructor with MemorySegment.
     * @param memory the memory segment of data.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     * @param m the number of rows.
     * @param n the number of columns.
     */
    private Vector64(MemorySegment memory, double[] array, int offset, int length, int m, int n) {
        if (offset < 0 || offset >= array.length) {
            throw new IllegalArgumentException("Invalid offset: " + offset);
        }
        if (offset + length >= array.length) {
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
     * Constructor.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     * @param m the number of rows.
     * @param n the number of columns.
     */
    public Vector64(double[] array, int offset, int length, int m, int n) {
        this(memory(array, offset, length), array, offset, length, m, n);
    }

    /**
     * Constructor of column vector.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     */
    public Vector64(double[] array, int offset, int length) {
        this(array, offset, length, length, 1);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memory = memory(array, offset, length);
    }

    @Override
    public ScalarType scalarType() {
        return ScalarType.Float64;
    }

    @Override
    public Vector64 transpose() {
        return new Vector64(memory, array, offset, length, n, m);
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
    public Vector slice(int from, int to) {
        int length = to - from;
        long byteSize = memory.byteSize();
        var slice = memory.asSlice(from * byteSize, length * byteSize);
        int m = nrow() > 1 ? length : 1;
        int n = nrow() > 1 ? 1 : length;
        return new Vector64(slice, array, offset + from, length, m, n);
    }

    @Override
    public Vector copy(int from, int to) {
        var data = Arrays.copyOfRange(array, offset + from, offset + to);
        int length = data.length;
        int m = nrow() > 1 ? length : 1;
        int n = nrow() > 1 ? 1 : length;
        return new Vector64(data, 0, length, m, n);
    }

    @Override
    public void copy(int pos, Vector dest, int destPos, int length) {
        if (dest instanceof Vector32 other) {
            System.arraycopy(array, pos, other.array, destPos, length);
        } else {
            throw new UnsupportedOperationException("Incompatible scalar type: " + dest.scalarType());
        }
    }

    @Override
    public double[] toArray(double[] a) {
        if (a.length < length) {
            a = Arrays.copyOfRange(array, offset, offset + length);
        } else {
            System.arraycopy(array, offset, a, 0, length);
        }
        return a;
    }

    @Override
    public float[] toArray(float[] a) {
        if (a.length < length) {
            a = new float[length];
        }
        for (int i = 0; i < length; i++) {
            a[i] = (float) array[offset + i];
        }
        return a;
    }

    @Override
    public void fill(double value) {
        Arrays.fill(array, offset, offset+length, value);
    }

    @Override
    public void fill(int from, int to, double value) {
        Arrays.fill(array, offset+from, offset+to, value);
    }

    @Override
    public String toString() {
        String suffix = size() > 10 ?  ", ...]" : "]";
        return IntStream.range(0, length)
                .limit(10)
                .mapToObj(i -> AbstractTensor.format(array[offset+i]))
                .collect(Collectors.joining(", ", "[", suffix));
    }
}
