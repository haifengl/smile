/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.anomaly;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.distance.Metric;
import smile.neighbor.CoverTree;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;

/**
 * Local Outlier Factor (LOF). LOF is an unsupervised outlier detection
 * algorithm based on local density estimation.
 * <p>
 * Many outlier detection algorithms compare an observation against the global
 * data distribution or boundary. However, in datasets where clusters have
 * varying densities, points that are normal relative to a sparse cluster might
 * be denser than outliers near a dense cluster. LOF addresses this by comparing
 * the local density of an observation to the local densities of its
 * {@code k} nearest neighbors.
 * <p>
 * For each point {@code p}, the algorithm determines:
 * <ul>
 *   <li>The {@code k}-distance, which is the distance to its {@code k}-th nearest neighbor.</li>
 *   <li>The reachability distance from {@code o} to {@code p}:
 *       {@code reach-dist_k(p, o) = max(k-distance(o), d(p, o))}.</li>
 *   <li>The local reachability density (lrd), defined as the inverse of the average
 *       reachability distance of {@code p} from its neighbors.</li>
 *   <li>The LOF score, which is the average ratio of the lrds of its neighbors to that
 *       of {@code p}.</li>
 * </ul>
 * <p>
 * An LOF score around {@code 1.0} indicates an inlier with local density comparable
 * to its neighbors. A score significantly greater than {@code 1.0} (e.g., &gt; 1.5)
 * signals an outlier whose local density is markedly lower than that of its neighbors.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Markus M. Breunig, Hans-Peter Kriegel, Raymond T. Ng, and Jörg Sander.
 *     LOF: Identifying Density-Based Local Outliers. ACM SIGMOD, 93–104, 2000.</li>
 * </ol>
 *
 * @param <T> the data type of observations.
 * @author Haifeng Li
 */
