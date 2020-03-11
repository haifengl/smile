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

package smile.base.rbf;

import java.io.Serializable;
import java.util.Arrays;
import smile.clustering.CLARANS;
import smile.clustering.KMeans;
import smile.math.MathEx;
import smile.math.distance.Metric;
import smile.math.distance.EuclideanDistance;
import smile.math.rbf.RadialBasisFunction;
import smile.math.rbf.GaussianRadialBasis;

/**
 * A neuron in radial basis function network. A radial basis function network is an
 * artificial neural network that uses radial basis functions as activation
 * functions. It is a linear combination of radial basis functions.
 * <p>
 * In its basic form, radial basis function network is in the form
 * <p>
 * y(x) = &Sigma; w<sub>i</sub> &phi;(||x-c<sub>i</sub>||)
 * <p>
 * where the approximating function y(x) is represented as a sum of N radial
 * basis functions &phi;, each associated with a different center c<sub>i</sub>,
 * and weighted by an appropriate coefficient w<sub>i</sub>. For distance,
 * one usually chooses Euclidean distance. The weights w<sub>i</sub> can
 * be estimated using the matrix methods of linear least squares, because
 * the approximating function is linear in the weights.
 * <p>
 * The centers c<sub>i</sub> can be randomly selected from training data,
 * or learned by some clustering method (e.g. k-means), or learned together
 * with weight parameters undergo a supervised learning processing
 * (e.g. error-correction learning).
 *
 * @author Haifeng Li
 */
