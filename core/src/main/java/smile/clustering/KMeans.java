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
package smile.clustering;

import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.tensor.ScalarType;
import smile.tensor.Vector;
import smile.util.AlgoStatus;

/**
 * K-Means clustering. The algorithm partitions n observations into k clusters
 * in which each observation belongs to the cluster with the nearest mean.
 * Although finding an exact solution to the k-means problem for arbitrary
 * input is NP-hard, the standard approach to finding an approximate solution
 * (often called Lloyd's algorithm or the k-means algorithm) is used widely
 * and frequently finds reasonable solutions quickly.
 * <p>
 * K-means has a number of interesting theoretical properties. First, it
 * partitions the data space into a structure known as a Voronoi diagram.
 * Second, it is conceptually close to nearest neighbor classification,
 * and as such is popular in machine learning. Third, it can be seen as
 * a variation of model based clustering, and Lloyd's algorithm as a
 * variation of the EM algorithm.
 * <p>
 * However, the k-means algorithm has at least two major theoretic shortcomings:
 * <ul>
 * <li> First, it has been shown that the worst case running time of the
 * algorithm is super-polynomial in the input size.
 * <li> Second, the approximation found can be arbitrarily bad with respect
 * to the objective function compared to the optimal learn. Therefore,
 * it is common to run multiple times with different random initializations.
 * </ul>
 *
 * In this implementation, we use k-means++ which addresses the second of these
 * obstacles by specifying a procedure to initialize the cluster centers before
 * proceeding with the standard k-means optimization iterations. With the
 * k-means++ initialization, the algorithm is guaranteed to find a solution
 * that is O(log k) competitive to the optimal k-means solution.
 * <p>
 * We also use k-d trees to speed up each k-means step as described in the filter
 * algorithm by Kanungo, et al.
 * <p>
 * K-means is a hard clustering method, i.e. each observation is assigned to
 * a specific cluster. In contrast, soft clustering, e.g. the
 * Expectation-Maximization algorithm for Gaussian mixtures, assign observations
 * to different clusters with different probabilities.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Tapas Kanungo, David M. Mount, Nathan S. Netanyahu, Christine D. Piatko, Ruth Silverman, and Angela Y. Wu. An Efficient k-Means Clustering Algorithm: Analysis and Implementation. IEEE TRANS. PAMI, 2002.</li>
 * <li> D. Arthur and S. Vassilvitskii. "K-means++: the advantages of careful seeding". ACM-SIAM symposium on Discrete algorithms, 1027-1035, 2007.</li>
 * <li> Anna D. Peterson, Arka P. Ghosh and Ranjan Maitra. A systematic evaluation of different methods for initializing the K-means clustering algorithm. 2010.</li>
 * </ol>
 * 
 * @see XMeans
 * @see GMeans
 * @see KMedoids
 * @see KModes
 * @see SIB
 * @see smile.vq.SOM
 * @see smile.vq.NeuralGas
 * @see BBDTree
 * 
 * @author Haifeng Li
 */
