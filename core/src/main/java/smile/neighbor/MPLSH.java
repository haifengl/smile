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

import java.util.*;

import smile.neighbor.lsh.*;
import smile.util.IntArrayList;
import smile.math.MathEx;
import smile.sort.HeapSelect;

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
public class MPLSH <E> extends LSH<E> {
    private static final long serialVersionUID = 2L;

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
     * @param w the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an w value that is too large, which
     * will increase the query time.
     */
    public MPLSH(int d, int L, int k, double w) {
        this(d, L, k, w, 1017881);
    }

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions, which is usually
     * set to log(N) where N is the dataset size.
     * @param w the width of random projections. It should be sufficiently
     * away from 0. But we should not choose an r value that is too large, which
     * will increase the query time.
     * @param H the number of buckets of hash tables.
     */
    public MPLSH(int d, int L, int k, double w, int H) {
        super(d, L, k, w, H);
    }

    @Override
    protected void initHashTable(int d, int L, int k, double w, int H) {
        hash = new ArrayList<>(L);
        for (int i = 0; i < L; i++) {
            hash.add(new MultiProbeHash(d, k, w, H));
        }
    }

    @Override
    public String toString() {
        return "Multi-Probe " + super.toString();
    }

    /**
     * Fits the posteriori multiple probe algorithm.
     * @param range the neighborhood search data structure.
     * @param radius the radius for range search.
     * @param samples the training samples.
     */
    public void fit(RNNSearch<double[], double[]> range, double[][] samples, double radius) {
        fit(range, samples, radius, 2500);
    }

    /**
     * Fits the posteriori multiple probe algorithm.
     * @param range the neighborhood search data structure.
     * @param radius the radius for range search.
     * @param Nz the number of quantized values.
     */
    public void fit(RNNSearch<double[], double[]> range, double[][] samples, double radius, int Nz) {
        fit(range, samples, radius, Nz, 0.2);
    }

    /**
     * Train the posteriori multiple probe algorithm.
     * @param range the neighborhood search data structure.
     * @param radius the radius for range search.
     * @param Nz the number of quantized values.
     * @param sigma the Parzen window width.
     */
    public void fit(RNNSearch<double[], double[]> range, double[][] samples, double radius, int Nz, double sigma) {
        MultiProbeSample[] training = new MultiProbeSample[samples.length];
        for (int i = 0; i < samples.length; i++) {
            training[i] = new MultiProbeSample(samples[i], new LinkedList<>());
            ArrayList<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
            range.range(samples[i], radius, neighbors);
            for (Neighbor<double[], double[]> n : neighbors) {
                training[i].neighbors.add(keys.get(n.index));
            }
        }

        model = new ArrayList<>(hash.size());
        for (Hash h : hash) {
            model.add(new PosterioriModel((MultiProbeHash) h, training, Nz, sigma));
        }
    }

    @Override
    public Neighbor<double[], E> nearest(double[] q) {
        if (model == null) return super.nearest(q);
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

        double[] key = null;
        int index = -1;
        double nearest = Double.MAX_VALUE;

        Set<Integer> candidates = getCandidates(q, recall, T);
        for (int i : candidates) {
            double[] x = keys.get(i);
            if (q != x) {
                double distance = MathEx.distance(q, x);
                if (distance < nearest) {
                    index = i;
                    nearest = distance;
                    key = x;
                }
            }
        }

        return index == -1 ? null : new Neighbor<>(key, data.get(index), index, nearest);
    }

    @Override
    public Neighbor<double[], E>[] knn(double[] q, int k) {
        if (model == null) return super.knn(q, k);
        return knn(q, k, 0.95, 100);
    }

    /**
     * Returns the approximate k-nearest neighbors. A posteriori multiple probe
     * model has to be trained already.
     * @param q the query object.
     * @param k the number of nearest neighbors to search for.
     * @param recall the expected recall rate.
     * @param T the maximum number of probes.
     */
    @SuppressWarnings("unchecked")
    public Neighbor<double[], E>[] knn(double[] q, int k, double recall, int T) {
        if (recall > 1 || recall < 0) {
            throw new IllegalArgumentException("Invalid recall: " + recall);
        }

        if (k < 1) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        Set<Integer> candidates = getCandidates(q, recall, T);
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
        if (model == null) super.range(q,radius, neighbors);
        else range(q, radius, neighbors, 0.95, 100);
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

        Set<Integer> candidates = getCandidates(q, recall, T);
        for (int index : candidates) {
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
    private Set<Integer> getCandidates(double[] q, double recall, int T) {
        double alpha = 1 - Math.pow(1 - recall, 1.0 / hash.size());

        Set<Integer> candidates = new LinkedHashSet<>();
        for (int i = 0; i < hash.size(); i++) {
            IntArrayList buckets = model.get(i).getProbeSequence(q, alpha, T);
            for (int j = 0; j < buckets.size(); j++) {
                Bucket bin = hash.get(i).get(buckets.get(j));
                if (bin != null) {
                    IntArrayList points = bin.points();
                    for (int l = 0; l < points.size(); l++) {
                        candidates.add(points.get(l));
                    }
                }
            }
        }

        return candidates;
    }
}
