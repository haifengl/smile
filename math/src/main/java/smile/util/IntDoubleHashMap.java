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
 * HashMap<int, double> for primitive types.
 * Integer.MIN_VALUE (0x80000000) is not allowed as key.
 */
public class IntDoubleHashMap {
    private static final int FREE_KEY = Integer.MIN_VALUE;

    private static final double NO_VALUE = Double.NaN;

    /**
     * Keys and values.
     */
    private int[] keys;
    private double[] values;

    /**
     * The load factor, must be between (0 and 1).
     */
    private final float loadFactor;
    /**
     * We will resize a map once it reaches this size.
     */
    private int threshold;
    /**
     * The number of map entries.
     */
    private int size;

    /**
     * Mask to calculate the original position.
     */
    private int mask;

    /**
     * Constructs an empty HashMap with the default initial
     * capacity (16) and the default load factor (0.75).
     */
    public IntDoubleHashMap() {
        this(16, 0.75f);
    }

    /**
     * Constructor.
     *
     * @param initialCapacity the initial capacity.
     * @param loadFactor the load factor.
     */
    public IntDoubleHashMap(int initialCapacity, float loadFactor) {
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
        values = new double[capacity];
        threshold = (int) (capacity * loadFactor);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or Double.NaN if this map contains no mapping for the key.
     */
    public double get(int key) {
        if (key == FREE_KEY) {
            throw new IllegalArgumentException("key cannot be 0x80000000");
        }

        int ptr = hash(key);

        do {
            int k = keys[ptr];
            if (k == FREE_KEY) return NO_VALUE;
            if (k == key) return values[ptr];
            ptr = (ptr + 1) & mask; // next index
        } while (true);
    }

    /** Associates the specified value with the specified key in this map. */
    public double put(int key, double value) {
        if (key == FREE_KEY) {
            throw new IllegalArgumentException("key cannot be 0x80000000");
        }

        int ptr = hash(key);
        do {
            int k = keys[ptr];
            if (k == FREE_KEY) {
                keys[ptr] = key;
                values[ptr] = value;
                if (++size >= threshold) {
                    rehash(keys.length * 2);
                }

                return NO_VALUE;
            }

            if (k == key) {
                double ret = values[ptr];
                values[ptr] = value;
                return ret;
            }

            ptr = (ptr + 1) & mask;
        } while (true);
    }

    /** Removes the mapping for the specified key from this map if present. */
    public double remove(int key) {
        if (key == FREE_KEY) {
            throw new IllegalArgumentException("key cannot be 0x80000000");
        }

        int ptr = hash(key);
        do {
            int k = keys[ptr];
            if (k == FREE_KEY) {
                return NO_VALUE;
            }

            if (k == key) {
                double ret = values[ptr];
                keys[ptr] = FREE_KEY;
                return ret;
            }

            ptr = (ptr + 1) & mask;
        } while (true);
    }

    /** Returns the number of key-value mappings in this map. */
    public int size() {
        return size;
    }

    /** Resize the hash table. */
    private void rehash(int newCapacity) {
        threshold = (int) (newCapacity * loadFactor);
        mask = newCapacity - 1;

        int oldCapacity = keys.length;
        int[] oldKeys = keys;
        double[] oldValues = values;

        keys = new int[newCapacity];
        Arrays.fill(keys, FREE_KEY);
        values = new double[newCapacity];

        for (int i = 0; i < oldCapacity; i++) {
            int oldKey = oldKeys[i];
            if (oldKey != FREE_KEY) {
                put(oldKey, oldValues[i]);
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
