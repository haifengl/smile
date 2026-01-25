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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;

import smile.math.MathEx;
import smile.math.distance.EuclideanDistance;
import smile.sort.QuickSort;
import smile.stat.distribution.GaussianDistribution;
import smile.util.AlgoStatus;

/**
 * G-Means clustering algorithm, an extended K-Means which tries to
 * automatically determine the number of clusters by normality test.
 * The G-means algorithm is based on a statistical test for the hypothesis
 * that a subset of data follows a Gaussian distribution. G-means runs
 * k-means with increasing k in a hierarchical fashion until the test accepts
 * the hypothesis that the data assigned to each k-means center are Gaussian.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>G. Hamerly and C. Elkan. Learning the k in k-means. NIPS, 2003.</li>
 * </ol>
 * 
 * @see KMeans
 * @see XMeans
 * 
 * @author Haifeng Li
 */
public class GMeans {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GMeans.class);
    private static final double CRITICAL_VALUE = 1.8692;

    /** Constructor. */
    private GMeans() {

    }

    /**
     * Clustering data with the number of clusters
     * determined by G-Means algorithm automatically.
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
     * determined by G-Means algorithm automatically.
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
        double[][] sum = new double[kmax][d];
        double[][] centroids = new double[kmax][];
        double[] mean = MathEx.colMeans(data);
        int[] size = new int[kmax];
        centroids[0] = mean;
        size[0] = n;

        BBDTree bbd = new BBDTree(data);
        var kmeans = new ArrayList<CentroidClustering<double[], double[]>>(kmax);
        ArrayList<double[]> centers = new ArrayList<>();

        int k = 1;
        while (k < kmax) {
            kmeans.clear();
            centers.clear();
            double[] score = new double[k];

            for (int i = 0; i < k; i++) {
                int ni = size[i];
                // don't split too small cluster.
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
                
                double[] v = new double[d];
                for (int j = 0; j < d; j++) {
                    v[j] = clustering.center(0)[j] - clustering.center(1)[j];
                }
                double vp = MathEx.dot(v, v);
                double[] x = new double[ni];
                for (int j = 0; j < ni; j++) {
                    x[j] = MathEx.dot(subset[j], v) / vp;
                }
                
                // normalize to mean 0 and variance 1.
                MathEx.standardize(x);
                score[i] = AndersonDarling(x);
                logger.info("Cluster {} Anderson-Darling adjusted test statistic: {}", i, score[i]);
            }

            int[] index = QuickSort.sort(score);
            for (int i = 0; i < k; i++) {
                if (score[i] <= CRITICAL_VALUE) {
                    centers.add(centroids[index[i]]);
                }
            }

            int m = centers.size();
            for (int i = k; --i >= 0;) {
                if (score[i] > CRITICAL_VALUE) {
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
            double distortion = Double.MAX_VALUE;
            for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
                double wcss = bbd.clustering(k, centroids, sum, size, group);
                diff = distortion - wcss;
                distortion = wcss;
                logger.info("Iteration {}: {}-cluster distortion = {}", iter, k, distortion);
            }

            if (controller != null) {
                controller.submit(new AlgoStatus(k, distortion));
                if (controller.isInterrupted()) break;
            }
        }

        double[] proximity = new double[n];
        IntStream.range(0, k).parallel().forEach(cluster -> {
            double[] centroid = centroids[cluster];
            for (int i = 0; i < n; i++) {
                if (group[i] == cluster) {
                    double dist = MathEx.squaredDistance(data[i], centroid);
                    proximity[i] = dist;
                }
            }
        });

        ToDoubleBiFunction<double[], double[]> distance = new EuclideanDistance();
        return new CentroidClustering<>("G-Means", Arrays.copyOf(centroids, k), distance, group, proximity);
    }
    
    /**
     * Calculates the Anderson-Darling statistic for one-dimensional normality test.
     *
     * @param x the observations to test if drawn from a Gaussian distribution.
     */
    private static double AndersonDarling(double[] x) {
        int n = x.length;
        GaussianDistribution gaussian = GaussianDistribution.getInstance();
        Arrays.sort(x);

        for (int i = 0; i < n; i++) {
            x[i] = gaussian.cdf(x[i]);
            // in case overflow when taking log later.
            if (x[i] == 0) x[i] = 0.0000001;
            if (x[i] == 1) x[i] = 0.9999999;
        }

        double A = 0.0;
        for (int i = 0; i < n; i++) {
            A -= (2*i+1) * (Math.log(x[i]) + Math.log(1-x[n-i-1]));
        }

        A = A / n - n;
        A *= (1 + 4.0/n - 25.0/(n*n));

        return A;
    }
}
