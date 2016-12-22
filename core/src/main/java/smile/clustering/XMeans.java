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
package smile.clustering;

import java.io.Serializable;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
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
public class XMeans extends KMeans implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(XMeans.class);

    private static final double LOG2PI = Math.log(Math.PI * 2.0);

    /**
     * Constructor. Clustering data with the number of clusters being
     * automatically determined by X-Means algorithm.
     * @param data the input data of which each row is a sample.
     * @param kmax the maximum number of clusters.
     */
    public XMeans(double[][] data, int kmax) {
        if (kmax < 2) {
            throw new IllegalArgumentException("Invalid parameter kmax = " + kmax);
        }

        int n = data.length;
        int d = data[0].length;

        k = 1;
        size = new int[k];
        size[0] = n;
        y = new int[n];
        centroids = new double[k][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                centroids[0][j] += data[i][j];
            }
        }

        for (int j = 0; j < d; j++) {
            centroids[0][j] /= n;
        }

        // within-cluster sum of squares
        double[] wcss = new double[k];
        for (int i = 0; i < n; i++) {
            wcss[0] += Math.squaredDistance(data[i], centroids[0]);
        }

        distortion = wcss[0];
        logger.info(String.format("X-Means distortion with %d clusters: %.5f", k, distortion));

        BBDTree bbd = new BBDTree(data);
        while (k < kmax) {
            ArrayList<double[]> centers = new ArrayList<>();
            double[] score = new double[k];
            KMeans[] kmeans = new KMeans[k];
            
            for (int i = 0; i < k; i++) {
                // don't split too small cluster. anyway likelihood estimation
                // not accurate in this case.
                if (size[i] < 25) {
                    logger.info("Cluster {} too small to split: {} samples", i, size[i]);
                    continue;
                }
                
                double[][] subset = new double[size[i]][];
                for (int j = 0, l = 0; j < n; j++) {
                    if (y[j] == i) {
                        subset[l++] = data[j];
                    }
                }

                kmeans[i] = new KMeans(subset, 2, 100, 4);
                double newBIC = bic(2, size[i], d, kmeans[i].distortion, kmeans[i].size);
                double oldBIC = bic(size[i], d, wcss[i]);
                score[i] = newBIC - oldBIC;
                logger.info(String.format("Cluster %3d\tBIC: %.5f\tBIC after split: %.5f\timprovement: %.5f", i, oldBIC, newBIC, score[i]));
            }

            int[] index = QuickSort.sort(score);
            for (int i = 0; i < k; i++) {
                if (score[index[i]] <= 0.0) {
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
                break;
            }

            k = centers.size();
            double[][] sums = new double[k][d];
            size = new int[k];
            centroids = new double[k][];
            for (int i = 0; i < k; i++) {
                centroids[i] = centers.get(i);
            }

            distortion = Double.MAX_VALUE;
            for (int iter = 0; iter < 100; iter++) {
                double newDistortion = bbd.clustering(centroids, sums, size, y);
                for (int i = 0; i < k; i++) {
                    if (size[i] > 0) {
                        for (int j = 0; j < d; j++) {
                            centroids[i][j] = sums[i][j] / size[i];
                        }
                    }
                }

                if (distortion <= newDistortion) {
                    break;
                } else {
                    distortion = newDistortion;
                }
            }

            wcss = new double[k];
            for (int i = 0; i < n; i++) {
                wcss[y[i]] += Math.squaredDistance(data[i], centroids[y[i]]);
            }

            logger.info(String.format("X-Means distortion with %d clusters: %.5f", k, distortion));
        }
    }

    /**
     * Calculates the BIC for single cluster.
     * @param n the total number of samples.
     * @param d the dimensionality of data.
     * @param distortion the distortion of clusters.
     * @return the BIC score.
     */
    private double bic(int n, int d, double distortion) {
        double variance = distortion / (n - 1);

        double p1 = -n * LOG2PI;
        double p2 = -n * d * Math.log(variance);
        double p3 = -(n - 1);
        double L = (p1 + p2 + p3) / 2;

        int numParameters = d + 1;
        return L - 0.5 * numParameters * Math.log(n);
    }

    /**
     * Calculates the BIC for the given set of centers.
     * @param k the number of clusters.
     * @param n the total number of samples.
     * @param d the dimensionality of data.
     * @param distortion the distortion of clusters.
     * @param clusterSize the number of samples in each cluster.
     * @return the BIC score.
     */
    private double bic(int k, int n, int d, double distortion, int[] clusterSize) {
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
     * @param n the total number of samples.
     * @param ni the number of samples belong to this cluster.
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
        double loglike = (p1 + p2 + p3) / 2 + p4 + p5;
        return loglike;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("X-Means distortion: %.5f%n", distortion));
        sb.append(String.format("Clusters of %d data points of dimension %d:%n", y.length, centroids[0].length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }

        return sb.toString();
    }
}
