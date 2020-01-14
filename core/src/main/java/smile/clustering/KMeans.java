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

import java.util.function.ToDoubleBiFunction;
import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;

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
 * @see CLARANS
 * @see SIB
 * @see smile.vq.SOM
 * @see smile.vq.NeuralGas
 * @see BBDTree
 * 
 * @author Haifeng Li
 */
public class KMeans extends CentroidClustering<double[], double[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KMeans.class);

    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param centroids the centroids of each cluster.
     * @param y the cluster labels.
     */
    public KMeans(double distortion, double[][] centroids, int[] y) {
        super(distortion, centroids, y);
    }

    @Override
    public double distance(double[] x, double[] y) {
        return MathEx.squaredDistance(x, y);
    }

    /**
     * Partitions data into k clusters up to 100 iterations.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     */
    public static KMeans fit(double[][] data, int k) {
        return fit(data, k, 100, 1E-4);
    }

    /**
     * Partitions data into k clusters up to 100 iterations.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance of convergence test.
     */
    public static KMeans fit(double[][] data, int k, int maxIter, double tol) {
        return fit(new BBDTree(data), data, k, maxIter, tol);
    }

    /**
     * Partitions data into k clusters.
     * @param bbd the BBD-tree of data for fast clustering.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance of convergence test.
     */
    public static KMeans fit(BBDTree bbd, double[][] data, int k, int maxIter, double tol) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = data.length;
        int d = data[0].length;

        int[] y = new int[n];
        double[][] medoids = new double[k][];

        double distortion = MathEx.sum(seed(data, medoids, y, MathEx::squaredDistance));
        logger.info(String.format("Distortion after initialization: %.4f", distortion));

        // Initialize the centroids
        int[] size = new int[k];
        double[][] centroids = new double[k][d];
        updateCentroids(centroids, data, y, size);

        double[][] sum = new double[k][d];
        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            double wcss = bbd.clustering(centroids, sum, size, y);

            logger.info(String.format("Distortion after %3d iterations: %.4f", iter, wcss));
            diff = distortion - wcss;
            distortion = wcss;
        }

        return new KMeans(distortion, centroids, y);
    }

    /**
     * The implementation of Lloyd algorithm as a benchmark. The data may
     * contain missing values (i.e. Double.NaN). The algorithm runs up to
     * 100 iterations.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     */
    public static KMeans lloyd(double[][] data, int k) {
        return lloyd(data, k, 100, 1E-4);
    }

    /**
     * The implementation of Lloyd algorithm as a benchmark. The data may
     * contain missing values (i.e. Double.NaN).
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance of convergence test.
     */
    public static KMeans lloyd(double[][] data, int k, int maxIter, double tol) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = data.length;
        int d = data[0].length;

        int[] y = new int[n];
        double[][] medoids = new double[k][];

        double distortion = MathEx.sum(seed(data, medoids, y, MathEx::squaredDistanceWithMissingValues));
        logger.info(String.format("Distortion after initialization: %.4f", distortion));

        int[] size = new int[k];
        double[][] centroids = new double[k][d];
        // The number of non-missing values per cluster per variable.
        int[][] notNaN = new int[k][d];

        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            updateCentroidsWithMissingValues(centroids, data, y, size, notNaN);

            double wcss = assign(y, data, centroids, MathEx::squaredDistanceWithMissingValues);
            logger.info(String.format("Distortion after %3d iterations: %.4f", iter, wcss));

            diff = distortion - wcss;
            distortion = wcss;
        }

        // In case of early stop, we should recalculate centroids.
        if (diff > tol) {
            updateCentroidsWithMissingValues(centroids, data, y, size, notNaN);
        }

        return new KMeans(distortion, centroids, y) {
            @Override
            public double distance(double[] x, double[] y) {
                return MathEx.squaredDistanceWithMissingValues(x, y);
            }
        };
    }
}
