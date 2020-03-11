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

package smile.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;
import smile.neighbor.RNNSearch;

/**
 * Non-parametric Minimum Conditional Entropy Clustering. This method performs
 * very well especially when the exact number of clusters is unknown.
 * The method can also correctly reveal the structure of data and effectively
 * identify outliers simultaneously.
 * <p>
 * The clustering criterion is based on the conditional entropy H(C | x), where
 * C is the cluster label and x is an observation. According to Fano's
 * inequality, we can estimate C with a low probability of error only if the
 * conditional entropy H(C | X) is small. MEC also generalizes the criterion
 * by replacing Shannon's entropy with Havrda-Charvat's structural
 * &alpha;-entropy. Interestingly, the minimum entropy criterion based
 * on structural &alpha;-entropy is equal to the probability error of the
 * nearest neighbor method when &alpha;= 2. To estimate p(C | x), MEC employs
 * Parzen density estimation, a nonparametric approach.
 * <p>
 * MEC is an iterative algorithm starting with an initial partition given by
 * any other clustering methods, e.g. k-means, CLARNAS, hierarchical clustering,
 * etc. Note that a random initialization is NOT appropriate.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Haifeng Li, Keshu Zhang, and Tao Jiang. Minimum Entropy Clustering and Applications to Gene Expression Analysis. CSB, 2004. </li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class MEC<T> extends PartitionClustering implements Comparable<MEC<T>> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MEC.class);

    /**
     * The conditional entropy as the objective function.
     */
    public final double entropy;
    /**
     * The range of neighborhood.
     */
    public final double radius;
    /**
     * The neighborhood search data structure.
     */
    private RNNSearch<T,T> nns;

    /**
     * Constructor.
     * @param entropy the conditional entropy of clusters.
     * @param radius the neighborhood radius.
     * @param nns the data structure for neighborhood search.
     * @param k the number of clusters.
     * @param y the cluster labels.
     */
    public MEC(double entropy, double radius, RNNSearch<T,T> nns, int k, int[] y) {
        super(k, y);
        this.entropy = entropy;
        this.radius = radius;
        this.nns = nns;
    }

    @Override
    public int compareTo(MEC<T> o) {
        return Double.compare(entropy, o.entropy);
    }

    /**
     * Clustering the data.
     * @param data the observations.
     * @param distance the distance measure for neighborhood search.
     * @param k the number of clusters. Note that this is just a hint. The final
     *          number of clusters may be less.
     * @param radius the neighborhood radius.
     */
    public static <T> MEC<T> fit(T[] data, Distance<T> distance, int k, double radius) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        // Initialize clusters with KMeans/CLARANS.
        int[] y;
        if (data instanceof double[][] && distance instanceof EuclideanDistance) {
            KMeans kmeans = KMeans.fit((double[][]) data, k);
            y = kmeans.y;
        } else {
            CLARANS<T> clarans = CLARANS.fit(data, distance::d, k);
            y = clarans.y;
        }

        return fit(data, new LinearSearch<>(data, distance), k, radius, y, 1E-4);
    }

    /**
     * Clustering the data.
     * @param data the observations.
     * @param nns the neighborhood search data structure.
     * @param k the number of clusters. Note that this is just a hint. The final
     *          number of clusters may be less.
     * @param radius the neighborhood radius.
     * @param y the initial clustering labels, which could be produced by any
     *          other clustering methods.
     * @param tol the tolerance of convergence test.
     */
    public  static <T> MEC<T> fit(T[] data, RNNSearch<T,T> nns, int k, double radius, int[] y, double tol) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        int n = data.length;
        // The density of each observation.
        double[] px = new double[n];

        // Neighbors of each observation.
        ArrayList<int[]> neighbors = new ArrayList<>();

        logger.info(String.format("Estimating the probabilities ..."));
        IntStream stream = IntStream.range(0, n);
        if (!(nns instanceof LinearSearch)) {
            stream = stream.parallel();
        }

        stream.forEach(i -> {
            ArrayList<Neighbor<T,T>> list = new ArrayList<>();
            // Add the point itself to the neighborhood
            // This is important to estimate posterior probability
            // and also avoid empty neighborhood.
            list.add(Neighbor.of(data[i], i, 0.0));

            nns.range(data[i], radius, list);
            int[] neighborhood = new int[list.size()];
            neighbors.add(neighborhood);

            for (int j = 0; j < list.size(); j++) {
                neighborhood[j] = list.get(j).index;
            }
            px[i] = (double) list.size() / n;
        });

        // Initialize a posterior probabilities.

        // The number of observations in each cluster in the neighborhood.
        int[][] size = new int[n][k];
        // The most significant cluster in the neighborhood.
        int[] dominantCluster = new int[n];

        IntStream.range(0, n).parallel().forEach(i -> {
            for (int j : neighbors.get(i)) {
                size[i][y[j]]++;
            }
        });

        IntStream.range(0, n).parallel().forEach(i -> {
            int max = 0;
            for (int j = 0; j < k; j++) {
                if (size[i][j] > max) {
                    dominantCluster[i] = j;
                    max = size[i][j];
                }
            }
        });

        double entropy = entropy(k, neighbors, size, px);
        logger.info(String.format("Entropy after initialization: %.4f", entropy));

        double diff = Double.MAX_VALUE;
        for (int iter = 1; diff > tol; iter++) {
            for (int i = 0; i < n; i++) {
                if (dominantCluster[i] != y[i]) {
                    double oldMutual = 0.0;
                    double newMutual = 0.0;

                    for (int neighbor : neighbors.get(i)) {
                        double nk = neighbors.get(neighbor).length;

                        double r1 = (double) size[neighbor][y[i]] / nk;
                        double r2 = (double) size[neighbor][dominantCluster[i]] / nk;
                        if (r1 > 0) {
                            oldMutual -= r1 * MathEx.log2(r1) * px[neighbor];
                        }
                        if (r2 > 0) {
                            oldMutual -= r2 * MathEx.log2(r2) * px[neighbor];
                        }

                        r1 = (size[neighbor][y[i]] - 1.0) / nk;
                        r2 = (size[neighbor][dominantCluster[i]] + 1.0) / nk;
                        if (r1 > 0) {
                            newMutual -= r1 * MathEx.log2(r1) * px[neighbor];
                        }
                        if (r2 > 0) {
                            newMutual -= r2 * MathEx.log2(r2) * px[neighbor];
                        }
                    }

                    if (newMutual < oldMutual) {
                        for (int neighbor : neighbors.get(i)) {
                            --size[neighbor][y[i]];
                            ++size[neighbor][dominantCluster[i]];
                            int mi = dominantCluster[i];
                            int mk = dominantCluster[neighbor];
                            if (size[neighbor][mi] > size[neighbor][mk]) {
                                dominantCluster[neighbor] = dominantCluster[i];
                            }
                        }
                        y[i] = dominantCluster[i];
                    }
                }
            }

            double prevObj = entropy;
            entropy = entropy(k, neighbors, size, px);
            diff = prevObj - entropy;
            logger.info(String.format("Entropy after %3d iterations: %.4f", iter, entropy));
        }

        // Collapse clusters by removing clusters with no samples.
        int[] clusterSize = new int[k];
        for (int i = 0; i < n; i++) {
            clusterSize[y[i]]++;
        }

        // Reuse clusterSize as the index of new cluster id.
        int K = 0;
        for (int i = 0, j = 0; i < k; i++) {
            if (clusterSize[i] > 0) {
                K++;
                clusterSize[i] = j++;
            }
        }

        for (int i = 0; i < n; i++) {
            y[i] = clusterSize[y[i]];
        }

        return new MEC<>(entropy, radius, nns, K, y);
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label. Note that it may be {@link #OUTLIER}.
     */
    public int predict(T x) {
        List<Neighbor<T,T>> neighbors = new ArrayList<>();
        nns.range(x, radius, neighbors);

        if (neighbors.isEmpty()) {
            return OUTLIER;
        }

        int[] label = new int[k];
        for (Neighbor<T,T> neighbor : neighbors) {
            int yi = y[neighbor.index];
            label[yi]++;
        }

        return MathEx.whichMax(label);
    }

    @Override
    public String toString() {
        return String.format("Cluster entropy: %.5f%n", entropy) + super.toString();
    }

    /** Calculates the entropy. */
    private static double entropy(int k, ArrayList<int[]> neighbors, int[][] size, double[] px) {
        int n = neighbors.size();

        return IntStream.range(0, n).parallel().mapToDouble(i -> {
            double conditionalEntropy = 0.0;
            int ni = neighbors.get(i).length;
            int[] ci = size[i];
            for (int j = 0; j < k; j++) {
                if (ci[j] > 0) {
                    double r = ((double) ci[j]) / ni;
                    conditionalEntropy -= r * MathEx.log2(r);
                }
            }

            conditionalEntropy *= px[i];
            return conditionalEntropy;
        }).sum();
    }
}
