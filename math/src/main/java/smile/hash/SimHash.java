/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.hash;

import java.nio.ByteBuffer;

/**
 * SimHash is a technique for quickly estimating how similar two sets are.
 * The algorithm is used by the Google Crawler to find near duplicate pages.
 *
 * @param <T> the data type of input objects.
 *
 * @author Haifeng Li
 */
public interface SimHash<T> {
    /**
     * Return the hash code.
     * @param x the object.
     * @return the hash code.
     */
    long hash(T x);

    /**
     * Returns the <code>SimHash</code> for a set of generic features (represented as byte[]).
     * @param features the generic features.
     * @return the <code>SimHash</code>.
     */
    static SimHash<int[]> of(byte[][] features) {
        int n = features.length;
        long[] hash = new long[n];
        for (int i = 0; i < n; i++) {
            ByteBuffer buffer = ByteBuffer.wrap(features[i]);
            hash[i] = MurmurHash2.hash64(buffer, 0, features[i].length, 0);
        }

        return weight -> {
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
        };
    }

    /**
     * Returns the <code>SimHash</code> for string tokens.
     * @return the <code>SimHash</code> for string tokens.
     */
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
