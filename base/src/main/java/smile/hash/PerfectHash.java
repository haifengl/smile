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
package smile.hash;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A perfect hash of an array of strings to their index in the array.
 *
 * <h2>What is a perfect hash?</h2>
 * A perfect hash function for a set {@code S} is a hash function that maps
 * distinct elements in {@code S} to a set of integers with no collisions.
 * In mathematical terms, it is an injective function.  Perfect hash functions
 * may be used to implement a lookup table with constant worst-case access time.
 *
 * <h2>Hash function</h2>
 * Given a keyword {@code w} of length {@code L}, the hash value is computed as:
 * <pre>
 *   h(w) = (L + sum_i kvals[w[i] - min]) mod tsize
 * </pre>
 * where
 * <ul>
 *   <li>{@code w[i]} is the {@code i}-th character of {@code w} (or only the
 *       characters at the user-supplied {@code select} positions when provided),</li>
 *   <li>{@code min} is the smallest character value that appears in any keyword,</li>
 *   <li>{@code kvals} is an integer array indexed by {@code (character - min)}, and</li>
 *   <li>{@code tsize} is the size of the hash table.</li>
 * </ul>
 * Lookup is O(L) in the length of the query string and O(1) in the number of keywords.
 *
 * <h2>Construction algorithm</h2>
 * The construction uses a <em>randomized character-value</em> approach:
 * <ol>
 *   <li><b>Character frequency analysis.</b>  The frequency of every character
 *       across all keywords is counted.  Keywords are sorted in descending order
 *       of their aggregate character frequency so that the most-constrained keys
 *       are placed first.</li>
 *   <li><b>Table-size selection.</b>  The initial table size {@code tsize} is set
 *       to {@code ceil(1.5 * n)}, where {@code n} is the number of keywords,
 *       giving a load factor of about 2/3.</li>
 *   <li><b>Random assignment.</b>  Each entry of {@code kvals} is assigned a
 *       uniformly random integer in {@code [0, tsize)}.  All {@code n} hash values
 *       {@code h(w) mod tsize} are then checked for collisions.</li>
 *   <li><b>Retry.</b>  If any two keywords collide, a new random {@code kvals}
 *       assignment is drawn and step 3 is repeated.  At load factor 2/3 the
 *       probability that a random assignment is collision-free is at least
 *       {@code e^{-n^2/(2*tsize)} ≈ e^{-3n/4}}, so the expected number of
 *       trials is O(1) per construction.</li>
 *   <li><b>Table growth.</b>  If no collision-free assignment is found within
 *       1 000 attempts, {@code tsize} is incremented by one and the random
 *       search restarts from scratch.  This ensures guaranteed termination.</li>
 *   <li><b>Table construction.</b>  Once a collision-free {@code kvals} is found,
 *       the final lookup table of size {@code tsize} is filled with keyword
 *       indices.  Slots that correspond to no keyword are left as {@code -1}.</li>
 * </ol>
 * Overall expected construction time is {@code O(n * |alphabet|)}.
 *
 * @author Haifeng Li
 */
public class PerfectHash implements Serializable {
    /** The keyword set. */
    private final String[] keywords;
    /** Hash table. */
    private int[] table;
    /** The k parameters to calculate the hash. */
    private int[] kvals;
    /** The lowest character value shown in the keywords. */
    private char min;
    /** The character positions in keywords used to calculate the hash. */
    private int[] select;
    /** The table size (modulus used during lookup). */
    private int tsize;

    /**
     * Constructor.
     * @param keywords the fixed set of keywords.
     */
    public PerfectHash(String... keywords) {
        this(null, keywords);
    }

    /**
     * Constructor.
     * @param select The character positions in keywords used to calculate the hash.
     * @param keywords the fixed set of keywords.
     */
    public PerfectHash(int[] select, String... keywords) {
        if (keywords.length == 0) {
            throw new IllegalArgumentException("Empty string set");
        }

        if (select != null) {
            this.select = Arrays.copyOf(select, select.length);
            Arrays.sort(this.select);
        }

        this.keywords = keywords;
        generate(keywords);
    }

    /**
     * Returns the index of a keyword. If the string
     * is not in the set, returns -1.
     * @param key the keyword.
     * @return the index of keyword or -1.
     */
     public int get(String key) {
        int raw = hash(key);
        if (raw < 0) return -1;
        int i = raw % tsize;
        if (i >= table.length) return -1;
        int idx = table[i];
        if (idx < 0 || !key.equals(keywords[idx])) return -1;
        return idx;
    }

    /**
     * Returns the hash code of a string.
     * @param key the keyword.
     * @return the hash code.
     */
    private int hash(String key) {
        int klen = key.length();
        int out = klen;
        if (select == null) {
            for (int i = 0; i < klen; i++) {
                int c = key.charAt(i) - min;
                if (c < 0 || c >= kvals.length) return -1;
                out += kvals[c];
            }
        } else {
            for (int i : select) {
                if (i >= klen) continue;
                int c = key.charAt(i) - min;
                if (c < 0 || c >= kvals.length) return -1;
                out += kvals[c];
            }
        }

        return out;
    }

    /** Keyword information. */
    private static class Key implements Comparable<Key> {
        /** selected characters in the string for hash computation. */
        final char[] ksig;
        /** original key length. */
        final int klen;
        /** the frequency of each character. */
        int kfreq;
        /** the value of map. */
        final int value;

