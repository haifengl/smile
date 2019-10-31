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

package smile.neighbor.lsh;

import smile.hash.MurmurHash2;

import java.nio.ByteBuffer;

/**
 * SimHash is a technique for quickly estimating how similar two sets are.
 * The algorithm is used by the Google Crawler to find near duplicate pages.
 */
public interface SimHash<T> {
    long hash(T x);

    /** Returns the simhash for a set of generic features (represented as byte[]). */
    static SimHash<int[]> of(byte[][] features) {
        int n = features.length;
        long[] hash = new long[n];
        for (int i = 0; i < n; i++) {
            ByteBuffer buffer = ByteBuffer.wrap(features[i]);
            hash[i] = MurmurHash2.hash64(buffer, 0, features[i].length, 0);
        }

        return new SimHash<int[]>() {
            @Override
            public long hash(int[] weight) {
                if (weight.length != n) {
                    throw new IllegalArgumentException("Invalid weight vector size");
                }

                final int BITS = 64;
                int[] count = new int[n];
                for (int i = 0; i < n; i++) {
                    long h = hash[i];
                    int w = weight[i];

                    for (int j = 0; j < BITS; j++) {
                        if (((h >>> i) & 1) == 1) {
                            count[j] += w;
                        } else {
                            count[j] -= w;
                        }
                    }
                }

                long bits = 0;
                long one = 1;
                for (int i = 0; i < BITS; i++) {
                    if (count[i] >= 0) {
                        bits |= one;
                    }
                    one <<= 1;
                }

                return bits;
            }
        };
    }

    /** Returns the simhash for string tokens. */
    static SimHash<String[]> text() {
        return tokens -> {
                final int BITS = 64;

                int[] bits = new int[BITS];
                for (String s : tokens) {
                    byte[] bytes = s.getBytes();
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    long hash = MurmurHash2.hash64(buffer, 0, bytes.length, 0);
                    for (int i = 0; i < BITS; i++) {
                        if (((hash >>> i) & 1) == 1) {
                            bits[i]++;
                        } else {
                            bits[i]--;
                        }
                    }
                }

                long hash = 0;
                long one = 1;
                for (int i = 0; i < BITS; i++) {
                    if (bits[i] >= 0) {
                        hash |= one;
                    }
                    one <<= 1;
                }
                return hash;
            };
    }
}
