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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.sort.QuickSort;

/**
 * X-Means clustering algorithm, an extended K-Means which tries to
 * automatically determine the number of clusters based on BIC scores.
 * Starting with only one cluster, the X-Means algorithm goes into action
 * after each run of K-Means, making local decisions about which subset of the
 * current centroids should split themselves in order to better fit the data.
 * The splitting decision is done by computing the Bayesian Information
 * Criterion (BIC).
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Dan Pelleg and Andrew Moore. X-means: Extending K-means with Efficient Estimation of the Number of Clusters. ICML, 2000. </li>
 * </ol>
 * 
 * @see KMeans
 * @see GMeans
 * 
 * @author Haifeng Li
 */
public class XMeans {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(XMeans.class);
    private static final double LOG2PI = Math.log(Math.PI * 2.0);

    /** Constructor. */
    private XMeans() {

    }

    /**
     * Clustering data with the number of clusters
     * determined by X-Means algorithm automatically.
     * @param data the input data of which each row is an observation.
     * @param kmax the maximum number of clusters.
     * @param maxIter the maximum number of iterations for k-means.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, int kmax, int maxIter) {
        return fit(data, new Clustering.Options(kmax, maxIter));
    }

    /**
     * Clustering data with the number of clusters
     * determined by X-Means algorithm automatically.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, Clustering.Options options) {
        int kmax = options.k();
        int maxIter = options.maxIter();
        double tol = options.tol();
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        int[] group = new int[n];
        double[] proximity = new double[n];
        double[][] sum = new double[kmax][d];
        double[] mean = MathEx.colMeans(data);
        double[][] centroids = new double[kmax][];
        int[] size = new int[kmax];
        centroids[0] = mean;
        size[0] = n;

        double distortion = Arrays.stream(data).parallel()
                .mapToDouble(x -> MathEx.squaredDistance(x, mean))
                .sum() / n;
        double[] distortions = new double[kmax];
        distortions[0] = distortion;

        BBDTree bbd = new BBDTree(data);
        var kmeans = new ArrayList<CentroidClustering<double[], double[]>>(kmax);
        ArrayList<double[]> centers = new ArrayList<>();

        int k = 1;
        while (k < kmax) {
            centers.clear();
            double[] score = new double[k];

            for (int i = 0; i < k; i++) {
                int ni = size[i];
                // don't split too small cluster. Anyway likelihood estimation
                // is not accurate in this case.
                if (ni < 25) {
                    logger.info("Cluster {} too small to split: {} observations", i, ni);
                    score[i] = 0.0;
                    kmeans.add(null);
                    continue;
                }

                double[][] subset = new double[ni][];
                for (int j = 0, l = 0; j < n; j++) {
                    if (group[j] == i) {
                        subset[l++] = data[j];
                    }
                }

                var clustering = KMeans.fit(subset, new Clustering.Options(2, maxIter, tol, null));
                kmeans.add(clustering);
                double newBIC = bic(2, ni, d, clustering.distortion(), clustering.size());
                double oldBIC = bic(ni, d, distortions[i]);
                score[i] = newBIC - oldBIC;
                logger.info("Cluster {} BIC: {}, BIC after split: {}, improvement: {}", i, oldBIC, newBIC, score[i]);
            }

            int[] index = QuickSort.sort(score);
            for (int i = 0; i < k; i++) {
                if (score[i] <= 0.0) {
                    centers.add(centroids[index[i]]);
                }
            }
            
            int m = centers.size();
            for (int i = k; --i >= 0;) {
                if (score[i] > 0) {
                    if (centers.size() + i - m + 1 < kmax) {
                        logger.info("Split cluster {}", index[i]);
                        centers.add(kmeans.get(index[i]).center(0));
                        centers.add(kmeans.get(index[i]).center(1));
                    } else {
                        centers.add(centroids[index[i]]);
                    }
                }
            }

            // no more split.
            if (centers.size() == k) {
                logger.info("No more split. Finish with {} clusters", k);
                break;
            }

            k = centers.size();
            centers.toArray(centroids);

            double diff = Double.MAX_VALUE;
            for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
                double wcss = bbd.clustering(k, centroids, sum, size, group) / n;
                diff = distortion - wcss;
                distortion = wcss;
            }
            logger.info("Distortion with {} clusters: {}", k, distortion);

            Arrays.fill(distortions, 0.0);
            IntStream.range(0, k).parallel().forEach(cluster -> {
                double[] centroid = centroids[cluster];
                for (int i = 0; i < n; i++) {
                    if (group[i] == cluster) {
                        double dist = MathEx.distance(data[i], centroid);
                        dist *= dist;
                        proximity[i] = dist;
                        distortions[cluster] += dist;
                    }
                }
            });

            for (int i = 0; i < k; i++) {
                distortions[i] /= size[i];
            }
        }

        ToDoubleBiFunction<double[], double[]> distance = MathEx::distance;
        return new CentroidClustering<>("X-Means", Arrays.copyOf(centroids, k), distance, group, proximity);
    }

    /**
     * Calculates the BIC for single cluster.
     * @param n the total number of observations.
     * @param d the dimensionality of data.
     * @param distortion the distortion of clusters.
     * @return the BIC score.
     */
    private static double bic(int n, int d, double distortion) {
        double variance = n * distortion / (n - 1);

        double p1 = -n * LOG2PI;
        double p2 = -n * d * Math.log(variance);
        double p3 = -(n - 1);
        double L = (p1 + p2 + p3) / 2;

        int numParameters = d + 1;
        return L - 0.5 * numParameters * Math.log(n);
    }

    /**
     * Calculates the BIC for k-means.
     * @param k the number of clusters.
     * @param n the total number of observations.
     * @param d the dimensionality of data.
     * @param distortion the distortion of clusters.
     * @param size the number of observations in each cluster.
     * @return the BIC score.
     */
    private static double bic(int k, int n, int d, double distortion, int[] size) {
        double variance = n * distortion / (n - k);

        double L = 0.0;
        for (int i = 0; i < k; i++) {
            L += logLikelihood(k, n, size[i], d, variance);
        }

        int numParameters = k + k * d;
        return L - 0.5 * numParameters * Math.log(n);
    }

    /**
     * Estimate the log-likelihood of the data for the given model.
     *
     * @param k the number of clusters.
     * @param n the total number of observations.
     * @param ni the number of observations belong to this cluster.
     * @param d the dimensionality of data.
     * @param variance the estimated variance of clusters.
     * @return the likelihood estimate
     */
    private static double logLikelihood(int k, int n, int ni, int d, double variance) {
        double p1 = -ni * LOG2PI;
        double p2 = -ni * d * Math.log(variance);
        double p3 = -(ni - k);
        double p4 = ni * Math.log(ni);
        double p5 = -ni * Math.log(n);
        return (p1 + p2 + p3) / 2 + p4 + p5;
    }
}