        public Key(char[] ksig, int klen, int value) {
            this.ksig = ksig;
            this.klen = klen;
            this.kfreq = 0;
            this.value = value;
        }

        @Override
        public int compareTo(Key b) {
            // sort in descending order
            return Integer.compare(b.kfreq, kfreq);
        }
    }

    /** Sorts an array according to freq. */
    private void sort(char[] arr, int[] freq) {
        for (int i = 1; i < arr.length; i++) {
            for (int j = i; j > 0; j--) {
                if (freq[arr[j]] < freq[arr[j - 1]]) {
                    char tmp = arr[j];
                    arr[j] = arr[j - 1];
                    arr[j - 1] = tmp;
                } else {
                    break;
                }
            }
        }
    }

    /** Adds a key. */
    private void add(Map<String, Key> map, String k, int v) {
        char[] ksig;
        int klen = k.length();

        if (select == null) {
            ksig = k.toCharArray();
        } else {
            ksig = new char[select.length];
            int idx = 0;
            for (int i : select) {
                if (i >= klen) continue;
                ksig[idx++] = k.charAt(i);
            }

            if (idx < ksig.length) {
                ksig = Arrays.copyOf(ksig, idx);
            }
        }

        Key prev = map.put(k, new Key(ksig, klen, v));
        if (prev != null) {
            throw new IllegalArgumentException(String.format("Duplicate key %s at %d and %d", k, prev.value, v));
        }
    }

    /** Counts the character frequency across all string keys. */
    private int[] countCharacterFrequency(Map<String, Key> map) {
        int[] freq = new int[Character.MAX_VALUE];
        for (Key m : map.values()) {
            for (char c : m.ksig) freq[c]++;
        }
        return freq;
    }

    /** Sorts keys by character frequency. */
    private Key[] sortKeys(Map<String, Key> map, int[] freq) {
        Key[] keys = new Key[map.size()];
        int idx = 0;
        for (Key key : map.values()) {
            keys[idx++] = key;
            for (char c : key.ksig) {
                key.kfreq += freq[c];
            }
            sort(key.ksig, freq);
        }
        Arrays.sort(keys);
        return keys;
    }

    /** Find a char in a which is not in b. */
    private char diff(char[] a, char[] b) {
        OUTER: for (char _a : a) {
            for (char _b : b) {
                if (_a == _b) continue OUTER;
            }
            return _a;
        }

        throw new IllegalArgumentException(String.format("Failed to find disjoint union of keysigs: %s and %s", Arrays.toString(a), Arrays.toString(b)));
    }

    /** Used in key generation phase. */
    private int hash(Key m) {
        int out = m.klen;
        for (char c : m.ksig) out += kvals[c - min];
        return out;
    }

    /** Generates the perfect hash. */
    private void generate(String[] keywords) {
        Map<String, Key> map = new HashMap<>();
        for (int i = 0; i < keywords.length; i++) {
            add(map, keywords[i], i);
        }

        int[] freq = countCharacterFrequency(map);
        Key[] keys = sortKeys(map, freq);

        min = Character.MAX_VALUE;
        char max = 0;

        for (char i = 0; i < freq.length; i++) {
            if (freq[i] > 0) {
                min = i;
                break;
            }
        }

        for (int i = freq.length - 1; i >= 0; i--) {
            if (freq[i] > 0) {
                max = (char) i;
                break;
            }
        }

        if (max < min) {
            throw new IllegalStateException("Failed to generate perfect hash. Possibly all empty keys.");
        }

        int kvlen = max - min + 1;
        kvals = new int[kvlen];

        int vsize = keys.length;
        // Table size: start at 1.5x and grow as needed.
        int tsize = vsize + (vsize >> 1);

        // Randomized approach: assign random values in [0, tsize) to each
        // character slot, then check for collisions. Retry with a new seed
        // if any collision occurs. Grow the table if too many retries.
        // This converges in O(n) expected retries for load factor <= 2/3.
        Random rng = new Random(0);
        final int MAX_RETRIES = 1000;

        outer:
        for (;;) {
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                // Assign random kvals in [0, tsize - vsize) so that
                // hash = klen + sum(kvals[c]) stays within tsize.
                // We use values in [0, tsize) and check bounds at placement.
                for (int i = 0; i < kvlen; i++) {
                    kvals[i] = rng.nextInt(tsize);
                }

                // Check all keys map to distinct slots in [0, tsize).
                int[] used = new int[tsize];
                Arrays.fill(used, -1);
                boolean collision = false;
                for (Key key : keys) {
                    int h = hash(key) % tsize;
                    if (h < 0) h += tsize;
                    if (used[h] != -1) {
                        collision = true;
                        break;
                    }
                    used[h] = key.value;
                }

                if (!collision) {
                    // Build the final lookup table.
                    this.tsize = tsize;
                    table = new int[tsize];
                    Arrays.fill(table, -1);
                    for (Key key : keys) {
                        int h = hash(key) % tsize;
                        if (h < 0) h += tsize;
                        table[h] = key.value;
                    }
                    return;
                }
            }
            // Too many retries — grow the table and try again.
            tsize++;
        }
    }
}