public class KMeans {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KMeans.class);

    /** Constructor. */
    private KMeans() {

    }

    /**
     * Fits k-means clustering on single-precision input data.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @return the model.
     */
    public static CentroidClustering<float[], float[]> fit(float[][] data, int k, int maxIter) {
        return fit(data, new Clustering.Options(k, maxIter));
    }

    /**
     * Fits k-means clustering on single-precision input data. A
     * {@link BBDTree} backed by {@link ScalarType#Float32} vectors is used
     * internally to reduce memory usage.
     *
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<float[], float[]> fit(float[][] data, Clustering.Options options) {
        validateOptions(options, data.length);
        return fit(new BBDTree(data), data, options);
    }

    /**
     * Partitions single-precision data into k clusters.
     * @param bbd the BBD-tree of data for fast clustering.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<float[], float[]> fit(BBDTree bbd, float[][] data, Clustering.Options options) {
        validateOptions(options, data.length);

        int k = options.k();
        int maxIter = options.maxIter();
        double tol = options.tol();
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        Distance<float[]> distance = MathEx::distance;
        var clustering = CentroidClustering.init("K-Means", data, k, distance);
        double distortion = clustering.distortion();
        logger.info("Initial distortion = {}", distortion);

        var size = clustering.size();
        var group = clustering.group();
        var centroids = clustering.centers();
        updateCentroids(clustering, data);

        var vCentroids = Arrays.stream(centroids).map(Vector::column).toArray(Vector[]::new);
        var vSum = IntStream.range(0, k)
                .mapToObj(i -> Vector.zeros(ScalarType.Float32, d))
                .toArray(Vector[]::new);
        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            double wcss = bbd.clustering(k, vCentroids, vSum, size, group);
            diff = distortion - wcss;
            distortion = wcss;

            logger.info("Iteration {}: distortion = {}", iter, distortion);
            if (controller != null) {
                controller.submit(new AlgoStatus(iter, distortion));
                if (controller.isInterrupted()) break;
            }
        }

        // In case of early stop, recalculate centroids and re-wrap.
        if (diff > tol) {
            updateCentroids(clustering, data);
        }

        var proximity = clustering.proximity();
        IntStream.range(0, n).parallel().forEach(i -> {
            double dist = distance.applyAsDouble(data[i], centroids[group[i]]);
            proximity[i] = dist * dist;
        });
        return new CentroidClustering<>("K-Means", centroids, distance, group, proximity);
    }

    /**
     * Fits k-means clustering.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, int k, int maxIter) {
        return fit(data, new Clustering.Options(k, maxIter));
    }

    /**
     * Fits k-means clustering.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, Clustering.Options options) {
        validateOptions(options, data.length);
        return fit(new BBDTree(data), data, options);
    }

    /**
     * Partitions data into k clusters.
     * @param bbd the BBD-tree of data for fast clustering.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(BBDTree bbd, double[][] data, Clustering.Options options) {
        validateOptions(options, data.length);
        validateData(data, false);

        int k = options.k();
        int maxIter = options.maxIter();
        double tol = options.tol();
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        Distance<double[]> distance = new EuclideanDistance();
        var clustering = CentroidClustering.init("K-Means", data, k, distance);
        double distortion = clustering.distortion();
        logger.info("Initial distortion = {}", distortion);

        // Initialize the centroids
        var size = clustering.size();
        var group = clustering.group();
        var centroids = clustering.centers();
        updateCentroids(clustering, data);

        var vCentroids = Arrays.stream(centroids).map(Vector::column).toArray(Vector[]::new);
        var vSum = IntStream.range(0, k)
                .mapToObj(i -> Vector.zeros(ScalarType.Float64, d))
                .toArray(Vector[]::new);
        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            double wcss = bbd.clustering(k, vCentroids, vSum, size, group);
            diff = distortion - wcss;
            distortion = wcss;

            logger.info("Iteration {}: distortion = {}", iter, distortion);
            if (controller != null) {
                controller.submit(new AlgoStatus(iter, distortion));
                if (controller.isInterrupted()) break;
            }
        }

        // In case of early stop, we should recalculate centroids.
        if (diff > tol) {
            updateCentroids(clustering, data);
        }

        var proximity = clustering.proximity();
        IntStream.range(0, n).parallel().forEach(i -> {
            double dist = distance.applyAsDouble(data[i], centroids[group[i]]);
            proximity[i] = dist * dist;
        });
        return new CentroidClustering<>("K-Means", centroids, distance, group, proximity);
    }

    /**
     * Fits k-means clustering on the data containing missing values (NaN).
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> lloyd(double[][] data, int k, int maxIter) {
        return lloyd(data, new Clustering.Options(k, maxIter));
    }

    /**
     * Fits k-means clustering on the data containing missing values (NaN).
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> lloyd(double[][] data, Clustering.Options options) {
        validateOptions(options, data.length);
        validateData(data, true);

        int k = options.k();
        int maxIter = options.maxIter();
        double tol = options.tol();
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        Distance<double[]> distance = MathEx::distanceWithMissingValues;
        var clustering = CentroidClustering.init("K-Means", data, k, distance);
        double distortion = clustering.distortion();
        logger.info("Initial distortion = {}", distortion);

        // The number of non-missing values per cluster per variable.
        int[][] notNaN = new int[k][d];
        var size = clustering.size();
        var group = clustering.group();

        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            updateCentroidsWithMissingValues(clustering, data, notNaN);
            clustering = clustering.assign(data);
            diff = distortion - clustering.distortion();
            distortion = clustering.distortion();

            logger.info("Iteration {}: distortion = {}", iter, distortion);
            if (controller != null) {
                controller.submit(new AlgoStatus(iter, distortion));
                if (controller.isInterrupted()) break;
            }
        }

        // In case of early stop, we should recalculate centroids.
        if (diff > tol) {
            updateCentroidsWithMissingValues(clustering, data, notNaN);
        }

        return clustering;
    }

    /**
     * Calculates the new centroids in the new clusters (single-precision).
     */
    static void updateCentroids(CentroidClustering<float[], float[]> clustering, float[][] data) {
        int n = data.length;
        int[] size = clustering.size();
        int[] group = clustering.group();
        float[][] centroids = clustering.centers();
        int k = centroids.length;
        int d = centroids[0].length;

        Arrays.fill(size, 0);
        IntStream.range(0, k).parallel().forEach(cluster -> {
            float[] centroid = new float[d];
            for (int i = 0; i < n; i++) {
                if (group[i] == cluster) {
                    size[cluster]++;
                    for (int j = 0; j < d; j++) {
                        centroid[j] += data[i][j];
                    }
                }
            }
            for (int j = 0; j < d; j++) {
                centroid[j] /= size[cluster];
            }
            centroids[cluster] = centroid;
        });
    }

    /**
     * Calculates the new centroids in the new clusters.
     */
    static void updateCentroids(CentroidClustering<double[], double[]> clustering, double[][] data) {
        int n = data.length;
        int[] size = clustering.size();
        int[] group = clustering.group();
        double[][] centroids = clustering.centers();
        int k = centroids.length;
        int d = centroids[0].length;

        Arrays.fill(size, 0);
        IntStream.range(0, k).parallel().forEach(cluster -> {
            double[] centroid = new double[d];
            for (int i = 0; i < n; i++) {
                if (group[i] == cluster) {
                    size[cluster]++;
                    for (int j = 0; j < d; j++) {
                        centroid[j] += data[i][j];
                    }
                }
            }

            for (int j = 0; j < d; j++) {
                centroid[j] /= size[cluster];
            }
            centroids[cluster] = centroid;
        });
    }

    /**
     * Calculates the new centroids in the new clusters with missing values.
     * @param notNaN the number of non-missing values per cluster per variable.
     */
    static void updateCentroidsWithMissingValues(CentroidClustering<double[], double[]> clustering, double[][] data, int[][] notNaN) {
        int n = data.length;
        int[] size = clustering.size();
        int[] group = clustering.group();
        double[][] centroids = clustering.centers();
        int k = centroids.length;
        int d = centroids[0].length;

        Arrays.fill(size, 0);
        IntStream.range(0, k).parallel().forEach(cluster -> {
            double[] centroid = new double[d];
            Arrays.fill(notNaN[cluster], 0);
            for (int i = 0; i < n; i++) {
                if (group[i] == cluster) {
                    size[cluster]++;
                    for (int j = 0; j < d; j++) {
                        if (!Double.isNaN(data[i][j])) {
                            centroid[j] += data[i][j];
                            notNaN[cluster][j]++;
                        }
                    }
                }
            }

            for (int j = 0; j < d; j++) {
                if (notNaN[cluster][j] > 0) {
                    centroid[j] /= notNaN[cluster][j];
                } else {
                    // Keep previous coordinate when a cluster has only missing values in this dimension.
                    centroid[j] = centroids[cluster][j];
                }
            }
            centroids[cluster] = centroid;
        });
    }

    /**
     * Validates the hyperparameters for K-Means.
     * @param options the clustering options.
     * @param n the number of observations.
     */
    private static void validateOptions(Clustering.Options options, int n) {
        if (options.k() > n) {
            throw new IllegalArgumentException(
                    String.format("Number of clusters %d cannot be greater than the number of observations %d.",
                            options.k(), n));
        }
    }

    /**
     * Validates matrix-shaped finite input data for K-Means.
     * @param data the input data.
     * @param allowNaN true if allows the input data to contain NaN values.
     */
    private static void validateData(double[][] data, boolean allowNaN) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Empty input data.");
        }

        int d = data[0].length;
        if (d == 0) {
            throw new IllegalArgumentException("Empty feature vectors.");
        }

        for (int i = 1; i < data.length; i++) {
            if (data[i].length != d) {
                throw new IllegalArgumentException("Ragged input data at row " + i);
            }
        }

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < d; j++) {
                double x = data[i][j];
                if (Double.isInfinite(x) || (!allowNaN && Double.isNaN(x))) {
                    throw new IllegalArgumentException(String.format("Invalid value at row %d col %d: %s", i, j, x));
                }
            }
        }
    }
}
