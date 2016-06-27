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

package smile.stat.distribution;

import java.util.List;
import java.util.ArrayList;
import smile.math.Math;

/**
 * Finite multivariate Gaussian mixture. The EM algorithm is provide to learned
 * the mixture model from data. BIC score is employed to estimate the number
 * of components.
 *
 * @author Haifeng Li
 */
public class MultivariateGaussianMixture extends MultivariateExponentialFamilyMixture {

    /**
     * Constructor.
     * @param mixture a list of multivariate Gaussian distributions.
     */
    public MultivariateGaussianMixture(List<Component> mixture) {
        super(mixture);
    }

    /**
     * Constructor. The Gaussian mixture model will be learned from the given data
     * with the EM algorithm.
     * @param data the training data.
     * @param k the number of components.
     */
    public MultivariateGaussianMixture(double[][] data, int k) {
        this(data, k, false);
    }

    /**
     * Constructor. The Gaussian mixture model will be learned from the given data
     * with the EM algorithm.
     * @param data the training data.
     * @param k the number of components.
     * @param diagonal true if the components have diagonal covariance matrix.
     */
    public MultivariateGaussianMixture(double[][] data, int k, boolean diagonal) {
        if (k < 2)
            throw new IllegalArgumentException("Invalid number of components in the mixture.");

        int n = data.length;
        int d = data[0].length;
        double[] mu = new double[d];
        double[][] sigma = new double[d][d];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                mu[j] += data[i][j];
            }
        }

        for (int j = 0; j < d; j++) {
            mu[j] /= n;
        }

        if (diagonal) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < d; j++) {
                    sigma[j][j] += (data[i][j] - mu[j]) * (data[i][j] - mu[j]);
                }
            }

            for (int j = 0; j < d; j++) {
                sigma[j][j] /= (n - 1);
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < d; j++) {
                    for (int l = 0; l <= j; l++) {
                        sigma[j][l] += (data[i][j] - mu[j]) * (data[i][l] - mu[l]);
                    }
                }
            }

            for (int j = 0; j < d; j++) {
                for (int l = 0; l <= j; l++) {
                    sigma[j][l] /= (n - 1);
                    sigma[l][j] = sigma[j][l];
                }
            }
        }

        double[] centroid = data[Math.randomInt(n)];
        Component c = new Component();
        c.priori = 1.0 / k;
        MultivariateGaussianDistribution gaussian = new MultivariateGaussianDistribution(centroid, sigma);
        gaussian.diagonal = diagonal;
        c.distribution = gaussian;
        components.add(c);

        // We use a the kmeans++ algorithm to find the initial centers.
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
                double dist = Math.squaredDistance(data[j], centroid);
                if (dist < D[j]) {
                    D[j] = dist;
                }
            }

            double cutoff = Math.random() * Math.sum(D);
            double cost = 0.0;
            int index = 0;
            for (; index < n; index++) {
                cost += D[index];
                if (cost >= cutoff)
                    break;
            }

            centroid = data[index];
            c = new Component();
            c.priori = 1.0 / k;
            gaussian = new MultivariateGaussianDistribution(centroid, sigma);
            gaussian.diagonal = diagonal;
            c.distribution = gaussian;
            components.add(c);
        }

        EM(components, data);
    }

    /**
     * Constructor. The Gaussian mixture model will be learned from the given data
     * with the EM algorithm. The number of components will be selected by BIC.
     * @param data the training data.
     */
    public MultivariateGaussianMixture(double[][] data) {
        this(data, false);
    }

    /**
     * Constructor. The Gaussian mixture model will be learned from the given data
     * with the EM algorithm. The number of components will be selected by BIC.
     * @param data the training data.
     * @param diagonal true if the components have diagonal covariance matrix.
     */
    @SuppressWarnings("unchecked")
    public MultivariateGaussianMixture(double[][] data, boolean diagonal) {
        if (data.length < 20)
            throw new IllegalArgumentException("Too few samples.");

        ArrayList<Component> mixture = new ArrayList<>();
        Component c = new Component();
        c.priori = 1.0;
        c.distribution = new MultivariateGaussianDistribution(data, diagonal);
        mixture.add(c);

        int freedom = 0;
        for (int i = 0; i < mixture.size(); i++)
            freedom += mixture.get(i).distribution.npara();

        double bic = 0.0;
        for (double[] x : data) {
            double p = c.distribution.p(x);
            if (p > 0) bic += Math.log(p);
        }
        bic -= 0.5 * freedom * Math.log(data.length);

        double b = Double.NEGATIVE_INFINITY;
        while (bic > b) {
            b = bic;
            components = (ArrayList<Component>) mixture.clone();

            split(mixture);
            bic = EM(mixture, data);

            freedom = 0;
            for (int i = 0; i < mixture.size(); i++)
                freedom += mixture.get(i).distribution.npara();

            bic -= 0.5 * freedom * Math.log(data.length);
        }
    }

    /**
     * Split the most heterogeneous cluster along its main direction (eigenvector).
     */
    private void split(List<Component> mixture) {
        // Find most dispersive cluster (biggest sigma)
        Component componentToSplit = null;

        double maxSigma = 0.0;
        for (Component c : mixture) {
            double sigma = ((MultivariateGaussianDistribution) c.distribution).scatter();
            if (sigma > maxSigma) {
                maxSigma = sigma;
                componentToSplit = c;
            }
        }

        // Splits the component
        double[][] delta = ((MultivariateGaussianDistribution) componentToSplit.distribution).cov();
        double[] mu = ((MultivariateGaussianDistribution) componentToSplit.distribution).mean();

        Component c = new Component();
        c.priori = componentToSplit.priori / 2;
        double[] mu1 = new double[mu.length];
        for (int i = 0; i < mu.length; i++)
            mu1[i] = mu[i] + Math.sqrt(delta[i][i])/2;
        c.distribution = new MultivariateGaussianDistribution(mu1, delta);
        mixture.add(c);

        c = new Component();
        c.priori = componentToSplit.priori / 2;
        double[] mu2 = new double[mu.length];
        for (int i = 0; i < mu.length; i++)
            mu2[i] = mu[i] - Math.sqrt(delta[i][i])/2;
        c.distribution = new MultivariateGaussianDistribution(mu2, delta);
        mixture.add(c);

        mixture.remove(componentToSplit);
    }
}