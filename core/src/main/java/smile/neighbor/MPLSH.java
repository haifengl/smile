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
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import smile.math.IntArrayList;
import smile.math.Math;
import smile.sort.HeapSelect;
import smile.stat.distribution.GaussianDistribution;

/**
 * Multi-Probe Locality-Sensitive Hashing. LSH is an efficient algorithm for
 * approximate nearest neighbor search in high dimensional spaces
 * by performing probabilistic dimension reduction of data. The basic idea
 * is to hash the input items so that similar items are mapped to the same
 * buckets with high probability (the number of buckets being much smaller
 * than the universe of possible input items). A drawback of LSH is the
 * requirement for a large number of hash tables in order to achieve good
 * search quality. Multi-probe LSH is designed to overcome this drawback.
 * Multi-probe LSH intelligently probes multiple buckets that are likely to
 * contain query results in a hash table.
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
 * TODO: not efficient. better not use it right now.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Qin Lv, William Josephson, Zhe Wang, Moses Charikar, and Kai Li. Multi-probe LSH: efficient indexing for high-dimensional similarity search. VLDB, 2007. </li>
 * <li> Alexis Joly and Olivier Buisson. A posteriori multi-probe locality sensitive hashing. ACM international conference on Multimedia, 2008. </li>
 * </ol>
 *
 * @see LSH
 *
 * @param <E> the type of data objects in the hash table.
 *
 * @author Haifeng Li
 */
public class MPLSH <E> implements NearestNeighborSearch<double[], E>, KNNSearch<double[], E>, RNNSearch<double[], E> {
    /**
     * The entry in the hash table.
     */
    class HashEntry {

        /**
         * The bucket id given by the universal bucket hashing.
         */
        int bucket;
        /**
         * The index of object in the data set.
         */
        int index;
        /**
         * The key of data object.
         */
        double[] key;
        /**
         * The data object.
         */
        E data;

        /**
         * Constructor
         */
        HashEntry(int bucket, int index, double[] x, E data) {
            this.bucket = bucket;
            this.index = index;
            this.key = x;
            this.data = data;
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
         * The minimum values of hashing functions for given dataset.
         */
        double[] umin;
        /**
         * The maximum values of hashing functions for given dataset.
         */
        double[] umax;
        /**
         * Hash table.
         */
        ArrayList<HashEntry>[] table;

        /**
         * Constructor.
         */
        @SuppressWarnings("unchecked")
        Hash() {
            a = new double[k][d];
            b = new double[k];
            umin = new double[k];
            umax = new double[k];

            Arrays.fill(umin, Double.POSITIVE_INFINITY);
            Arrays.fill(umax, Double.NEGATIVE_INFINITY);

            GaussianDistribution gaussian = GaussianDistribution.getInstance();
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < d; j++) {
                    a[i][j] = gaussian.rand();
                }

                b[i] = Math.random(0, r);
            }

            ArrayList<HashEntry> list = new ArrayList<>();
            table = (ArrayList<HashEntry>[]) java.lang.reflect.Array.newInstance(list.getClass(), H);
        }

        /**
         * Returns the raw hash value of given vector x.
         * @param x the vector to be hashed.
         * @param m the m-<i>th</i> hash function to be employed.
         * @return the raw hash value.
         */
        double hash(double[] x, int m) {
            double g = b[m];
            for (int j = 0; j < d; j++) {
                g += a[m][j] * x[j];
            }
            return g / r;
        }

