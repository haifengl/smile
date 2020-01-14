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
import java.util.Arrays;
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
public class XMeans extends CentroidClustering<double[], double[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(XMeans.class);
    private static final double LOG2PI = Math.log(Math.PI * 2.0);

    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param centroids the centroids of each cluster.
     * @param y the cluster labels.
     */
    public XMeans(double distortion, double[][] centroids, int[] y) {
        super(distortion, centroids, y);
    }

    @Override
    public double distance(double[] x, double[] y) {
        return MathEx.squaredDistance(x, y);
    }

    /**
     * Clustering data with the number of clusters
     * determined by X-Means algorithm automatically.
     * @param data the input data of which each row is an observation.
     * @param kmax the maximum number of clusters.
     */
    public static XMeans fit(double[][] data, int kmax) {
        return fit(data, kmax, 100, 1E-4);
    }

    /**
     * Clustering data with the number of clusters
     * determined by X-Means algorithm automatically.
     * @param data the input data of which each row is an observation.
     * @param kmax the maximum number of clusters.
     * @param maxIter the maximum number of iterations for k-means.
     * @param tol the tolerance of k-means convergence test.
     */
    public static XMeans fit(double[][] data, int kmax, int maxIter, double tol) {
        if (kmax < 2) {
            throw new IllegalArgumentException("Invalid parameter kmax = " + kmax);
        }

        int n = data.length;
        int d = data[0].length;
        int k = 1;

        int[] size = new int[kmax];
        size[0] = n;

        int[] y = new int[n];
        double[][] sum = new double[kmax][d];

        double[] mean = MathEx.colMeans(data);
        double[][] centroids = {mean};

        double distortion = Arrays.stream(data).parallel().mapToDouble(x -> MathEx.squaredDistance(x, mean)).sum();
        double[] distortions = new double[kmax];
        distortions[0] = distortion;

        BBDTree bbd = new BBDTree(data);
        KMeans[] kmeans = new KMeans[kmax];
        ArrayList<double[]> centers = new ArrayList<>();

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
                    kmeans[i] = null;
                    continue;
                }

                double[][] subset = new double[ni][];
                for (int j = 0, l = 0; j < n; j++) {
                    if (y[j] == i) {
                        subset[l++] = data[j];
                    }
                }

                kmeans[i] = KMeans.fit(subset, 2, maxIter, tol);
                double newBIC = bic(2, ni, d, kmeans[i].distortion, kmeans[i].size);
                double oldBIC = bic(ni, d, distortions[i]);
                score[i] = newBIC - oldBIC;
                logger.info(String.format("Cluster %3d BIC: %12.4f, BIC after split: %12.4f, improvement: %12.4f", i, oldBIC, newBIC, score[i]));
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
                        centers.add(kmeans[index[i]].centroids[0]);
                        centers.add(kmeans[index[i]].centroids[1]);
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
            centroids = centers.toArray(new double[k][]);

            double diff = Double.MAX_VALUE;
            for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
                double wcss = bbd.clustering(centroids, sum, size, y);

                diff = distortion - wcss;
                distortion = wcss;
            }

            Arrays.fill(distortions, 0.0);
            IntStream.range(0, k).parallel().forEach(cluster -> {
                double[] centroid = centers.get(cluster);
                for (int i = 0; i < n; i++) {
                    if (y[i] == cluster) {
                        distortions[cluster] += MathEx.squaredDistance(data[i], centroid);
                    }
                }
            });

            logger.info(String.format("Distortion with %d clusters: %.5f", k, distortion));
        }

        return new XMeans(distortion, centroids, y);
    }

    /**
     * Calculates the BIC for single cluster.
     * @param n the total number of observations.
     * @param d the dimensionality of data.
     * @param distortion the distortion of clusters.
     * @return the BIC score.
     */
    private static double bic(int n, int d, double distortion) {
        double variance = distortion / (n - 1);

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
     * @param clusterSize the number of observations in each cluster.
     * @return the BIC score.
     */
    private static double bic(int k, int n, int d, double distortion, int[] clusterSize) {
        double variance = distortion / (n - k);

        double L = 0.0;
        for (int i = 0; i < k; i++) {
            L += logLikelihood(k, n, clusterSize[i], d, variance);
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
