/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.neighbor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import smile.math.IntArrayList;
import smile.math.Math;
import smile.sort.HeapSelect;
import smile.stat.distribution.GaussianDistribution;

/**
 * Locality-Sensitive Hashing. LSH is an efficient algorithm for
 * approximate nearest neighbor search in high dimensional spaces
 * by performing probabilistic dimension reduction of data. The basic idea
 * is to hash the input items so that similar items are mapped to the same
 * buckets with high probability (the number of buckets being much smaller
 * than the universe of possible input items).
 * <p>
 * By default, the query object (reference equality) is excluded from the neighborhood.
 * You may change this behavior with <code>setIdenticalExcluded</code>. Note that
 * you may observe weird behavior with String objects. JVM will pool the string literal
 * objects. So the below variables
 * <code>
 *     String a = "ABC";
 *     String b = "ABC";
 *     String c = "AB" + "C";
 * </code>
 * are actually equal in reference test <code>a == b == c</code>. With toy data that you
 * type explicitly in the code, this will cause problems. Fortunately, the data would be
 * read from secondary storage in production.
 * </p>
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
public class LSH <E> implements NearestNeighborSearch<double[], E>, KNNSearch<double[], E>, RNNSearch<double[], E> {
    /**
     * A bucket is a container for points that all have the same value for hash
     * function g (function g is a vector of k LSH functions). A bucket is specified by a vector in integers of length k.
     */
    static class BucketEntry {

        /**
         * The bucket numbers given by the universal bucket hashing.
         * These numbers are used instead of the full k-vector (value of the hash
         * function g) describing the bucket. With a high probability all
         * buckets will have different pairs of numbers.
         */
        int bucket;

        /**
         * The indices of points that all have the same value for hash function g.
         */
        IntArrayList entry;

        /**
         * Constructor.
         * @param bucket the bucket number given by universal hashing.
         */
        BucketEntry(int bucket) {
            this.bucket = bucket;
            entry = new IntArrayList();
        }

        /**
         * Adds a point to bucket.
         * @param point the index of point.
         */
        void add(int point) {
            entry.add(point);
        }

        /**
         * Removes a point from bucket.
         * @param point the index of point.
         * @return true if the point was in the bucket.
         */
        boolean remove(int point) {
            int n = entry.size();
            for (int i = 0; i < n; i++) {
                if (entry.get(i) == point) {
                    entry.remove(i);
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * The entry of an universal hash table with collision solved by chaining.
     */
    static class HashEntry {

        /**
         * The chain of buckets in the slot.
         */
        List<BucketEntry> entry;

        /**
         * Constructor.
         */
        HashEntry() {
            entry = new LinkedList<>();
        }

        /**
         * Adds a point to given bucket.
         * @param bucket the bucket number.
         * @param point the index of point.
         */
        void add(int bucket, int point) {
            for (BucketEntry b : entry) {
                if (b.bucket == bucket) {
                    b.add(point);
                    return;
                }
            }

            BucketEntry b = new BucketEntry(bucket);
            b.add(point);
            entry.add(b);
        }

        /**
         * Removes a point from given bucket.
         * @param bucket the bucket number.
         * @param point the index of point.
         * @return true if the point was in the bucket.
         */
        boolean remove(int bucket, int point) {
            for (BucketEntry b : entry) {
                if (b.bucket == bucket) {
                    return b.remove(point);
                }
            }

            return false;
        }
    }

    /**
     * The hash function for data in Euclidean spaces.
     */
    class Hash {

        /**
         * The random vectors with entries chosen independently from a Gaussian
         * distribution.
         */
        double[][] a;
        /**
         * Real numbers chosen uniformly from the range [0, w].
         */
        double[] b;
        /**
         * Hash table.
         */
        HashEntry[] table;

        /**
         * Constructor.
         */
        Hash() {
            a = new double[k][d];
            b = new double[k];

            GaussianDistribution gaussian = GaussianDistribution.getInstance();
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < d; j++) {
                    a[i][j] = gaussian.rand();
                }

                b[i] = Math.random(0, w);
            }

            table = new HashEntry[H];
        }

        /**
         * Returns the hash value of given vector x.
         * @param x the vector to be hashed.
         * @param m the m-<i>th</i> hash function to be employed.
         * @return the hash value.
         */
        int hash(double[] x, int m) {
            double g = b[m];
            for (int j = 0; j < d; j++) {
                g += a[m][j] * x[j];
            }

            int h = (int) Math.floor(g / w);
            if (h < 0) {
                h += 2147483647;
            }

            return h;
        }

        /**
         * Apply hash functions on given vector x.
         * @param r universal hashing random integers.
         * @param x the vector to be hashed.
         * @return the bucket of hash table for given vector x.
         */
        int hash(int[] r, double[] x) {
            long g = 0;
            for (int i = 0; i < k; i++) {
                g += r[i] * hash(x, i);
            }

            int h = (int) (g % P);
            if (h < 0) {
                h += P;
            }

            return h;
        }

        /**
         * Insert an item into the hash table.
         */
        void add(int index, double[] x) {
            int bucket = hash(r2, x);
            int i = hash(r1, x) % H;

            if (table[i] == null) {
                table[i] = new HashEntry();
            }

            table[i].add(bucket, index);
        }

        /**
         * Returns the bucket entry for the given point.
         */
        BucketEntry get(double[] x) {
            int bucket = hash(r2, x);
            int i = hash(r1, x) % H;

            HashEntry he = table[i];
            if (he == null) {
                return null;
            }

            for (BucketEntry be : he.entry) {
                if (bucket == be.bucket) {
                    return be;
                }
            }

            return null;
        }
    }

    /**
     * The prime number in universal bucket hashing.
     */
    final int P = 2147483647;
    /**
     * The range of universal hashing random integers [0, 2^29).
     */
    final int MAX_HASH_RND = 536870912;
    /**
     * The keys of data objects.
     */
    ArrayList<double[]> keys;
    /**
     * The data objects.
     */
    ArrayList<E> data;
    /**
     * Hash functions.
     */
    List<Hash> hash;
    /**
     * The size of hash table.
     */
    int H;
    /**
     * The dimensionality of data.
     */
    int d;
    /**
     * The number of hash tables.
     */
    int L;
    /**
     * The number of random projections per hash value.
     */
    int k;
    /**
     * The width of projection. The hash function is defined as floor((a * x + b) / w). The value
     * of w determines the bucket interval.
     */
    double w;
    /**
     * The random integer used for universal bucket hashing.
     */
    int[] r1;
    /**
     * The random integer used for universal hashing for control values.
     */
    int[] r2;
    /**
     * Whether to exclude query object self from the neighborhood.
     */
    boolean identicalExcluded = true;

    /**
     * Constructor.
     * @param keys the keys of data objects.
     * @param data the data objects.
     */
    public LSH(double[][] keys, E[] data) {
        this(keys, data, 4.0);
    }

    /**
     * Constructor.
     * @param keys the keys of data objects.
     * @param data the data objects.
     * @param w the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an w value that is too large, which
     * will increase the query time.
     */
    public LSH(double[][] keys, E[] data, double w) {
        this(keys, data, w, keys.length);
    }

    /**
     * Constructor.
     * @param keys the keys of data objects.
     * @param data the data objects.
     * @param w the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an w value that is too large, which
     * will increase the query time.
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
     * set to log(N) where N is the dataset size.
     */
    public LSH(int d, int L, int k) {
        this(d, L, k, 4.0);
    }

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions, which is usually
     * set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an w value that is too large, which
     * will increase the query time.
     */
    public LSH(int d, int L, int k, double w) {
        this(d, L, k, w, 1017881);
    }

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions, which is usually
     * set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an w value that is too large, which
     * will increase the query time.
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

        this.d = d;
        this.L = L;
        this.k = k;
        this.w = w;
        this.H = H;

        keys = new ArrayList<>();
        data = new ArrayList<>();
        r1 = new int[k];
        r2 = new int[k];
        for (int i = 0; i < k; i++) {
            r1[i] = Math.randomInt(MAX_HASH_RND);
            r2[i] = Math.randomInt(MAX_HASH_RND);
        }

        hash = new ArrayList<>(L);
        for (int i = 0; i < L; i++) {
            hash.add(new Hash());
        }
    }

    @Override
    public String toString() {
        return String.format("LSH (L=%d, k=%d, H=%d, w=%.4f)", hash.size(), k, H, w);
    }

    /**
     * Get whether if query object self be excluded from the neighborhood.
     */
    public boolean isIdenticalExcluded() {
        return identicalExcluded;
    }

    /**
     * Set if exclude query object self from the neighborhood.
     */
    public LSH<E> setIdenticalExcluded(boolean excluded) {
        identicalExcluded = excluded;
        return this;
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
        Set<Integer> candidates = obtainCandidates(q);
        Neighbor<double[], E> neighbor = new Neighbor<>(null, null, -1, Double.MAX_VALUE);
        for (int index : candidates) {
            double[] key = keys.get(index);
            if (q == key && identicalExcluded) {
                continue;
            }
            double distance = Math.distance(q, key);
            if (distance < neighbor.distance) {
                neighbor.index = index;
                neighbor.distance = distance;
                neighbor.key = key;
                neighbor.value = data.get(index);
            }
        }
        
        return neighbor;
    }

    @Override
    public Neighbor<double[], E>[] knn(double[] q, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }
        Set<Integer> candidates = obtainCandidates(q);
        Neighbor<double[], E> neighbor = new Neighbor<>(null, null, 0, Double.MAX_VALUE);
        @SuppressWarnings("unchecked")
        Neighbor<double[], E>[] neighbors = (Neighbor<double[], E>[]) java.lang.reflect.Array.newInstance(neighbor.getClass(), k);
        HeapSelect<Neighbor<double[], E>> heap = new HeapSelect<>(neighbors);
        for (int i = 0; i < k; i++) {
            heap.add(neighbor);
        }

        int hit = 0;
        for (int index : candidates) {
            double[] key = keys.get(index);
            if (q == key && identicalExcluded) {
                continue;
            }

            double distance = Math.distance(q, key);
            if (distance < heap.peek().distance) {
                heap.add(new Neighbor<>(key, data.get(index), index, distance));
                hit++;
            }
        }
        
        heap.sort();

        if (hit < k) {
            @SuppressWarnings("unchecked")
            Neighbor<double[], E>[] n2 = (Neighbor<double[], E>[]) java.lang.reflect.Array.newInstance(neighbor.getClass(), hit);
            int start = k - hit;
            for (int i = 0; i < hit; i++) {
                n2[i] = neighbors[i + start];
            }
            neighbors = n2;
        }

        return neighbors;
    }

    @Override
    public void range(double[] q, double radius, List<Neighbor<double[], E>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }
        Set<Integer> candidates = obtainCandidates(q);
        for (int index : candidates) {
            double[] key = keys.get(index);
            if (q == key && identicalExcluded) {
                continue;
            }

            double distance = Math.distance(q, key);
            if (distance <= radius) {
                neighbors.add(new Neighbor<>(key, data.get(index), index, distance));
            }
        }
    }

    /**
     * Obtaining Candidates
     * @return Indices of Candidates
     */
    private Set<Integer> obtainCandidates(double[] q) {
        Set<Integer> candidates = new LinkedHashSet<>();
        for (Hash h : hash) {
            BucketEntry bucket = h.get(q);
            if (bucket != null) {
                int m = bucket.entry.size();
                for (int i = 0; i < m; i++) {
                    int index = bucket.entry.get(i);
                    candidates.add(index);
                }
            }
        }
        return candidates;
    }
}