public class LOF<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Small constant to prevent division by zero for duplicate points.
     */
    private static final double EPSILON = 1E-10;

    /**
     * Hyperparameters of LOF.
     *
     * @param k the number of nearest neighbors used to define the local neighborhood.
     */
    public record Options(int k) {
        /**
         * Constructor.
         */
        public Options {
            if (k < 1) {
                throw new IllegalArgumentException("Invalid k: " + k);
            }
        }

        /**
         * Default constructor with k = 20.
         */
        public Options() {
            this(20);
        }

        /**
         * Returns the persistent set of hyperparameters.
         *
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.lof.k", Integer.toString(k));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.lof.k", "20"));
            return new Options(k);
        }
    }

    /** The number of nearest neighbors. */
    private final int k;
    /** The nearest neighbor search data structure. */
    private final KNNSearch<T, T> nns;
    /** The k-distance of each training observation. */
    private final double[] kdistance;
    /** The local reachability density of each training observation. */
    private final double[] lrd;
    /** The in-sample LOF scores for training observations. */
    private final double[] scores;

    /**
     * Constructor.
     *
     * @param k the number of neighbors.
     * @param nns the nearest neighbor search data structure.
     * @param kdistance the k-distance of each training observation.
     * @param lrd the local reachability density of each training observation.
     * @param scores the LOF scores of training observations.
     */
    public LOF(int k, KNNSearch<T, T> nns, double[] kdistance, double[] lrd, double[] scores) {
        this.k = k;
        this.nns = nns;
        this.kdistance = kdistance;
        this.lrd = lrd;
        this.scores = scores;
    }

    /**
     * Returns the number of nearest neighbors.
     *
     * @return the number of nearest neighbors.
     */
    public int k() {
        return k;
    }

    /**
     * Returns the in-sample LOF scores of the training data.
     *
     * @return the LOF scores.
     */
    public double[] scores() {
        return scores;
    }

    /**
     * Fits an LOF model on numeric data using a KD-tree for spatial neighborhood search.
     * Default k = 20.
     *
     * @param data the training observations.
     * @return the fitted model.
     */
    public static LOF<double[]> fit(double[][] data) {
        return fit(data, new Options());
    }

    /**
     * Fits an LOF model on numeric data using a KD-tree for spatial neighborhood search.
     *
     * @param data the training observations.
     * @param k the number of nearest neighbors.
     * @return the fitted model.
     */
    public static LOF<double[]> fit(double[][] data, int k) {
        return fit(data, new Options(k));
    }

    /**
     * Fits an LOF model on numeric data using a KD-tree for spatial neighborhood search.
     *
     * @param data the training observations.
     * @param options the hyperparameters.
     * @return the fitted model.
     */
    public static LOF<double[]> fit(double[][] data, Options options) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Training data is empty");
        }
        return fit(data, new KDTree<>(data, data), options.k());
    }

    /**
     * Fits an LOF model on arbitrary objects in a metric space.
     *
     * @param data the training observations.
     * @param distance the distance function.
     * @param k the number of nearest neighbors.
     * @param <T> the data type.
     * @return the fitted model.
     */
    public static <T> LOF<T> fit(T[] data, Distance<T> distance, int k) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Training data is empty");
        }
        KNNSearch<T, T> nns;
        if (distance instanceof Metric<T> metric) {
            nns = CoverTree.of(data, metric);
        } else {
            nns = LinearSearch.of(data, distance);
        }
        return fit(data, nns, k);
    }

    /**
     * Fits an LOF model using a provided nearest neighbor search structure.
     *
     * @param data the training observations.
     * @param nns the nearest neighbor search structure.
     * @param k the number of nearest neighbors.
     * @param <T> the data type.
     * @return the fitted model.
     */
    public static <T> LOF<T> fit(T[] data, KNNSearch<T, T> nns, int k) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Training data is empty");
        }
        if (k < 1) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }
        if (k >= data.length) {
            throw new IllegalArgumentException(String.format("k (%d) must be less than sample size (%d)", k, data.length));
        }

        int n = data.length;
        double[] kdistance = new double[n];
        @SuppressWarnings("unchecked")
        Neighbor<T, T>[][] neighborhoods = new Neighbor[n][];

        // Step 1: find k nearest neighbors and k-distance for each observation
        IntStream.range(0, n).parallel().forEach(i -> {
            Neighbor<T, T>[] neighbors = nns.search(data[i], k);
            neighborhoods[i] = neighbors;
            kdistance[i] = neighbors.length > 0 ? neighbors[neighbors.length - 1].distance() : 0.0;
        });

        // Step 2: compute local reachability density (lrd) for each observation
        double[] lrd = new double[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            Neighbor<T, T>[] neighbors = neighborhoods[i];
            double sumReachDist = 0.0;
            for (Neighbor<T, T> o : neighbors) {
                double reachDist = Math.max(kdistance[o.index()], o.distance());
                sumReachDist += reachDist;
            }
            lrd[i] = neighbors.length / (sumReachDist + EPSILON);
        });

        // Step 3: compute LOF scores
        double[] scores = new double[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            Neighbor<T, T>[] neighbors = neighborhoods[i];
            double sumLrdRatio = 0.0;
            for (Neighbor<T, T> o : neighbors) {
                sumLrdRatio += lrd[o.index()] / (lrd[i] + EPSILON);
            }
            scores[i] = sumLrdRatio / neighbors.length;
        });

        return new LOF<>(k, nns, kdistance, lrd, scores);
    }

    /**
     * Returns the LOF score for an out-of-sample observation.
     * Scores around 1.0 indicate an inlier; scores significantly greater than 1.0 indicate an anomaly.
     *
     * @param x the observation.
     * @return the LOF score.
     */
    public double score(T x) {
        if (x == null) {
            throw new IllegalArgumentException("Observation is null");
        }

        Neighbor<T, T>[] neighbors = nns.search(x, k);
        if (neighbors.length == 0) {
            return 1.0;
        }

        // Local reachability density of x
        double sumReachDist = 0.0;
        for (Neighbor<T, T> o : neighbors) {
            double reachDist = Math.max(kdistance[o.index()], o.distance());
            sumReachDist += reachDist;
        }
        double lrdX = neighbors.length / (sumReachDist + EPSILON);

        // LOF of x
        double sumLrdRatio = 0.0;
        for (Neighbor<T, T> o : neighbors) {
            sumLrdRatio += lrd[o.index()] / (lrdX + EPSILON);
        }

        return sumLrdRatio / neighbors.length;
    }

    /**
     * Returns the LOF scores for an array of observations.
     *
     * @param x the observations.
     * @return the LOF scores.
     */
    public double[] score(T[] x) {
        return Arrays.stream(x).parallel().mapToDouble(this::score).toArray();
    }

    /**
     * Predicts whether an observation is an anomaly based on whether its LOF score
     * exceeds the specified threshold.
     *
     * @param x the observation.
     * @param threshold the LOF threshold (e.g. 1.5). An observation is flagged as an
     *                  anomaly when {@code score(x) > threshold}.
     * @return {@code true} if the observation is predicted to be an anomaly.
     */
    public boolean predict(T x, double threshold) {
        return score(x) > threshold;
    }
}
