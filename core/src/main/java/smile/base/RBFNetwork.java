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

package smile.base;

import java.util.Arrays;

import smile.clustering.CLARANS;
import smile.clustering.KMeans;
import smile.math.MathEx;
import smile.math.distance.Metric;
import smile.math.rbf.GaussianRadialBasis;

/**
 * RBFNetwork trait.
 *
 * @author Haifeng Li
 */
public interface RBFNetwork {

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the centroids of K-Means. Let d<sub>max</sub> be the maximum
     * distance between the chosen centers, the standard deviation (i.e. width)
     * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
     * k is number of centers. This choice would be close to the optimal
     * solution if the data were uniformly distributed in the input space,
     * leading to a uniform distribution of centroids.
     * @param x the training dataset.
     * @param centers an array to store centers on output. Its length is used as k of k-means.
     * @return a Gaussian RBF function with parameter learned from data.
     */
    static GaussianRadialBasis fitGaussianRadialBasis(double[][] x, double[][] centers) {
        int k = centers.length;
        KMeans kmeans = new KMeans(x, k, 10);
        System.arraycopy(kmeans.centroids(), 0, centers, 0, k);

        double r0 = 0.0;
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < i; j++) {
                double d = MathEx.distance(centers[i], centers[j]);
                if (r0 < d) {
                    r0 = d;
                }
            }
        }

        r0 /= Math.sqrt(2*k);
        return new GaussianRadialBasis(r0);
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the centroids of K-Means. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated by the p-nearest neighbors
     * (among centers, not all samples) heuristic. A suggested value for
     * p is 2.
     * @param x the training dataset.
     * @param centers an array to store centers on output. Its length is used as k of k-means.
     * @param p the number of nearest neighbors of centers to estimate the width
     * of Gaussian RBF functions.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    static GaussianRadialBasis[] fitGaussianRadialBasis(double[][] x, double[][] centers, int p) {
        int k = centers.length;

        if (p < 1 || p >= k) {
            throw new IllegalArgumentException("Invalid number of nearest neighbors: " + p);
        }

        KMeans kmeans = new KMeans(x, k, 10);
        System.arraycopy(kmeans.centroids(), 0, centers, 0, k);

        double[] r = new double[k];
        GaussianRadialBasis[] rbf = new GaussianRadialBasis[k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                r[j] = MathEx.distance(centers[i], centers[j]);
            }

            Arrays.sort(r);
            double r0 = 0.0;
            for (int j = 1; j <= p; j++) {
                r0 += r[j];
            }
            r0 /= p;
            rbf[i] = new GaussianRadialBasis(r0);
        }

        return rbf;
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the centroids of K-Means. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated as the width of each
     * cluster multiplied with a given scaling parameter r.
     * @param x the training dataset.
     * @param centers an array to store centers on output. Its length is used as k of k-means.
     * @param r the scaling parameter.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    static GaussianRadialBasis[] fitGaussianRadialBasis(double[][] x, double[][] centers, double r) {
        if (r <= 0.0) {
            throw new IllegalArgumentException("Invalid scaling parameter: " + r);
        }

        int k = centers.length;
        KMeans kmeans = new KMeans(x, k, 10);
        System.arraycopy(kmeans.centroids(), 0, centers, 0, k);

        int n = x.length;
        int[] ni = kmeans.getClusterSize();
        int[] y = kmeans.getClusterLabel();
        double[] sigma = new double[k];
        for (int i = 0; i < n; i++) {
            sigma[y[i]] += MathEx.squaredDistance(x[i], centers[y[i]]);
        }

        GaussianRadialBasis[] rbf = new GaussianRadialBasis[k];
        for (int i = 0; i < k; i++) {
            if (ni[i] >= 5 || sigma[i] != 0.0) {
                sigma[i] = Math.sqrt(sigma[i] / ni[i]);
            } else {
                sigma[i] = Double.POSITIVE_INFINITY;
                for (int j = 0; j < k; j++) {
                    if (i != j) {
                        double d = MathEx.distance(centers[i], centers[j]);
                        if (d < sigma[i]) {
                            sigma[i] = d;
                        }
                    }
                }
                sigma[i] /= 2.0;
            }

            rbf[i] = new GaussianRadialBasis(r * sigma[i]);
        }

        return rbf;
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the medoids of CLARANS. Let d<sub>max</sub> be the maximum
     * distance between the chosen centers, the standard deviation (i.e. width)
     * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
     * k is number of centers. In this way, the radial basis functions are not
     * too peaked or too flat. This choice would be close to the optimal
     * solution if the data were uniformly distributed in the input space,
     * leading to a uniform distribution of medoids.
     * @param x the training dataset.
     * @param centers an array to store centers on output. Its length is used as k of CLARANS.
     * @param distance the distance functor.
     * @return a Gaussian RBF function with parameter learned from data.
     */
    static <T> GaussianRadialBasis fitGaussianRadialBasis(T[] x, T[] centers, Metric<T> distance) {
        int k = centers.length;
        CLARANS<T> clarans = new CLARANS<>(x, distance, k, Math.min(100, (int) Math.round(0.01 * k * (x.length - k))));
        System.arraycopy(clarans.medoids(), 0, centers, 0, k);

        double r0 = 0.0;
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < i; j++) {
                double d = distance.d(centers[i], centers[j]);
                if (r0 < d) {
                    r0 = d;
                }
            }
        }

        r0 /= Math.sqrt(2*k);
        return new GaussianRadialBasis(r0);
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the medoids of CLARANS. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated by the p-nearest neighbors
     * (among centers, not all samples) heuristic. A suggested value for
     * p is 2.
     * @param x the training dataset.
     * @param centers an array to store centers on output. Its length is used as k of CLARANS.
     * @param distance the distance functor.
     * @param p the number of nearest neighbors of centers to estimate the width
     * of Gaussian RBF functions.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    static <T> GaussianRadialBasis[] fitGaussianRadialBasis(T[] x, T[] centers, Metric<T> distance, int p) {
        if (p < 1) {
            throw new IllegalArgumentException("Invalid number of nearest neighbors: " + p);
        }

        int k = centers.length;
        CLARANS<T> clarans = new CLARANS<>(x, distance, k, Math.min(100, (int) Math.round(0.01 * k * (x.length - k))));
        System.arraycopy(clarans.medoids(), 0, centers, 0, k);

        p = Math.min(p, k-1);
        double[] r = new double[k];
        GaussianRadialBasis[] rbf = new GaussianRadialBasis[k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                r[j] = distance.d(centers[i], centers[j]);
            }

            Arrays.sort(r);
            double r0 = 0.0;
            for (int j = 1; j <= p; j++) {
                r0 += r[j];
            }
            r0 /= p;
            rbf[i] = new GaussianRadialBasis(r0);
        }

        return rbf;
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the medoids of CLARANS. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated as the width of each
     * cluster multiplied with a given scaling parameter r.
     * @param x the training dataset.
     * @param centers an array to store centers on output. Its length is used as k of CLARANS.
     * @param distance the distance functor.
     * @param r the scaling parameter.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    static <T> GaussianRadialBasis[] fitGaussianRadialBasis(T[] x, T[] centers, Metric<T> distance, double r) {
        if (r <= 0.0) {
            throw new IllegalArgumentException("Invalid scaling parameter: " + r);
        }

        int k = centers.length;
        CLARANS<T> clarans = new CLARANS<>(x, distance, k, Math.min(100, (int) Math.round(0.01 * k * (x.length - k))));
        System.arraycopy(clarans.medoids(), 0, centers, 0, k);

        int n = x.length;
        int[] y = clarans.getClusterLabel();
        double[] sigma = new double[k];
        for (int i = 0; i < n; i++) {
            sigma[y[i]] += MathEx.sqr(distance.d(x[i], centers[y[i]]));
        }

        int[] ni = clarans.getClusterSize();
        GaussianRadialBasis[] rbf = new GaussianRadialBasis[k];
        for (int i = 0; i < k; i++) {
            if (ni[i] >= 5 || sigma[i] == 0.0) {
                sigma[i] = Math.sqrt(sigma[i] / ni[i]);
            } else {
                sigma[i] = Double.POSITIVE_INFINITY;
                for (int j = 0; j < k; j++) {
                    if (i != j) {
                        double d = distance.d(centers[i], centers[j]);
                        if (d < sigma[i]) {
                            sigma[i] = d;
                        }
                    }
                }
                sigma[i] /= 2.0;
            }

            rbf[i] = new GaussianRadialBasis(r * sigma[i]);
        }

        return rbf;
    }
}
