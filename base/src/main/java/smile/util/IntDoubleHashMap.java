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

/**
 * {@code HashMap<int, double>} for primitive types.
 * {@code Integer.MIN_VALUE (0x80000000)} is not allowed as key.
 */
public class IntDoubleHashMap implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int FREE_KEY = Integer.MIN_VALUE;

    private static final double NO_VALUE = Double.NaN;

    /**
     * The key array.
     */
    private int[] keys;
    /**
     * The value array.
     */
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
     * @param key the key.
     * @return the value.
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

    /**
     * Associates the specified value with the specified key in this map.
     * @param key the key.
     * @param value the value.
     * @return the old value.
     */
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

    /**
     * Removes the mapping for the specified key from this map if present.
     * @param key the key.
     * @return the previous value, or {@code Double.NaN} if not present.
     */
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
                shiftKeys(ptr);
                --size;
                return ret;
            }
            ptr = (ptr + 1) & mask;
        } while (true);
    }

    /**
     * Shifts entries after a deletion to preserve probe-chain integrity
     * for open-addressing with linear probing (Robin Hood backward-shift).
     */
    private void shiftKeys(int pos) {
        while (true) {
            int next = (pos + 1) & mask;
            while (true) {
                int k = keys[next];
                if (k == FREE_KEY) {
                    keys[pos] = FREE_KEY;
                    return;
                }
                int slot = hash(k);
                // We can pull keys[next] back to pos iff slot is NOT in (pos, next]
                // i.e. the key's home is at or before pos (the gap) in circular order.
                if (!inRange(pos + 1, next, slot)) {
                    keys[pos]   = k;
                    values[pos] = values[next];
                    pos = next;
                    break;
                }
                next = (next + 1) & mask;
            }
        }
    }

    /**
     * Returns true if {@code slot} is in the circular range [lo, hi] (both inclusive).
     * lo and hi are indices into the table; lo may be > hi when the range wraps.
     */
    private boolean inRange(int lo, int hi, int slot) {
        lo  &= mask;
        if (lo <= hi) return lo <= slot && slot <= hi;
        return slot >= lo || slot <= hi;  // wraps around
    }

    /**
     * Returns true if this map contains no key-value mappings.
     * @return true if this map is empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of key-value mappings in this map.
     * @return the number of key-value mappings in this map.
     */
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
        size = 0;   // reset: rawPut will re-count

        for (int i = 0; i < oldCapacity; i++) {
            int oldKey = oldKeys[i];
            if (oldKey != FREE_KEY) {
                rawPut(oldKey, oldValues[i]);
            }
        }
    }

    /** Insert without rehash check — used only during rehash itself. */
    private void rawPut(int key, double value) {
        int ptr = hash(key);
        while (keys[ptr] != FREE_KEY) ptr = (ptr + 1) & mask;
        keys[ptr]   = key;
        values[ptr] = value;
        size++;
    }

    /**
     * Return the least power of two greater than or equal to the specified value.
     * <p>
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
