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

package smile.neighbor;

import java.util.List;
import java.util.stream.Collectors;
import smile.neighbor.lsh.Bucket;
import smile.neighbor.lsh.Hash;

/** Mutable LSH. */
public class MutableLSH<E> extends LSH<E> {
    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions, which is usually
     *          set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently
     *          away from 0. But we should not choose an w value that is too
     *          large, which will increase the query time.
     */
    public MutableLSH(int d, int L, int k, double w) {
        super(d, L, k, w, 1017881);
    }

    @Override
    public void put(double[] key, E value) {
        int index = keys.size();
        for (int i = 0; i < index; i++) {
            if (keys.get(i) == null) {
                index = i;
                keys.set(i, key);
                data.set(i, value);
                break;
            }
        }

        if (index == keys.size()) {
            keys.add(key);
            data.add(value);
        }

        for (Hash h : hash) {
            h.add(index, key);
        }
    }

    /**
     * Remove an entry from the hash table.
     */
    public void remove(double[] key, E value) {
        int n = data.size();
        for (int i = 0; i < n; i++) {
            if (data.get(i) == value) {
                keys.set(i, null);
                data.set(i, null);

                for (Hash h : hash) {
                    Bucket bucket = h.get(key);
                    if (bucket == null) {
                        System.out.println("impossible");
                    }
                    bucket.remove(i);
                }
                return;
            }
        }

        throw new IllegalArgumentException("Remove non-exist element");
    }

    /**
     * Update an entry with new key. Note that the new key and old key
     * should not be the same object.
     */
    public void update(double[] key, E value) {
        int n = data.size();
        for (int i = 0; i < n; i++) {
            if (data.get(i) == value) {
                double[] oldKey = keys.get(i);
                keys.set(i, key);

                for (Hash h : hash) {
                    int oldBucket = h.hash(oldKey);
                    int newBucket = h.hash(key);
                    if (newBucket != oldBucket) {
                        h.get(oldBucket).remove(i);
                        h.get(newBucket).add(i);
                    }
                }
                return;
            }
        }

        throw new IllegalArgumentException("Update non-exist element");
    }

    /** Returns the keys. */
    public List<double[]> keys() {
        return keys.stream().filter(key -> key != null).collect(Collectors.toList());
    }

    /** Returns the values. */
    public List<E> values() {
        return data.stream().filter(value -> value != null).collect(Collectors.toList());
    }
}