        /**
         * Apply hash functions on given vector x.
         * @param x the vector to be hashed.
         * @return the bucket of hash table for given vector x.
         */
        int hash(double[] x) {
            long g = 0;
            for (int i = 0; i < k; i++) {
                double gi = hash(x, i);

                if (gi < umin[i]) {
                    umin[i] = gi;
                }

                if (gi > umax[i]) {
                    umax[i] = gi;
                }

                g += c[i] * (int) Math.floor(gi);
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
        void add(int index, double[] x, E data) {
            int bucket = hash(x);
            int i = bucket % H;

            if (table[i] == null) {
                table[i] = new ArrayList<>();
            }

            table[i].add(new HashEntry(bucket, index, x, data));
        }
    }
    
    /**
     * Probability for given query object and hash function.
     */
    static class PrH implements Comparable<PrH> {

        /**
         * The index of bucket.
         */
        int u;
        /**
         * The probability
         */
        double pr;

        @Override
        public int compareTo(PrH o) {
            // to sort PrH in decreasing order.
            return (int) Math.signum(o.pr - pr);
        }
    }

    /**
     * Probability list of all buckets for given query object.
     */
    static class PrZ implements Comparable<PrZ> {

        /**
         * The index of hash function.
         */
        int m;
        /**
         * The n_i probabilities for h_m hash function,
         * where n_i = u_i_max - u_i_min + 1.
         */
        PrH[] prh;

        @Override
        public int compareTo(PrZ o) {
            // to sort PrZ in decreasing order.
            return prh[0].compareTo(o.prh[0]);
        }
    }

    /**
     * The training samples.
     */
    static class TrainSample {

        /**
         * The query object.
         */
        double[] query;
        /**
         * Neighbors of query object in terms of kNN or range search.
         */
        ArrayList<double[]> neighbors;
    }

    /**
     * Gaussian model of hash values of nearest neighbor.
     */
    static class NeighborHashValueModel {

        /**
         * Hash values of query object.
         */
        double[] H;
        /**
         * Mean of hash values of neighbors.
         */
        double[] mean;
        /**
         * Variance of hash values of neighbors.
         */
        double[] var;

        /**
         * Constructor.
         */
        NeighborHashValueModel(double[] H, double[] mean, double[] var) {
            this.H = H;
            this.mean = mean;
            this.var = var;
        }
    }

    class HashValueParzenModel {

        /**
         * Gaussian kernel for Parzen window estimation.
         */
        GaussianDistribution gaussian;
        /**
         * Query object's neighbor hash values model.
         */
        NeighborHashValueModel[] neighborHashValueModels;
        /**
         * Estimated conditional mean
         */
        double mean;
        /**
         * Estimated conditional standard de
         */
        double std;

        /**
         * Constructor.
         */
        HashValueParzenModel(Hash hash, TrainSample[] samples, double sigma) {
            gaussian = new GaussianDistribution(0, sigma);

            int n = 0;
            for (int i = 0; i < samples.length; i++) {
                if (samples[i].neighbors.size() > 1) {
                    n++;
                }
            }

            neighborHashValueModels = new NeighborHashValueModel[n];
            int l = 0;
            for (TrainSample sample : samples) {
                if (sample.neighbors.size() > 1) {
                    double[] H = new double[k];
                    double[] mu = new double[k];
                    double[] var = new double[k];

                    for (int i = 0; i < k; i++) {
                        H[i] = hash.hash(sample.query, i);

                        double sum = 0.0;
                        double sumsq = 0.0;
                        for (double[] v : sample.neighbors) {
                            double h = hash.hash(v, i);
                            sum += h;
                            sumsq += h * h;
                        }

                        mu[i] = sum / sample.neighbors.size();
                        var[i] = sumsq / sample.neighbors.size() - mu[i] * mu[i];
                    }

                    neighborHashValueModels[l++] = new NeighborHashValueModel(H, mu, var);
                }
            }
        }

        /**
         * Given a hash value h, estimate the Gaussian model (mean and variance)
         * of neighbors existing in the corresponding bucket.
         * @param m the index of hash function.
         * @param h the given hash value.
         */
        void estimate(int m, double h) {
            double mm = 0.0, vv = 0.0, ss = 0.0;
            for (int i = 0; i < neighborHashValueModels.length; i++) {
                double k = gaussian.p(neighborHashValueModels[i].H[m] - h);
                mm += k * neighborHashValueModels[i].mean[m];
                vv += k * neighborHashValueModels[i].var[m];
                ss += k;
            }

            if (ss > 1E-7) {
                mean = mm / ss;
                std = Math.sqrt(vv / ss);
            } else {
                mean = h;
                std = 0.0;
            }

            if (std < 1E-5) {
                std = 0.0;
                for (int i = 0; i < neighborHashValueModels.length; i++) {
                    std += neighborHashValueModels[i].var[m];
                }
                std = Math.sqrt(std / neighborHashValueModels.length);
            }
        }
    }

    /**
     * Pre-computed posteriori probabilities for generating multiple probes.
     */
    class PosterioriModel {

        /**
         * The hash function to model.
         */
        Hash hash;
        /**
         * The posteriori probabilities lookup table.
         */
        PrH[][][] lookup;

        /**
         * Constructor.
         * @param hash the hash function.
         * @param samples the training samples.
         * @param Nz the size of lookup table.
         * @param sigma the Parzen window width.
         */
        PosterioriModel(Hash hash, TrainSample[] samples, int Nz, double sigma) {
            this.hash = hash;

            HashValueParzenModel parzen = new HashValueParzenModel(hash, samples, sigma);
            lookup = new PrH[k][][];

            // for each component u
            for (int m = 0; m < k; m++) {
                int minh = (int) Math.floor(hash.umin[m]);
                int maxh = (int) Math.floor(hash.umax[m]); // min & max inclusive
                int size = Math.min(maxh - minh + 1, Nz);
                double delta = (maxh - minh) / (double) size;

                lookup[m] = new PrH[size][];

                // for each quantum of u(q)
                for (int n = 0; n < size; n++) {
                    parzen.estimate(m, minh + (n + 0.5) * delta);
                    GaussianDistribution gaussian = new GaussianDistribution(parzen.mean, parzen.std);

                    // This is the original method. However, a losts of h values
                    // will have very small probability and are essentially not useful.
                    /*
                    lookup[m][n] = new PrH[size];
                    for (int h = 0; h < size; ++h) {
                        int u = h + minh;
                        lookup[m][n][h] = new PrH();
                        lookup[m][n][h].u = u;
                        lookup[m][n][h].pr = gaussian.cdf(u + 1) - gaussian.cdf(u);
                    }
                     */

                    // Here we only generate those h values with reasonably large probability
                    ArrayList<PrH> probs = new ArrayList<>();
                    int h0 = (int) Math.floor(parzen.mean);
                    for (int h = h0;; h++) {
                        PrH prh = new PrH();
                        prh.u = h;
                        prh.pr = gaussian.cdf(h + 1) - gaussian.cdf(h);
                        if (prh.pr < 1E-7) {
                            break;
                        }
                        probs.add(prh);
                    }

                    for (int h = h0 - 1;; h--) {
                        PrH prh = new PrH();
                        prh.u = h;
                        prh.pr = gaussian.cdf(h + 1) - gaussian.cdf(h);
                        if (prh.pr < 1E-7) {
                            break;
                        }
                        probs.add(prh);
                    }

                    lookup[m][n] = probs.toArray(new PrH[probs.size()]);
                    Arrays.sort(lookup[m][n]);
                }
            }
        }

        /**
         * Generate query-directed probes.
         * @param x the query object.
         * @param recall the threshold of global probability of probes as a
         * quality control parameter.
         * @param T the maximum number of probes.
         * @return the list of probe buckets.
         */
        IntArrayList getProbeSequence(double[] x, double recall, int T) {
            PrZ[] pz = new PrZ[k];

            for (int i = 0; i < k; i++) {
                double h = hash.hash(x, i);

                double hmin = h - hash.umin[i];
                if (hmin < 0.0) {
                    hmin = 0.0;
                    //throw new IllegalArgumentException("hash[" + i + "] out of range " + h + " < umin = " + hash.umin[i]);
                }

                if (h > hash.umax[i]) {
                    hmin = hash.umax[i] - hash.umin[i];
                    //throw new IllegalArgumentException("hash[" + i + "] out of range " + h + " > umax = " + hash.umax[i]);
                }

                int qh = (int) (hmin * lookup[i].length / (hash.umax[i] - hash.umin[i] + 1));

                pz[i] = new PrZ();
                pz[i].m = i;
                pz[i].prh = lookup[i][qh];
            }

            Arrays.sort(pz);

            // generate probe sequence
            IntArrayList seq = new IntArrayList();
            seq.add(hash.hash(x));

            int[] range = new int[k];
            for (int i = 0; i < k; i++) {
                range[i] = pz[i].prh.length;
            }

            PriorityQueue<Probe> heap = new PriorityQueue<>();
            heap.add(new Probe(range));

            heap.peek().setProb(pz);
            double pr = heap.peek().prob;
            seq.add(heap.peek().hash(hash, pz));

            heap.peek().bucket[0] = 0;
            heap.peek().last = 0;
            heap.peek().setProb(pz);

            while (!heap.isEmpty() && pr < recall && seq.size() < T) {
                Probe p = heap.poll();

                seq.add(p.hash(hash, pz));
                pr += p.prob;

                if (p.isShiftable()) {
                    Probe p2 = p.shift();
                    p2.setProb(pz);
                    heap.offer(p2);
                }

                if (p.isExpandable()) {
                    Probe p2 = p.expand();
                    p2.setProb(pz);
                    heap.offer(p2);
                }

                if (p.isExtendable()) {
                    Probe p2 = p.extend();
                    p2.setProb(pz);
                    heap.offer(p2);
                }
            }

            return seq;
        }
    }

    /**
     * Probe to check for nearest neighbors.
     */
    class Probe implements Comparable<Probe> {

        /**
         * The valid range of buckets.
         */
        int[] range;
        /**
         * The bucket for probing.
         */
        int[] bucket;
        /**
         * The last non-zero component.
         */
        int last;
        /**
         * The probability of this probe.
         */
        double prob;

        /**
         * Constructor.
         */
        Probe(int[] range) {
            this.range = range;
            bucket = new int[range.length];
            last = 0;
        }

        boolean isShiftable() {
            if (bucket[last] != 1) {
                return false;
            }
            if (last + 1 >= bucket.length) {
                return false;
            }
            if (range[last + 1] <= 1) {
                return false;
            }
            return true;
        }

        /**
         * This operation shifts to the right the last nonzero component if
         * it is equal to one and if it is not the last one.
         */
        Probe shift() {
            Probe p = new Probe(range);
            p.last = last;
            System.arraycopy(bucket, 0, p.bucket, 0, bucket.length);

            p.bucket[last] = 0;
            p.last++;
            p.bucket[last] = 1;
            return p;
        }

        boolean isExpandable() {
            if (last + 1 >= bucket.length) {
                return false;
            }
            if (range[last + 1] <= 1) {
                return false;
            }
            return true;
        }

        /**
         * This operation sets to one the component following the last nonzero
         * component if it is not the last one.
         */
        Probe expand() {
            Probe p = new Probe(range);
            p.last = last;
            System.arraycopy(bucket, 0, p.bucket, 0, bucket.length);

            p.last++;
            p.bucket[last] = 1;
            return p;
        }

        boolean isExtendable() {
            if (bucket[last] + 1 >= range[last]) {
                return false;
            }
            return true;
        }

        /**
         * This operation adds one to the last nonzero component.
         */
        Probe extend() {
            Probe p = new Probe(range);
            p.last = last;
            System.arraycopy(bucket, 0, p.bucket, 0, bucket.length);

            p.bucket[last]++;
            return p;
        }

        @Override
        public int compareTo(Probe o) {
            return (int) Math.signum(prob - o.prob);
        }

        /**
         * Calculate the probability of the probe.
         */
        void setProb(PrZ[] pz) {
            prob = 1.0;
            for (int i = 0; i < bucket.length; i++) {
                prob *= pz[i].prh[bucket[i]].pr;
            }
        }

        /**
         * Returns the bucket number of the probe.
         */
        int hash(Hash hash, PrZ[] pz) {
            long r = 0;

            for (int i = 0; i < k; i++) {
                r += c[pz[i].m] * pz[i].prh[bucket[i]].u;
            }

            int h = (int) (r % P);
            if (h < 0) {
                h += P;
            }

            return h;
        }
    }

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
    double r;
    /**
     * The random integer used for universal bucket hashing.
     */
    int[] c;
    /**
     * The prime number in universal bucket hashing.
     */
    int P = 2147483647;
    /**
     * Whether to exclude query object self from the neighborhood.
     */
    boolean identicalExcluded = true;
    /**
     * Pre-computed posteriori lookup table to generate multiple probes.
     */
    private List<PosterioriModel> model;

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions, which is usually
     * set to log(N) where N is the dataset size.
     * @param r the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an r value that is too large, which
     * will increase the query time.
     */
    public MPLSH(int d, int L, int k, double r) {
        this(d, L, k, r, 1017881);
    }

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions, which is usually
     * set to log(N) where N is the dataset size.
     * @param r the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an r value that is too large, which
     * will increase the query time.
     * @param H the number of buckets of hash tables.
     */
    public MPLSH(int d, int L, int k, double r, int H) {
        if (d < 2) {
            throw new IllegalArgumentException("Invalid input space dimension: " + d);
        }

        if (L < 1) {
            throw new IllegalArgumentException("Invalid number of hash tables: " + L);
        }

        if (k < 1) {
            throw new IllegalArgumentException("Invalid number of random projections per hash value: " + k);
        }

        if (r <= 0.0) {
            throw new IllegalArgumentException("Invalid width of random projections: " + r);
        }

        if (H < 1) {
            throw new IllegalArgumentException("Invalid size of hash tables: " + H);
        }

        this.d = d;
        this.L = L;
        this.k = k;
        this.r = r;
        this.H = H;

        keys = new ArrayList<>();
        data = new ArrayList<>();
        c = new int[k];
        for (int i = 0; i < c.length; i++) {
            c[i] = Math.randomInt(P);
        }

        hash = new ArrayList<>(L);
        for (int i = 0; i < L; i++) {
            hash.add(new Hash());
        }
    }

    @Override
    public String toString() {
        return String.format("Multi-Probe LSH (L=%d, k=%d, H=%d, w=%.4f)", hash.size(), k, H, r);
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
    public MPLSH<E> setIdenticalExcluded(boolean excluded) {
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
            h.add(index, key, value);
        }
    }

    /**
     * Train the posteriori multiple probe algorithm.
     * @param range the neighborhood search data structure.
     * @param radius the radius for range search.
     * @param samples the training samples.
     */
    public void learn(RNNSearch<double[], double[]> range, double[][] samples, double radius) {
        learn(range, samples, radius, 2500);
    }

    /**
     * Train the posteriori multiple probe algorithm.
     * @param range the neighborhood search data structure.
     * @param radius the radius for range search.
     * @param Nz the number of quantized values.
     */
    public void learn(RNNSearch<double[], double[]> range, double[][] samples, double radius, int Nz) {
        learn(range, samples, radius, Nz, 0.2);
    }

    /**
     * Train the posteriori multiple probe algorithm.
     * @param range the neighborhood search data structure.
     * @param radius the radius for range search.
     * @param Nz the number of quantized values.
     * @param sigma the Parzen window width.
     */
    public void learn(RNNSearch<double[], double[]> range, double[][] samples, double radius, int Nz, double sigma) {
        TrainSample[] training = new TrainSample[samples.length];
        for (int i = 0; i < samples.length; i++) {
            training[i] = new TrainSample();
            training[i].query = samples[i];
            training[i].neighbors = new ArrayList<>();
            ArrayList<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
            range.range(training[i].query, radius, neighbors);
            for (Neighbor<double[], double[]> n : neighbors) {
                training[i].neighbors.add(keys.get(n.index));
            }
        }

        model = new ArrayList<>(hash.size());
        for (int i = 0; i < hash.size(); i++) {
            model.add(new PosterioriModel(hash.get(i), training, Nz, sigma));
        }
    }

    @Override
    public Neighbor<double[], E> nearest(double[] q) {
        return nearest(q, 0.95, 100);
    }

    /**
     * Returns the approximate nearest neighbor. A posteriori multiple probe
     * model has to be trained already.
     * @param q the query object.
     * @param recall the expected recall rate.
     * @param T the maximum number of probes.
     */
    public Neighbor<double[], E> nearest(double[] q, double recall, int T) {
        if (recall > 1 || recall < 0) {
            throw new IllegalArgumentException("Invalid recall: " + recall);
        }

        double alpha = 1 - Math.pow(1 - recall, 1.0 / hash.size());

        IntArrayList candidates = new IntArrayList();
        for (int i = 0; i < hash.size(); i++) {
            IntArrayList buckets = model.get(i).getProbeSequence(q, alpha, T);

            for (int j = 0; j < buckets.size(); j++) {
                int bucket = buckets.get(j);
                ArrayList<HashEntry> bin = hash.get(i).table[bucket % H];
                if (bin != null) {
                    for (HashEntry e : bin) {
                        if (e.bucket == bucket) {
                            if (q != e.key || !identicalExcluded) {
                                candidates.add(e.index);
                            }
                        }
                    }
                }
            }
        }
        
        Neighbor<double[], E> neighbor = new Neighbor<>(null, null, -1, Double.MAX_VALUE);

        int[] cand = candidates.toArray();
        Arrays.sort(cand);
        int prev = -1;
        for (int index : cand) {
            if (index == prev) {
                continue;
            } else {
                prev = index;
            }

            double[] key = keys.get(index);
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
        return knn(q, k, 0.95, 100);
    }

    /**
     * Returns the approximate k-nearest neighbors. A posteriori multiple probe
     * model has to be trained already.
     * @param q the query object.
     * @param k	the number of nearest neighbors to search for.
     * @param recall the expected recall rate.
     * @param T the maximum number of probes.
     */
    public Neighbor<double[], E>[] knn(double[] q, int k, double recall, int T) {
        if (recall > 1 || recall < 0) {
            throw new IllegalArgumentException("Invalid recall: " + recall);
        }

        if (k < 1) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        double alpha = 1 - Math.pow(1 - recall, 1.0 / hash.size());

        int hit = 0;
        IntArrayList candidates = new IntArrayList();
        for (int i = 0; i < hash.size(); i++) {
            IntArrayList buckets = model.get(i).getProbeSequence(q, alpha, T);

            for (int j = 0; j < buckets.size(); j++) {
                int bucket = buckets.get(j);
                ArrayList<HashEntry> bin = hash.get(i).table[bucket % H];
                if (bin != null) {
                    for (HashEntry e : bin) {
                        if (e.bucket == bucket) {
                            if (q != e.key || identicalExcluded) {
                                candidates.add(e.index);
                            }
                        }
                    }
                }
            }
        }

        int[] cand = candidates.toArray();
        Arrays.sort(cand);

        Neighbor<double[], E> neighbor = new Neighbor<>(null, null, 0, Double.MAX_VALUE);
        @SuppressWarnings("unchecked")
        Neighbor<double[], E>[] neighbors = (Neighbor<double[], E>[]) java.lang.reflect.Array.newInstance(neighbor.getClass(), k);
        HeapSelect<Neighbor<double[], E>> heap = new HeapSelect<>(neighbors);
        for (int i = 0; i < k; i++) {
            heap.add(neighbor);
        }

        int prev = -1;
        for (int index : cand) {
            if (index == prev) {
                continue;
            } else {
                prev = index;
            }

            double[] key = keys.get(index);
            double dist = Math.distance(q, key);
            if (dist < heap.peek().distance) {
                heap.add(new Neighbor<>(key, data.get(index), index, dist));
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
        range(q, radius, neighbors, 0.95, 100);
    }

    /**
     * Search the neighbors in the given radius of query object, i.e.
     * d(x, v) &le; radius.
     *
     * @param q the query object.
     * @param radius the radius of search range.
     * @param neighbors the list to store found neighbors in the given range on output.
     * @param recall the expected recall rate.
     * @param T the maximum number of probes.
     */
    public void range(double[] q, double radius, List<Neighbor<double[], E>> neighbors, double recall, int T) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        if (recall > 1 || recall < 0) {
            throw new IllegalArgumentException("Invalid recall: " + recall);
        }

        double alpha = 1 - Math.pow(1 - recall, 1.0 / hash.size());

        for (int i = 0; i < hash.size(); i++) {
            IntArrayList buckets = model.get(i).getProbeSequence(q, alpha, T);

            for (int j = 0; j < buckets.size(); j++) {
                int bucket = buckets.get(j);
                ArrayList<HashEntry> bin = hash.get(i).table[bucket % H];
                if (bin != null) {
                    for (HashEntry e : bin) {
                        if (e.bucket == bucket) {
                            if (q == e.key && identicalExcluded) {
                                continue;
                            }

                            double distance = Math.distance(q, e.key);
                            if (distance <= radius) {
                                boolean existed = false;
                                for (Neighbor<double[], E> n : neighbors) {
                                    if (e.index == n.index) {
                                        existed = true;
                                        break;
                                    }
                                }

                                if (!existed) {
                                    neighbors.add(new Neighbor<>(e.key, e.data, e.index, distance));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
