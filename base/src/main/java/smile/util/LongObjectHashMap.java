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
 * An open-addressing hash map from primitive {@code long} keys to object
 * values. Avoids autoboxing of keys entirely.
 * <p>
 * {@code Long.MIN_VALUE (0x8000000000000000L)} is reserved as the free-slot
 * sentinel and must not be used as a key.
 *
 * @param <V> the type of values.
 * @author Haifeng Li
 */
public class LongObjectHashMap<V> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Sentinel value for an empty slot. */
    private static final long FREE_KEY = Long.MIN_VALUE;

    /** Parallel key array. */
    private long[] keys;
    /** Parallel value array. */
    private Object[] values;

    /** Load factor, in (0, 1). */
    private final float loadFactor;
    /** Resize threshold. */
    private int threshold;
    /** Number of entries. */
    private int size;
    /** Bit mask for index calculation (capacity - 1). */
    private int mask;

    /**
     * Constructs an empty map with default capacity 16 and load factor 0.75.
     */
    public LongObjectHashMap() {
        this(16, 0.75f);
    }

    /**
     * Constructs an empty map.
     * @param initialCapacity the initial capacity (will be rounded up to a power of two).
     * @param loadFactor the load factor, must be in (0, 1).
     */
    public LongObjectHashMap(int initialCapacity, float loadFactor) {
        if (loadFactor <= 0 || loadFactor >= 1)
            throw new IllegalArgumentException("Invalid load factor: " + loadFactor);
        if (initialCapacity <= 0)
            throw new IllegalArgumentException("Invalid initial capacity: " + initialCapacity);

        this.loadFactor = loadFactor;
        int capacity = nextPowerOf2(Math.max(initialCapacity, 2));
        mask = capacity - 1;
        keys = new long[capacity];
        Arrays.fill(keys, FREE_KEY);
        values = new Object[capacity];
        threshold = (int) (capacity * loadFactor);
    }

    /**
     * Returns the number of key-value mappings.
     * @return the number of entries.
     */
    public int size() {
        return size;
    }

    /**
     * Returns {@code true} if this map contains no entries.
     * @return {@code true} if empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     * @param key the key (must not be {@code Long.MIN_VALUE}).
     * @return the value, or {@code null} if absent.
     */
    @SuppressWarnings("unchecked")
    public V get(long key) {
        int ptr = hash(key);
        for (;;) {
            long k = keys[ptr];
            if (k == FREE_KEY) return null;
            if (k == key) return (V) values[ptr];
            ptr = (ptr + 1) & mask;
        }
    }

    /**
     * Associates the specified value with the specified key.
     * @param key the key (must not be {@code Long.MIN_VALUE}).
     * @param value the value.
     * @return the previous value, or {@code null} if there was no mapping.
     */
    @SuppressWarnings("unchecked")
    public V put(long key, V value) {
        int ptr = hash(key);
        for (;;) {
            long k = keys[ptr];
            if (k == FREE_KEY) {
                keys[ptr] = key;
                values[ptr] = value;
                if (++size >= threshold) rehash();
                return null;
            }
            if (k == key) {
                V old = (V) values[ptr];
                values[ptr] = value;
                return old;
            }
            ptr = (ptr + 1) & mask;
        }
    }

    /**
     * If the specified key is not already associated with a value, associates
     * it with the given value and returns {@code null}; otherwise returns the
     * current value.
     * @param key the key.
     * @param value the value to insert if absent.
     * @return the existing value if present, else {@code null}.
     */
    @SuppressWarnings("unchecked")
    public V putIfAbsent(long key, V value) {
        int ptr = hash(key);
        for (;;) {
            long k = keys[ptr];
            if (k == FREE_KEY) {
                keys[ptr] = key;
                values[ptr] = value;
                if (++size >= threshold) rehash();
                return null;
            }
            if (k == key) {
                return (V) values[ptr];
            }
            ptr = (ptr + 1) & mask;
        }
    }

    /** Returns the slot index for the given key. */
    private int hash(long key) {
        // Fibonacci hashing to spread keys uniformly
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> (64 - Integer.numberOfTrailingZeros(keys.length)));
    }

    /** Doubles capacity and rehashes all entries. */
    @SuppressWarnings("unchecked")
    private void rehash() {
        int newCap = keys.length * 2;
        long[] oldKeys = keys;
        Object[] oldVals = values;

        keys = new long[newCap];
        Arrays.fill(keys, FREE_KEY);
        values = new Object[newCap];
        mask = newCap - 1;
        threshold = (int) (newCap * loadFactor);
        size = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            long k = oldKeys[i];
            if (k != FREE_KEY) {
                put(k, (V) oldVals[i]);
            }
        }
    }

    /** Returns the smallest power of 2 >= {@code n}. */
    private static int nextPowerOf2(int n) {
        int v = n - 1;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        return v + 1;
    }
}

