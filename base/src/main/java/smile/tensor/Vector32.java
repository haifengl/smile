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
 * A vector of single precision floating numbers.
 *
 * @author Haifeng Li
 */
class Vector32 extends Vector implements Serializable {
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
     * Constructor.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    public Vector32(float[] array, int offset, int length, int nrow, int ncol) {
        if (offset < 0 || offset >= array.length) {
            throw new IllegalArgumentException("Invalid offset: " + offset);
        }
        if (offset + length >= array.length) {
            throw new IllegalArgumentException("Invalid length: " + length);
        }
        if (nrow != 1 && ncol != 1) {
            throw new IllegalArgumentException("Invalid vector dimension: " + nrow + " x " + ncol);
        }
        if (nrow != length && ncol != length) {
            throw new IllegalArgumentException("Invalid vector dimension: " + nrow + " x " + ncol);
        }

        super(memory(array, offset, length), nrow, ncol);
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Constructor of column vector.
     * @param array the primitive array backing the vector.
     * @param offset the base offset.
     * @param length the length.
     */
    public Vector32(float[] array, int offset, int length) {
        this(array, offset, length, length, 1);
    }

    /**
     * Returns a slice of the memory segment, at the given offset.
     * The returned segment's address is the address of the array
     * plus the given offset; its size is specified by the given
     * argument.
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
     * Returns a vector.
     * @param array the primitive array backing the vector.
     * @return a vector.
     */
    public static Vector32 of(float[] array) {
        return new Vector32(array, 0, array.length);
    }

    /**
     * Returns a zero vector.
     * @param length the length of vector.
     * @return a zero vector.
     */
    public static Vector32 zeros(int length) {
        return new Vector32(new float[length], 0, length);
    }

    @Override
    public ScalarType scalarType() {
        return ScalarType.Float32;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memory = MemorySegment.ofArray(array);
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
    public void set(int i, double x) {
        array[offset + i] = (float) x;
    }

    @Override
    public double get(int i) {
        return array[offset + i];
    }

    @Override
    public void set(int i, int j, double x) {
        assert(i == 0 || j == 0);
        array[offset + i + j] = (float) x;
    }

    @Override
    public double get(int i, int j) {
        assert(i == 0 || j == 0);
        return array[offset + i + j];
    }

    @Override
    public Vector32 copy(int from, int to) {
        return Vector32.of(Arrays.copyOfRange(array, offset + from, offset + to));
    }

    @Override
    public Vector32 transpose() {
        return new Vector32(array, offset, length, n, m);
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
