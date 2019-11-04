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

package smile.hash;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A perfect hash of an array of strings to their index in the array.
 *
 * A perfect hash function for a set S is a hash function that maps
 * distinct elements in S to a set of integers, with no collisions.
 * In mathematical terms, it is an injective function.
 *
 * Perfect hash functions may be used to implement a lookup table with
 * constant worst-case access time.
 *
 * A perfect hash function for a specific set S can be found by a
 * randomized algorithm in a number of operations that is proportional
 * to the size of S. The original construction of Fredman, Komlós &
 * Szemerédi (1984) chooses a large prime p (larger than the size
 * of the universe from which S is drawn), and a parameter k, and maps
 * each element x of S to the index g(x) = (kx mod p) mode n.
 */
public class PerfectHash implements Serializable {
    /** The keyword set. */
    private String[] keywords;
    /** Hash table. */
    private int[] table;
    /** The k parameters to calculate the hash. */
    private int[] kvals;
    /** The lowest character value shown in the keywords. */
    private char min;
    /** The character positions in keywords used to calculate the hash. */
    private int[] select;

    /** Constructs the perfect hash of strings. */
    public PerfectHash(String... keywords) {
        this(null, keywords);
    }

    /**
     * Constructs the perfect hash of strings.
     * @param select The character positions in keywords used to calculate the hash.
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
     * Returns the index of a string. If the string
     * is in the set, returns its array index. Otherwise, -1.
     */
     public int get(String key) {
        int i = hash(key);
        if (i < 0 || i >= table.length) return -1;
        int idx = table[i];
        if (!key.equals(keywords[idx])) return -1;
        return idx;
    }

    /** Returns the hash code of a string. */
    private int hash(String k) {
        int klen = k.length();
        int out = klen;
        if (select == null) {
            for (int i = 0; i < klen; i++) {
                int c = k.charAt(i) - min;
                if (c < 0) return -1;
                if (c >= kvals.length) return -2;
                out += kvals[c];
            }

        } else {
            for (int i : select) {
                if (i >= klen) continue;
                int c = k.charAt(i) - min;
                if (c < 0) return -1;
                if (c >= kvals.length) return -2;
                out += kvals[c];
            }
        }

        return out;
    }

    /** Keyword information. */
    private static class Key implements Comparable<Key> {
        /** selected characters in the string for hash computation. */
        char[] ksig;
        /** original key length. */
        int klen;
        /** the frequency of each character. */
        int kfreq;
        /** the value of map. */
        int value;

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

    private void resolve(Key x, Key y, int[] kvals, char min) {
        char c = diff(x.ksig, y.ksig);
        kvals[c - min]++;
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

        for (char i = (char)(freq.length - 1); i >= 0; i--) {
            if (freq[i] > 0) {
                max = i;
                break;
            }
        }

        if (max < min) {
            throw new IllegalStateException("Failed to generate perfect hash. Possibly all empty keys.");
        }

        kvals = new int[max - min + 1];

        int vsize = keys.length;
        int tsize = vsize + (vsize >> 1);
        Key[] used = new Key[tsize];

        LOOP: for (;;) {
            for (Key key : keys) {
                int hash = hash(key);
                if (hash >= used.length) {
                    tsize = hash + 1;
                    used = new Key[tsize];
                    continue LOOP;
                }

                if (used[hash] != null) {
                    resolve(key, used[hash], kvals, min);
                    Arrays.fill(used, null);
                    continue LOOP;
                }

                used[hash] = key;
            }

            table = new int[tsize];
            Arrays.fill(table, -1);
            for (Map.Entry<String, Key> e : map.entrySet()) {
                Key m = e.getValue();
                int hash = hash(m);
                if (table[hash] != -1) {
                    throw new IllegalStateException("Failed to generate perfect hash.");
                }
                table[hash] = m.value;
            }

            return;
        }
    }
}
