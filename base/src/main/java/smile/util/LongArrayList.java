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
package smile.util;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * A resizeable, array-backed list of {@code long} primitives.
 *
 * @author Haifeng Li
 */
public final class LongArrayList implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The backing array. */
    private long[] data;
    /** The number of valid entries. */
    private int size;

    /** Constructs an empty list with initial capacity 10. */
    public LongArrayList() {
        this(10);
    }

    /**
     * Constructs an empty list with the specified initial capacity.
     * @param capacity the initial capacity.
     */
    public LongArrayList(int capacity) {
        data = new long[capacity];
        size = 0;
    }

    /**
     * Constructs a list containing the values of the specified array.
     * @param values the initial values.
     */
    public LongArrayList(long[] values) {
        this(Math.max(values.length, 10));
        add(values);
    }

    @Override
    public String toString() {
        String suffix = size > 10 ? ", ...]" : "]";
        return stream().limit(10)
                .mapToObj(Long::toString)
                .collect(Collectors.joining(", ", "[", suffix));
    }

    /**
     * Returns the stream of elements.
     * @return the stream.
     */
    public LongStream stream() {
        return Arrays.stream(data, 0, size);
    }

    /**
     * Ensures capacity is at least {@code capacity}.
     * @param capacity the minimum required capacity.
     */
    public void ensureCapacity(int capacity) {
        if (capacity > data.length) {
            int newCap = Math.max(data.length << 1, capacity);
            data = Arrays.copyOf(data, newCap);
        }
    }

    /**
     * Returns the number of elements.
     * @return the number of elements.
     */
    public int size() {
        return size;
    }

    /**
     * Returns {@code true} if this list contains no elements.
     * @return {@code true} if empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the element at the specified index.
     * @param index the index.
     * @return the element.
     */
    public long get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        return data[index];
    }

    /**
     * Sets the element at the specified index.
     * @param index the index.
     * @param val the new value.
     */
    public void set(int index, long val) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        data[index] = val;
    }

    /**
     * Appends the specified value to the end of this list.
     * @param val the value.
     */
    public void add(long val) {
        ensureCapacity(size + 1);
        data[size++] = val;
    }

    /**
     * Appends all values in the specified array.
     * @param vals the values.
     */
    public void add(long[] vals) {
        ensureCapacity(size + vals.length);
        System.arraycopy(vals, 0, data, size, vals.length);
        size += vals.length;
    }

    /** Removes all elements from the list. */
    public void clear() {
        size = 0;
    }

    /**
     * Returns an array containing all elements in proper sequence.
     * @return a copy of the backing array trimmed to size.
     */
    public long[] toArray() {
        return Arrays.copyOf(data, size);
    }
}

