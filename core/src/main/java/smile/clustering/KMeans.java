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

import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.MathEx;
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
        int k = options.k();
        int maxIter = options.maxIter();
        double tol = options.tol();
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        double[][] centroids = new double[k][];
        ToDoubleBiFunction<double[], double[]> distance = MathEx::distance;
        var clustering = CentroidClustering.init(data, centroids, distance);
        double distortion = clustering.distortion();
        logger.info("Initial distortion = {}", distortion);

        // Initialize the centroids
        var size = clustering.size();
        var group = clustering.group();
        updateCentroids(clustering, data);

        double[][] sum = new double[k][d];
        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            double wcss = bbd.clustering(centroids, sum, size, group);
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
            proximity[i] = distance.applyAsDouble(data[i], centroids[group[i]]);
        });
        return new CentroidClustering<>(centroids, distance, group, proximity);
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
        int k = options.k();
        int maxIter = options.maxIter();
        double tol = options.tol();
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        double[][] centroids = new double[k][];
        ToDoubleBiFunction<double[], double[]> distance = MathEx::distanceWithMissingValues;
        var clustering = CentroidClustering.init(data, centroids, distance);
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
                centroid[j] /= notNaN[cluster][j];
            }
            centroids[cluster] = centroid;
        });
    }
}
