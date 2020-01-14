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
import smile.math.MathEx;
import smile.sort.QuickSort;
import smile.stat.distribution.GaussianDistribution;

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
public class GMeans extends CentroidClustering<double[], double[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GMeans.class);

    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param centroids the centroids of each cluster.
     * @param y the cluster labels.
     */
    public GMeans(double distortion, double[][] centroids, int[] y) {
        super(distortion, centroids, y);
    }

    @Override
    public double distance(double[] x, double[] y) {
        return MathEx.squaredDistance(x, y);
    }

    /**
     * Clustering data with the number of clusters
     * determined by G-Means algorithm automatically.
     * @param data the input data of which each row is an observation.
     * @param kmax the maximum number of clusters.
     */
    public static GMeans fit(double[][] data, int kmax) {
        return fit(data, kmax, 100, 1E-4);
    }

    /**
     * Clustering data with the number of clusters
     * determined by G-Means algorithm automatically.
     * @param data the input data of which each row is an observation.
     * @param kmax the maximum number of clusters.
     * @param maxIter the maximum number of iterations for k-means.
     * @param tol the tolerance of k-means convergence test.
     */
    public static GMeans fit(double[][] data, int kmax, int maxIter, double tol) {
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

        BBDTree bbd = new BBDTree(data);
        KMeans[] kmeans = new KMeans[kmax];
        ArrayList<double[]> centers = new ArrayList<>();

        while (k < kmax) {
            centers.clear();
            double[] score = new double[k];

            for (int i = 0; i < k; i++) {
                int ni = size[i];
                // don't split too small cluster.
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
                
                double[] v = new double[d];
                for (int j = 0; j < d; j++) {
                    v[j] = kmeans[i].centroids[0][j] - kmeans[i].centroids[1][j];
                }
                double vp = MathEx.dot(v, v);
                double[] x = new double[ni];
                for (int j = 0; j < x.length; j++) {
                    x[j] = MathEx.dot(subset[j], v) / vp;
                }
                
                // normalize to mean 0 and variance 1.
                MathEx.standardize(x);

                score[i] = AndersonDarling(x);
                logger.info(String.format("Cluster %d Anderson-Darling adjusted test statistic: %7.4f", i, score[i]));
            }

            int[] index = QuickSort.sort(score);
            for (int i = 0; i < k; i++) {
                if (score[i] <= 1.8692) {
                    centers.add(centroids[index[i]]);
                }
            }

            int m = centers.size();
            for (int i = k; --i >= 0;) {
                if (score[i] > 1.8692) {
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

            logger.info(String.format("Distortion with %d clusters: %.5f%n", k, distortion));
        }

        return new GMeans(distortion, centroids, y);
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
