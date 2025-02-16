/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;
import smile.neighbor.RNNSearch;
import smile.util.AlgoStatus;
import smile.util.IterativeAlgorithmController;
import static smile.clustering.Clustering.OUTLIER;

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
 * on structural &alpha;-entropy is equal to the probability error of the
 * nearest neighbor method when &alpha;= 2. To estimate p(C | x), MEC employs
 * Parzen density estimation, a nonparametric approach.
 * <p>
 * MEC is an iterative algorithm starting with an initial partition given by
 * any other clustering methods, e.g. k-means, CLARNAS, hierarchical clustering,
 * etc. Note that a random initialization is NOT appropriate.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Haifeng Li. All rights reserved., Keshu Zhang, and Tao Jiang. Minimum Entropy Clustering and Applications to Gene Expression Analysis. CSB, 2004. </li>
 * </ol>
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class MEC<T> extends Partitioning implements Comparable<MEC<T>> {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MEC.class);

    /**
     * The conditional entropy as the objective function.
     */
    private final double entropy;
    /**
     * The range of neighborhood.
     */
    private final double radius;
    /**
     * The neighborhood search data structure.
     */
    private final RNNSearch<T,T> nns;

    /**
     * Constructor.
     * @param k the number of clusters.
     * @param group the cluster labels.
     * @param entropy the conditional entropy of clusters.
     * @param radius the neighborhood radius.
     * @param nns the data structure for neighborhood search.
     */
    public MEC(int k, int[] group, double entropy, double radius, RNNSearch<T,T> nns) {
        super(k, group);
        this.entropy = entropy;
        this.radius = radius;
        this.nns = nns;
    }

    /**
     * Returns the conditional entropy of clusters.
     * @return the conditional entropy of clusters.
     */
    public double entropy() {
        return entropy;
    }

    /**
     * Returns the neighborhood radius.
     * @return the neighborhood radius.
     */
    public double radius() {
        return radius;
    }

    @Override
    public int compareTo(MEC<T> o) {
        return Double.compare(entropy, o.entropy);
    }

    /**
     * MEC hyperparameters.
     * @param k the maximum number of clusters.
     * @param radius the neighborhood radius.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance of convergence test.
     * @param controller the optional training controller.
     */
    public record Options(int k, double radius, int maxIter, double tol,
                          IterativeAlgorithmController<AlgoStatus> controller) {
        /** Constructor. */
        public Options {
            if (k < 2) {
                throw new IllegalArgumentException("Invalid k: " + k);
            }

            if (radius <= 0.0) {
                throw new IllegalArgumentException("Invalid radius: " + radius);
            }

            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }

            if (tol < 0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }
        }

        /**
         * Constructor.
         * @param k the maximum number of clusters.
         * @param radius the neighborhood radius.
         */
        public Options(int k, double radius) {
            this(k, radius, 500, 1E-4, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.mec.k", Integer.toString(k));
            props.setProperty("smile.mec.radius", Double.toString(radius));
            props.setProperty("smile.mec.iterations", Integer.toString(maxIter));
            props.setProperty("smile.mec.tolerance", Double.toString(tol));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.mec.k", "2"));
            double radius = Double.parseDouble(props.getProperty("smile.mec.radius", "1.0"));
            int maxIter = Integer.parseInt(props.getProperty("smile.mec.iterations", "500"));
            double tol = Double.parseDouble(props.getProperty("smile.mec.tolerance", "1E-4"));
            return new Options(k, radius, maxIter, tol, null);
        }
    }

    /**
     * Clustering the data.
     * @param data the observations.
     * @param distance the distance function.
     * @param k the number of clusters. Note that this is just a hint. The final
     *          number of clusters may be less.
     * @param radius the neighborhood radius.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> MEC<T> fit(T[] data, Distance<T> distance, int k, double radius) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        // Initialize clusters with KMeans/CLARANS.
        int[] group;
        if (data instanceof double[][] matrix && distance instanceof EuclideanDistance) {
            var kmeans = KMeans.fit(matrix, k, 100);
            group = kmeans.group();
        } else {
            var clarans = KMedoids.fit(data, distance, k);
            group = clarans.group();
        }

        return fit(data, LinearSearch.of(data, distance), group, new Options(k, radius));
    }

    /**
     * Clustering the data.
     * @param data the observations.
     * @param nns the neighborhood search data structure.
     * @param options the hyperparameters.
     * @param <T> the data type.
     * @return the model.
     */
    public  static <T> MEC<T> fit(T[] data, RNNSearch<T,T> nns, int[] group, Options options) {
        int k = options.k;
        int maxIter = options.maxIter;
        double radius = options.radius;
        double tol = options.tol;
        var controller = options.controller;
        int n = data.length;
        // The density of each observation.
        double[] px = new double[n];

        // Neighbors of each observation.
        int[][] neighbors = new int[n][];

        logger.info("Estimating the probabilities ...");
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

            nns.search(data[i], radius, list);
            int[] neighborhood = new int[list.size()];
            neighbors[i] = neighborhood;

            for (int j = 0; j < list.size(); j++) {
                neighborhood[j] = list.get(j).index();
            }
            px[i] = (double) list.size() / n;
        });

        // Initialize a posterior probabilities.

        // The number of observations in each cluster in the neighborhood.
        int[][] size = new int[n][k];
        // The most significant cluster in the neighborhood.
        int[] dominantCluster = new int[n];

        IntStream.range(0, n).parallel().forEach(i -> {
            for (int j : neighbors[i]) {
                size[i][group[j]]++;
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
        logger.info("Initial entropy = {}", entropy);

        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            for (int i = 0; i < n; i++) {
                if (dominantCluster[i] != group[i]) {
                    double oldMutual = 0.0;
                    double newMutual = 0.0;

                    for (int neighbor : neighbors[i]) {
                        double nk = neighbors[neighbor].length;

                        double r1 = (double) size[neighbor][group[i]] / nk;
                        double r2 = (double) size[neighbor][dominantCluster[i]] / nk;
                        if (r1 > 0) {
                            oldMutual -= r1 * MathEx.log2(r1) * px[neighbor];
                        }
                        if (r2 > 0) {
                            oldMutual -= r2 * MathEx.log2(r2) * px[neighbor];
                        }

                        r1 = (size[neighbor][group[i]] - 1.0) / nk;
                        r2 = (size[neighbor][dominantCluster[i]] + 1.0) / nk;
                        if (r1 > 0) {
                            newMutual -= r1 * MathEx.log2(r1) * px[neighbor];
                        }
                        if (r2 > 0) {
                            newMutual -= r2 * MathEx.log2(r2) * px[neighbor];
                        }
                    }

                    if (newMutual < oldMutual) {
                        for (int neighbor : neighbors[i]) {
                            --size[neighbor][group[i]];
                            ++size[neighbor][dominantCluster[i]];
                            int mi = dominantCluster[i];
                            int mk = dominantCluster[neighbor];
                            if (size[neighbor][mi] > size[neighbor][mk]) {
                                dominantCluster[neighbor] = dominantCluster[i];
                            }
                        }
                        group[i] = dominantCluster[i];
                    }
                }
            }

            double prevObj = entropy;
            entropy = entropy(k, neighbors, size, px);
            diff = prevObj - entropy;
            logger.info("Iteration {}: entropy = {}", iter, entropy);

            if (controller != null) {
                controller.submit(new AlgoStatus(iter, entropy));
                if (controller.isInterrupted()) break;
            }
        }

        // Collapse clusters by removing clusters with no samples.
        int[] clusterSize = new int[k];
        for (int i = 0; i < n; i++) {
            clusterSize[group[i]]++;
        }

        // Reuse clusterSize as the index of new cluster id.
        int numClusters = 0;
        for (int i = 0, j = 0; i < k; i++) {
            if (clusterSize[i] > 0) {
                numClusters++;
                clusterSize[i] = j++;
            }
        }

        for (int i = 0; i < n; i++) {
            group[i] = clusterSize[group[i]];
        }

        return new MEC<>(numClusters, group, entropy, radius, nns);
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label. Note that it may be {@link Clustering#OUTLIER}.
     */
    public int predict(T x) {
        List<Neighbor<T,T>> neighbors = new ArrayList<>();
        nns.search(x, radius, neighbors);

        if (neighbors.isEmpty()) {
            return OUTLIER;
        }

        int[] label = new int[k];
        for (Neighbor<T,T> neighbor : neighbors) {
            int y = group[neighbor.index()];
            label[y]++;
        }

        return MathEx.whichMax(label);
    }

    @Override
    public String toString() {
        return String.format("Cluster entropy: %.5f%n", entropy) + super.toString();
    }

    /** Calculates the entropy. */
    private static double entropy(int k, int[][] neighbors, int[][] size, double[] px) {
        return IntStream.range(0, neighbors.length).parallel().mapToDouble(i -> {
            double conditionalEntropy = 0.0;
            int ni = neighbors[i].length;
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
