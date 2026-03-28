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
import java.util.Iterator;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import smile.sort.QuickSort;
import smile.util.function.IntArrayElementConsumer;
import smile.util.function.IntArrayElementFunction;

/**
 * Sparse array of integers.
 *
 * @author Haifeng Li
 */
public class SparseIntArray implements Iterable<SparseIntArray.Entry>, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    // Entry as an object has too much overhead and not CPU cache friendly.
    // Use two continuous array lists for index and value correspondingly.
    /** The index of nonzero entries. */
    private final IntArrayList index;
    /** The value of nonzero entries. */
    private final IntArrayList value;

    /**
     * The entry in a sparse array of double values.
     * @param index The index of entry.
     * @param value The value of entry.
     */
    public record Entry(int index, int value) implements Comparable<Entry> {
        @Override
        public String toString() {
            return String.format("%d:%d", index, value);
        }

        @Override
        public int compareTo(Entry o) {
            return Double.compare(value, o.value);
        }
    }

    /**
     * Constructor.
     */
    public SparseIntArray() {
        this(10);
    }

    /**
     * Constructor.
     * @param initialCapacity the initial capacity.
     */
    public SparseIntArray(int initialCapacity) {
        index = new IntArrayList(initialCapacity);
        value = new IntArrayList(initialCapacity);
    }

    /**
     * Constructor.
     * @param entries the nonzero entries.
     */
    public SparseIntArray(Collection<Entry> entries) {
        index = new IntArrayList(entries.size());
        value = new IntArrayList(entries.size());

        for (Entry e : entries) {
            index.add(e.index);
            value.add(e.value);
        }
    }

    /**
     * Constructor.
     * @param stream the stream of nonzero entries.
     */
    public SparseIntArray(Stream<Entry> stream) {
        this(stream.toList());
    }

    @Override
    public String toString() {
        String suffix = size() > 10 ?  ", ...]" : "]";
        return stream().limit(10)
                .map(Entry::toString)
                .collect(Collectors.joining(", ", "[", suffix));
    }

    /**
     * Returns the number of nonzero entries.
     * @return the number of nonzero entries
     */
    public int size() {
        return index.size();
    }

    /**
     * Returns true if the array is empty.
     * @return true if the array is empty.
     */
    public boolean isEmpty() {
        return index.isEmpty();
    }

    /**
     * Performs an action for each nonzero entry.
     * @param action a non-interfering action to perform on the nonzero entries.
     */
    public void forEach(IntArrayElementConsumer action) {
        int n = size();
        for (int i = 0; i < n; i++) {
            action.apply(index.get(i), value.get(i));
        }
    }

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the nonzero entries.
     * @param mapper a non-interfering, stateless function to map each
     *               nonzero entry to new value.
     * @return the stream of the new values of nonzero entries.
     */
    public DoubleStream map(IntArrayElementFunction mapper) {
        return IntStream.range(0, size()).mapToDouble(i -> mapper.apply(index.get(i), value.get(i)));
    }

    /**
     * Updates each nonzero entry.
     * @param mapper a function to map each nonzero entry to new value.
     */
    public void update(IntArrayElementFunction mapper) {
        int n = size();
        for (int i = 0; i < n; i++) {
            value.set(i, mapper.apply(index.get(i), value.get(i)));
        }
    }

    @Override
    public Iterator<Entry> iterator() {
        return new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < size();
            }

            @Override
            public Entry next() {
                Entry e = new Entry(index.get(i), value.get(i));
                i++;
                return e;
            }
        };
    }

    /**
     * Returns the stream of nonzero entries.
     * @return the stream of nonzero entries.
     */
    public Stream<Entry> stream() {
        return IntStream.range(0, size()).mapToObj(i -> new Entry(index.get(i), value.get(i)));
    }

    /**
     * Returns the stream of the indices of nonzero entries.
     * @return the stream of the indices of nonzero entries.
     */
    public IntStream indexStream() {
        return index.stream();
    }

    /**
     * Returns the stream of the values of nonzero entries.
     * @return the stream of the values of nonzero entries.
     */
    public IntStream valueStream() {
        return value.stream();
    }

    /**
     * Sorts the array elements such that the indices are in ascending order.
     */
    public void sort() {
        QuickSort.sort(index.data, value.data, size());
    }

    /**
     * Returns the value of i-th entry.
     * @param i the index of entry.
     * @return the value of entry, 0.0 if the index doesn't exist in the array.
     */
    public double get(final int i) {
        int length = size();
        for (int k = 0; k < length; k++) {
            if (index.get(k) == i) return value.get(k);
        }
        return 0.0;
    }

    /**
     * Sets or adds an entry.
     * @param i the index of entry.
     * @param x the value of entry.
     * @return true if a new entry added, false if an existing entry updated.
     */
    public boolean set(int i, int x) {
        if (x == 0.0) {
            remove(i);
            return false;
        }

        int length = size();
        for (int k = 0; k < length; k++) {
            if (index.get(k) == i) {
                value.set(k, x);
                return false;
            }
        }

        index.add(i);
        value.add(x);
        return true;
    }

    /**
     * Append an entry to the array, optimizing for the case where the
     * index is greater than all existing indices in the array.
     * @param i the index of entry.
     * @param x the value of entry.
     */
    public void append(int i, int x) {
        if (x != 0.0) {
            index.add(i);
            value.add(x);
        }
    }

    /**
     * Removes an entry.
     * @param i the index of entry.
     */
    public void remove(int i) {
        int length = size();
        for (int k = 0; k < length; k++) {
            if (index.get(k) == i) {
                index.remove(k);
                value.remove(k);
                return;
            }
        }
    }
}
