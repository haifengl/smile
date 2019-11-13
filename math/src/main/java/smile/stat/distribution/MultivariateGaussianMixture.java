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

package smile.stat.distribution;

import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;

/**
 * Finite multivariate Gaussian mixture. The EM algorithm is provide to learned
 * the mixture model from data. The BIC score is employed to estimate the number
 * of components.
 *
 * @author Haifeng Li
 */
public class MultivariateGaussianMixture extends MultivariateExponentialFamilyMixture {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MultivariateGaussianMixture.class);

    /**
     * Constructor.
     * @param components a list of multivariate Gaussian distributions.
     */
    public MultivariateGaussianMixture(Component... components) {
        this(0.0, 1, components);
    }

    /**
     * Constructor.
     * @param components a list of multivariate Gaussian distributions.
     * @param L the log-likelihood.
     * @param n the number of samples to fit the distribution.
     */
    private MultivariateGaussianMixture(double L, int n, Component... components) {
        super(L, n, components);

        for (Component component : components) {
            if (component.distribution instanceof MultivariateGaussianDistribution == false) {
                throw new IllegalArgumentException("Component " + component + " is not of Gaussian distribution.");
            }
        }
    }

    /**
     * Fits the Gaussian mixture model with the EM algorithm.
     * @param data the training data.
     * @param k the number of components.
     */
    public static MultivariateGaussianMixture fit(int k, double[][] data) {
        return fit(k, data, false);
    }

    /**
     * Fits the Gaussian mixture model with the EM algorithm.
     * @param data the training data.
     * @param k the number of components.
     * @param diagonal true if the components have diagonal covariance matrix.
     */
    public static MultivariateGaussianMixture fit(int k, double[][] data, boolean diagonal) {
        if (k < 2)
            throw new IllegalArgumentException("Invalid number of components in the mixture.");

        int n = data.length;
        int d = data[0].length;
        double[] mu = MathEx.colMeans(data);

        double[] centroid = data[MathEx.randomInt(n)];
        double[] variance = null;
        DenseMatrix cov = null;
        MultivariateGaussianDistribution gaussian;
        if (diagonal) {
            variance = new double[d];
            for (int i = 0; i < n; i++) {
                double[] x = data[i];
                for (int j = 0; j < d; j++) {
                    variance[j] += (x[j] - mu[j]) * (x[j] - mu[j]);
                }
            }

            int n1 = n - 1;
            for (int j = 0; j < d; j++) {
                variance[j] /= n1;
            }
            gaussian = new MultivariateGaussianDistribution(centroid, variance);
        } else {
            cov = Matrix.of(MathEx.cov(data, mu));
            gaussian = new MultivariateGaussianDistribution(centroid, cov);
        }

        Component[] components = new Component[k];
        components[0] = new Component(1.0 / k, gaussian);

        // We use the kmeans++ algorithm to find the initial centers.
        // Initially, all components have same covariance matrix.
        double[] D = new double[n];
        for (int i = 0; i < n; i++) {
            D[i] = Double.MAX_VALUE;
        }

        // pick the next center
        for (int i = 1; i < k; i++) {
            // Loop over the samples and compare them to the most recent center.  Store
            // the distance from each sample to its closest center in scores.
            for (int j = 0; j < n; j++) {
                // compute the distance between this sample and the current center
                double dist = MathEx.squaredDistance(data[j], centroid);
                if (dist < D[j]) {
                    D[j] = dist;
                }
            }

            double cutoff = MathEx.random() * MathEx.sum(D);
            double cost = 0.0;
            int index = 0;
            for (; index < n; index++) {
                cost += D[index];
                if (cost >= cutoff) break;
            }

            centroid = data[index];
            gaussian = diagonal ? new MultivariateGaussianDistribution(centroid, variance) : new MultivariateGaussianDistribution(centroid, cov);
            components[i] = new Component(1.0 / k, gaussian);
        }

        MultivariateExponentialFamilyMixture model = fit(data, components);
        return new MultivariateGaussianMixture(model.L, data.length, model.components);
    }

    /**
     * Fits the Gaussian mixture model with the EM algorithm.
     * The number of components will be selected by BIC.
     * @param data the training data.
     */
    public static MultivariateGaussianMixture fit(double[][] data) {
        return fit(data, false);
    }

    /**
     * Fits the Gaussian mixture model with the EM algorithm.
     * The number of components will be selected by BIC.
     * @param data the training data.
     * @param diagonal true if the components have diagonal covariance matrix.
     */
    @SuppressWarnings("unchecked")
    public static MultivariateGaussianMixture fit(double[][] data, boolean diagonal) {
        if (data.length < 20)
            throw new IllegalArgumentException("Too few samples.");

        MultivariateGaussianMixture mixture = new MultivariateGaussianMixture(new Component(1.0, MultivariateGaussianDistribution.fit(data, diagonal)));
        double bic = mixture.bic(data);
        logger.info(String.format("The BIC of %s = %.4f", mixture, bic));

        for (int k = 2; k < data.length / 20; k++) {
            MultivariateExponentialFamilyMixture model = fit(k, data);
            logger.info(String.format("The BIC of %s = %.4f", model, model.bic));

            if (model.bic <= bic) break;

            mixture = new MultivariateGaussianMixture(model.L, data.length, model.components);
            bic = model.bic;
        }

        return mixture;
    }

    /**
     * Split the most heterogeneous cluster along its main direction (eigenvector).
     */
    private static Component[] split(Component[] components) {
        // Find most dispersive cluster (biggest sigma)
        int k = components.length;
        int index = -1;
        double maxSigma = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            Component c = components[i];
            double sigma = ((MultivariateGaussianDistribution) c.distribution).scatter();
            if (sigma > maxSigma) {
                maxSigma = sigma;
                index = i;
            }
        }

        // Splits the component
        Component component = components[index];
        double priori = component.priori / 2;
        DenseMatrix delta = component.distribution.cov();
        double[] mu = component.distribution.mean();

        Component[] mixture = new Component[k+1];
        System.arraycopy(components, 0, mixture, 0, k);

        double[] mu1 = new double[mu.length];
        double[] mu2 = new double[mu.length];
        for (int i = 0; i < mu.length; i++) {
            mu1[i] = mu[i] + Math.sqrt(delta.get(i, i))/2;
            mu2[i] = mu[i] - Math.sqrt(delta.get(i, i)) / 2;
        }

        mixture[index] = new Component(priori, new MultivariateGaussianDistribution(mu1, delta));
        mixture[k] = new Component(priori, new MultivariateGaussianDistribution(mu2, delta));
        return mixture;
    }
}