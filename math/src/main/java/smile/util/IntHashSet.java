/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.util;

import java.util.Arrays;

/**
 * HashSet<int> for primitive types.
 * Integer.MIN_VALUE (0x80000000) is not allowed as key.
 */
public class IntHashSet {
    private static final int FREE_KEY = Integer.MIN_VALUE;

    /**
     * Keys.
     */
    private int[] keys;

    /**
     * The load factor, must be between (0 and 1).
     */
    private final float loadFactor;
    /**
     * We will resize a set once it reaches this size.
     */
    private int threshold;
    /**
     * The number of set entries.
     */
    private int size;

    /**
     * Mask to calculate the original position.
     */
    private int mask;

    /**
     * Constructs an empty HashSet with the default initial
     * capacity (16) and the default load factor (0.75).
     */
    public IntHashSet() {
        this(16, 0.75f);
    }

    /**
     * Constructor.
     *
     * @param initialCapacity the initial capacity.
     * @param loadFactor the load factor.
     */
    public IntHashSet(int initialCapacity, float loadFactor) {
        if (loadFactor <= 0 || loadFactor >= 1) {
            throw new IllegalArgumentException("Invalid fill factor: " + loadFactor);
        }

        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Invalid initial capacity: " + initialCapacity);
        }

        this.loadFactor = loadFactor;
        int capacity = arraySize(initialCapacity, loadFactor);
        mask = capacity - 1;

        keys = new int[capacity];
        Arrays.fill(keys, FREE_KEY);
        threshold = (int) (capacity * loadFactor);
    }

    /**
     * Returns true if this set contains the specified element.
     */
    public boolean contains(int key) {
        if (key == FREE_KEY) {
            throw new IllegalArgumentException("key cannot be 0x80000000");
        }

        int ptr = hash(key);

        do {
            int k = keys[ptr];
            if (k == FREE_KEY) return false;
            if (k == key) return true;
            ptr = (ptr + 1) & mask; // next index
        } while (true);
    }

    /**
     * Adds the specified element to this set if it is not already present.
     * If this set already contains the element, the call leaves the set unchanged
     * and returns false.
     */
    public boolean add(int key) {
        if (key == FREE_KEY) {
            throw new IllegalArgumentException("key cannot be 0x80000000");
        }

        int ptr = hash(key);
        do {
            int k = keys[ptr];
            if (k == FREE_KEY) {
                keys[ptr] = key;
                if (++size >= threshold) {
                    rehash(keys.length * 2);
                }

                return true;
            }

            if (k == key) {
                return false;
            }

            ptr = (ptr + 1) & mask;
        } while (true);
    }

    /**
     * Removes the specified element from this set if it is present.
     * Returns true if this set contained the element (or equivalently,
     * if this set changed as a result of the call).
     */
    public boolean remove(int key) {
        if (key == FREE_KEY) {
            throw new IllegalArgumentException("key cannot be 0x80000000");
        }

        int ptr = hash(key);
        do {
            int k = keys[ptr];
            if (k == FREE_KEY) {
                return false;
            }

            if (k == key) {
                keys[ptr] = FREE_KEY;
                return true;
            }

            ptr = (ptr + 1) & mask;
        } while (true);
    }

    /** Returns the number of elements in this set. */
    public int size() {
        return size;
    }

    /** Returns the elements as an array. */
    public int[] toArray() {
        int[] a = new int[size];
        int i = 0;
        for (int k : keys) {
            if (k != FREE_KEY)
                a[i++] = k;
        }
        return a;
    }

    /** Resize the hash table. */
    private void rehash(int newCapacity) {
        threshold = (int) (newCapacity * loadFactor);
        mask = newCapacity - 1;

        int oldCapacity = keys.length;
        int[] oldKeys = keys;

        keys = new int[newCapacity];
        Arrays.fill(keys, FREE_KEY);

        for (int i = 0; i < oldCapacity; i++) {
            int oldKey = oldKeys[i];
            if (oldKey != FREE_KEY) {
                add(oldKey);
            }
        }
    }

    /**
     * Return the least power of two greater than or equal to the specified value.
     *
     * Note that this function will return 1 when the argument is 0.
     *
     * @param x a long integer smaller than or equal to 2<sup>62</sup>.
     * @return the least power of two greater than or equal to the specified value.
     */
    private long nextPowerOfTwo(long x) {
        if (x == 0) return 1;
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return (x | x >> 32) + 1;
    }

    /**
     * Returns the least power of two smaller than or equal to
     * 2<sup>30</sup> and larger than or equal to
     * <code>ceil(expected / f)</code>.
     *
     * @param expected the expected number of elements in a hash table.
     * @param f        the load factor.
     * @return the minimum possible size for a backing array.
     * @throws IllegalArgumentException if the necessary size is larger than 2<sup>30</sup>.
     */
    private int arraySize(int expected, float f) {
        long s = Math.max(2, nextPowerOfTwo((long) Math.ceil(expected / f)));

        if (s > (1 << 30)) {
            throw new IllegalArgumentException(String.format("Too large %d expected elements with load factor %.2f", expected, f));
        }

        return (int) s;
    }

    /** Magic number for hash function. */
    private static final int INT_PHI = 0x9E3779B9;

    /** The hash function for int. */
    private int hash( int x) {
        int h = x * INT_PHI;
        return (h ^ (h >> 16)) & mask;
    }
}
