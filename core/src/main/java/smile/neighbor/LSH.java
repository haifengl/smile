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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import smile.neighbor.lsh.*;
import smile.util.IntArrayList;
import smile.math.MathEx;
import smile.sort.HeapSelect;

/**
 * Locality-Sensitive Hashing. LSH is an efficient algorithm for
 * approximate nearest neighbor search in high dimensional spaces
 * by performing probabilistic dimension reduction of data. The basic idea
 * is to hash the input items so that similar items are mapped to the same
 * buckets with high probability (the number of buckets being much smaller
 * than the universe of possible input items).
 * <p>
 * By default, the query object (reference equality) is excluded from the neighborhood.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Alexandr Andoni and Piotr Indyk. Near-Optimal Hashing Algorithms for Near Neighbor Problem in High Dimensions.  FOCS, 2006. </li>
 * <li> Alexandr Andoni, Mayur Datar, Nicole Immorlica, Piotr Indyk, and Vahab Mirrokni. Locality-Sensitive Hashing Scheme Based on p-Stable Distributions. 2004. </li>
 * </ol>
 *
 * @see MPLSH
 *
 * @param <E> the type of data objects in the hash table.
 *
 * @author Haifeng Li
 */
public class LSH <E> implements NearestNeighborSearch<double[], E>, KNNSearch<double[], E>, RNNSearch<double[], E>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The keys of data objects.
     */
    protected ArrayList<double[]> keys;
    /**
     * The data objects.
     */
    protected ArrayList<E> data;
    /**
     * Hash functions.
     */
    protected List<Hash> hash;
    /**
     * The size of hash table.
     */
    protected int H;
    /**
     * The number of random projections per hash value.
     */
    protected int k;
    /**
     * The width of projection. The hash function is defined as floor((a * x + b) / w). The value
     * of w determines the bucket interval.
     */
    protected double w;

    /**
     * Constructor.
     * @param keys the keys of data objects.
     * @param data the data objects.
     * @param w the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an w value that is too large, which
     * will increase the query time.
     */
    public LSH(double[][] keys, E[] data, double w) {
        this(keys, data, w, 1017881);
    }

    /**
     * Constructor.
     * @param keys the keys of data objects.
     * @param data the data objects.
     * @param w the width of random projections. It should be sufficiently
     *          away from 0. But we should not choose an w value that is too
     *          large, which will increase the query time.
     * @param H the size of universal hash tables.
     */
    public LSH(double[][] keys, E[] data, double w, int H) {
        this(keys[0].length, Math.max(50, (int) Math.pow(keys.length, 0.25)), Math.max(3, (int) Math.log10(keys.length)), w, H);

        if (keys.length != data.length) {
            throw new IllegalArgumentException("The array size of keys and data are different.");
        }

        if (H < keys.length) {
            throw new IllegalArgumentException("Hash table size is too small: " + H);
        }

        int n = keys.length;
        for (int i = 0; i < n; i++) {
            put(keys[i], data[i]);
        }
    }

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
    public LSH(int d, int L, int k, double w) {
        this(d, L, k, w, 1017881);
    }

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions, which is usually
     *          set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently
     *          away from 0. But we should not choose an w value that is too
     *          large, which will increase the query time.
     * @param H the size of universal hash tables.
     */
    public LSH(int d, int L, int k, double w, int H) {
        if (d < 2) {
            throw new IllegalArgumentException("Invalid input space dimension: " + d);
        }

        if (L < 1) {
            throw new IllegalArgumentException("Invalid number of hash tables: " + L);
        }

        if (k < 1) {
            throw new IllegalArgumentException("Invalid number of random projections per hash value: " + k);
        }

        if (w <= 0.0) {
            throw new IllegalArgumentException("Invalid width of random projections: " + w);
        }

        if (H < 1) {
            throw new IllegalArgumentException("Invalid size of hash tables: " + H);
        }

        this.k = k;
        this.w = w;
        this.H = H;

        keys = new ArrayList<>();
        data = new ArrayList<>();

        initHashTable(d, L, k, w, H);
    }

    /** Initialize the hash tables. */
    protected void initHashTable(int d, int L, int k, double w, int H) {
        hash = new ArrayList<>(L);
        for (int i = 0; i < L; i++) {
            hash.add(new Hash(d, k, w, H));
        }
    }

    @Override
    public String toString() {
        return String.format("LSH(L=%d, k=%d, H=%d, w=%.4f)", hash.size(), k, H, w);
    }

    /**
     * Insert an item into the hash table.
     */
    public void put(double[] key, E value) {
        int index = keys.size();
        keys.add(key);
        data.add(value);
        for (Hash h : hash) {
            h.add(index, key);
        }
    }

    @Override
    public Neighbor<double[], E> nearest(double[] q) {
        int index = -1;
        double nearest = Double.MAX_VALUE;

        for (int i : getCandidates(q)) {
            double[] x = keys.get(i);
            if (q != x) {
                double distance = MathEx.distance(q, x);
                if (distance < nearest) {
                    index = i;
                    nearest = distance;
                }
            }
        }
        
        return index == -1 ? null : new Neighbor<>(keys.get(index), data.get(index), index, nearest);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Neighbor<double[], E>[] knn(double[] q, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        Set<Integer> candidates = getCandidates(q);
        k = Math.min(k, candidates.size());

        HeapSelect<Neighbor<double[], E>> heap = new HeapSelect<>(new Neighbor[k]);

        for (int index : candidates) {
            double[] key = keys.get(index);
            if (q != key) {
                double distance = MathEx.distance(q, key);
                heap.add(new Neighbor<>(key, data.get(index), index, distance));
            }
        }
        
        heap.sort();
        return heap.toArray();
    }

    @Override
    public void range(double[] q, double radius, List<Neighbor<double[], E>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        for (int index : getCandidates(q)) {
            double[] key = keys.get(index);
            if (q != key) {
                double distance = MathEx.distance(q, key);
                if (distance <= radius) {
                    neighbors.add(new Neighbor<>(key, data.get(index), index, distance));
                }
            }
        }
    }

    /**
     * Returns the nearest neighbor candidates.
     * @return Indices of Candidates
     */
    private Set<Integer> getCandidates(double[] q) {
        Set<Integer> candidates = new LinkedHashSet<>();
        for (Hash h : hash) {
            Bucket bucket = h.get(q);
            if (bucket != null) {
                IntArrayList points = bucket.points();
                int n = points.size();
                for (int i = 0; i < n; i++) {
                    candidates.add(points.get(i));
                }
            }
        }
        return candidates;
    }
}