public class RBF<T> implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The center of neuron. */
    private T center;
    /** Radial basis function. */
    private RadialBasisFunction rbf;
    /** Metric distance. */
    private Metric<T> distance;

    /**
     * Constructor.
     *
     * @param center the center of neuron.
     * @param rbf the radial basis functions.
     * @param distance the distance metric functor.
     */
    public RBF(T center, RadialBasisFunction rbf, Metric<T> distance) {
        this.center = center;
        this.rbf = rbf;
        this.distance = distance;
    }

    /**
     * The activation function.
     */
    public double f(T x) {
        return rbf.f(distance.d(x, center));
    }

    /** Makes a set of RBF neurons. */
    public static <T> RBF<T>[] of(T[] centers, RadialBasisFunction basis, Metric<T> distance) {
        int k = centers.length;
        @SuppressWarnings("unchecked")
        RBF<T>[] rbf = new RBF[k];
        for (int i = 0; i < k; i++) {
            rbf[i] = new RBF<>(centers[i], basis, distance);
        }
        return rbf;
    }

    /** Makes a set of RBF neurons. */
    public static <T> RBF<T>[] of(T[] centers, RadialBasisFunction[] basis, Metric<T> distance) {
        int k = centers.length;
        @SuppressWarnings("unchecked")
        RBF<T>[] rbf = new RBF[k];
        for (int i = 0; i < k; i++) {
            rbf[i] = new RBF<>(centers[i], basis[i], distance);
        }
        return rbf;
    }

    /**
     * Estimates the width of RBF. Let d<sub>max</sub> be the maximum
     * distance between the chosen centers, the standard deviation (i.e. width)
     * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
     * k is number of centers. This choice would be close to the optimal
     * solution if the data were uniformly distributed in the input space,
     * leading to a uniform distribution of centroids.
     */
    private static <T> double estimateWidth(T[] centers, Metric<T> distance) {
        int k = centers.length;
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
        return r0;
    }

    /**
     * Estimates the width of RBF. Let d<sub>max</sub> be the maximum
     * distance between the chosen centers, the standard deviation (i.e. width)
     * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
     * k is number of centers. This choice would be close to the optimal
     * solution if the data were uniformly distributed in the input space,
     * leading to a uniform distribution of centroids.
     */
    private static <T> double[] estimateWidth(T[] centers, Metric<T> distance, int p) {
        int k = centers.length;
        double[] r = new double[k];
        GaussianRadialBasis[] basis = new GaussianRadialBasis[k];
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
            basis[i] = new GaussianRadialBasis(r0);
        }

        return r;
    }

    /**
     * Estimates the width of RBF. Let d<sub>max</sub> be the maximum
     * distance between the chosen centers, the standard deviation (i.e. width)
     * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
     * k is number of centers. This choice would be close to the optimal
     * solution if the data were uniformly distributed in the input space,
     * leading to a uniform distribution of centroids.
     */
    private static <T> double[] estimateWidth(T[] x, int[] y, T[] centers, int[] clusterSize, Metric<T> distance, double r) {
        int k = centers.length;
        double[] sigma = new double[k];

        for (int i = 0; i < x.length; i++) {
            sigma[y[i]] += MathEx.sqr(distance.d(x[i], centers[y[i]]));
        }

        for (int i = 0; i < k; i++) {
            if (clusterSize[i] >= 5 || sigma[i] != 0.0) {
                sigma[i] = Math.sqrt(sigma[i] / clusterSize[i]);
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

            sigma[i] *= r;
        }

        return sigma;
    }

    /** Returns a set of Gaussian radial basis with given widths. */
    private static GaussianRadialBasis[] gaussian(double[] width) {
        int k = width.length;
        GaussianRadialBasis[] basis = new GaussianRadialBasis[k];
        for (int i = 0; i < k; i++) {
            basis[i] = new GaussianRadialBasis(width[i]);
        }

        return basis;
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the centroids of K-Means. Let d<sub>max</sub> be the maximum
     * distance between the chosen centers, the standard deviation (i.e. width)
     * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
     * k is number of centers. This choice would be close to the optimal
     * solution if the data were uniformly distributed in the input space,
     * leading to a uniform distribution of centroids.
     * @param x the training dataset.
     * @param k the number of RBF neurons to learn.
     * @return a Gaussian RBF function with parameter learned from data.
     */
    public static RBF<double[]>[] fit(double[][] x, int k) {
        KMeans kmeans = KMeans.fit(x, k, 10, 1E-4);
        double[][] centers = kmeans.centroids;

        EuclideanDistance distance = new EuclideanDistance();
        GaussianRadialBasis basis = new GaussianRadialBasis(estimateWidth(centers, distance));
        return of(centers, basis, distance);
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the centroids of K-Means. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated by the p-nearest neighbors
     * (among centers, not all samples) heuristic. A suggested value for
     * p is 2.
     * @param x the training dataset.
     * @param k the number of RBF neurons to learn.
     * @param p the number of nearest neighbors of centers to estimate the width
     * of Gaussian RBF functions.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    public static RBF<double[]>[] fit(double[][] x, int k, int p) {
        if (p < 1 || p >= k) {
            throw new IllegalArgumentException("Invalid number of nearest neighbors: " + p);
        }

        KMeans kmeans = KMeans.fit(x, k, 10, 1E-4);
        double[][] centers = kmeans.centroids;

        EuclideanDistance distance = new EuclideanDistance();
        double[] width = estimateWidth(centers, distance, p);
        GaussianRadialBasis[] basis = gaussian(width);
        return of(centers, basis, distance);
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the centroids of K-Means. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated as the width of each
     * cluster multiplied with a given scaling parameter r.
     * @param x the training dataset.
     * @param k the number of RBF neurons to learn.
     * @param r the scaling parameter.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    public static RBF<double[]>[] fit(double[][] x, int k, double r) {
        if (r <= 0.0) {
            throw new IllegalArgumentException("Invalid scaling parameter: " + r);
        }

        KMeans kmeans = KMeans.fit(x, k, 10, 1E-4);
        double[][] centers = kmeans.centroids;

        EuclideanDistance distance = new EuclideanDistance();
        double[] width = estimateWidth(x, kmeans.y, centers, kmeans.size, distance, r);
        GaussianRadialBasis[] basis = gaussian(width);

        return of(centers, basis, distance);
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
     * @param k the number of RBF neurons to learn.
     * @param distance the distance functor.
     * @return a Gaussian RBF function with parameter learned from data.
     */
    public static <T> RBF<T>[] fit(T[] x, Metric<T> distance, int k) {
        CLARANS<T> clarans = CLARANS.fit(x, distance::d, k);
        T[] centers = clarans.centroids;

        GaussianRadialBasis basis = new GaussianRadialBasis(estimateWidth(centers, distance));
        return of(centers, basis, distance);
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the medoids of CLARANS. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated by the p-nearest neighbors
     * (among centers, not all samples) heuristic. A suggested value for
     * p is 2.
     * @param x the training dataset.
     * @param k the number of RBF neurons to learn.
     * @param distance the distance functor.
     * @param p the number of nearest neighbors of centers to estimate the width
     * of Gaussian RBF functions.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    public static <T> RBF<T>[] fit(T[] x, Metric<T> distance, int k, int p) {
        if (p < 1 || p >= k) {
            throw new IllegalArgumentException("Invalid number of nearest neighbors: " + p);
        }

        CLARANS<T> clarans = CLARANS.fit(x, distance::d, k);
        T[] centers = clarans.centroids;

        double[] width = estimateWidth(centers, distance, p);
        GaussianRadialBasis[] basis = gaussian(width);

        return of(centers, basis, distance);
    }

    /**
     * Learns Gaussian RBF function and centers from data. The centers are
     * chosen as the medoids of CLARANS. The standard deviation (i.e. width)
     * of Gaussian radial basis function is estimated as the width of each
     * cluster multiplied with a given scaling parameter r.
     * @param x the training dataset.
     * @param k the number of RBF neurons to learn.
     * @param distance the distance functor.
     * @param r the scaling parameter.
     * @return Gaussian RBF functions with parameter learned from data.
     */
    public static <T> RBF<T>[] fit(T[] x, Metric<T> distance, int k, double r) {
        if (r <= 0.0) {
            throw new IllegalArgumentException("Invalid scaling parameter: " + r);
        }

        CLARANS<T> clarans = CLARANS.fit(x, distance::d, k);
        T[] centers = clarans.centroids;

        double[] width = estimateWidth(x, clarans.y, centers, clarans.size, distance, r);
        GaussianRadialBasis[] basis = gaussian(width);

        return of(centers, basis, distance);
    }
}
