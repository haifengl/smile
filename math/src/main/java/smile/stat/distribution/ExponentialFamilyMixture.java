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
 * The finite mixture of distributions from exponential family.
 * The EM algorithm can be used to learn the mixture model from data.
 * EM is particularly useful when the likelihood is an exponential family: the
 * E-step becomes the sum of expectations of sufficient statistics, and the
 * M-step involves maximizing a linear function. In such a case, it is usually
 * possible to derive closed form updates for each step.
 *
 * @author Haifeng Li
 */
public class ExponentialFamilyMixture extends Mixture {

    /**
     * Constructor.
     */
    ExponentialFamilyMixture() {
        super();
    }

    /**
     * Constructor.
     * @param mixture a list of exponential family distributions.
     */
    public ExponentialFamilyMixture(List<Component> mixture) {
        super(mixture);

        for (Component component : mixture) {
            if (component.distribution instanceof ExponentialFamily == false)
                throw new IllegalArgumentException("Component " + component + " is not of exponential family.");
        }
    }

    /**
     * Constructor. The mixture model will be learned from the given data with the
     * EM algorithm.
     * @param mixture the initial guess of mixture. Components may have
     * different distribution form.
     * @param data the training data.
     */
    public ExponentialFamilyMixture(List<Component> mixture, double[] data) {
        this(mixture);

        EM(components, data);
    }

    /**
     * Standard EM algorithm which iteratively alternates
     * Expectation and Maximization steps until convergence.
     *
     * @param mixture the initial configuration.
     * @param x the input data.
     * @return log Likelihood
     */
    double EM(List<Component> mixture, double[] x) {
        return EM(mixture, x, 0.0);
    }

    /**
     * Standard EM algorithm which iteratively alternates
     * Expectation and Maximization steps until convergence.
     *
     * @param mixture the initial configuration.
     * @param x the input data.
     * @param gamma the regularization parameter. Although regularization works
     *              well for high dimensional data, it often reduces the model
     *              to too few components. For one-dimensional data, gamma should
     *              be 0 in general.
     * @return log Likelihood
     */

    double EM(List<Component> mixture, double[] x, double gamma) {
        return EM(mixture, x, gamma, Integer.MAX_VALUE);
    }

    /**
     * Standard EM algorithm which iteratively alternates
     * Expectation and Maximization steps until convergence.
     *
     * @param components the initial configuration.
     * @param x the input data.
     * @param gamma the regularization parameter. Although regularization works
     *              well for high dimensional data, it often reduces the model
     *              to too few components. For one-dimensional data, gamma should
     *              be 0 in general.
     * @param maxIter the maximum number of iterations.
     * @return log Likelihood
     */
    double EM(List<Component> components, double[] x , double gamma, int maxIter) {
        if (x.length < components.size() / 2)
            throw new IllegalArgumentException("Too many components");

        if (gamma < 0.0 || gamma > 0.2)
            throw new IllegalArgumentException("Invalid regularization factor gamma.");

        int n = x.length;
        int m = components.size();

        double[][] posteriori = new double[m][n];

        // Log Likelihood
        double L = 0.0;
        for (double xi : x) {
            double p = 0.0;
            for (Component c : components)
                p += c.priori * c.distribution.p(xi);
            if (p > 0) L += Math.log(p);
        }

        // EM loop until convergence
        int iter = 0;
        for (; iter < maxIter; iter++) {

            // Expectation step
            for (int i = 0; i < m; i++) {
                Component c = components.get(i);

                for (int j = 0; j < n; j++) {
                    posteriori[i][j] = c.priori * c.distribution.p(x[j]);
                }
            }

            // Normalize posteriori probability.
            for (int j = 0; j < n; j++) {
                double p = 0.0;

                for (int i = 0; i < m; i++) {
                    p += posteriori[i][j];
                }

                for (int i = 0; i < m; i++) {
                    posteriori[i][j] /= p;
                }

                // Adjust posterior probabilites based on Regularized EM algorithm.
                if (gamma > 0) {
                    for (int i = 0; i < m; i++) {
                        posteriori[i][j] *= (1 + gamma * Math.log2(posteriori[i][j]));
                        if (Double.isNaN(posteriori[i][j]) || posteriori[i][j] < 0.0) {
                            posteriori[i][j] = 0.0;
                        }
                    }
                }
            }

            // Maximization step
            ArrayList<Component> newConfig = new ArrayList<>();
            for (int i = 0; i < m; i++)
                newConfig.add(((ExponentialFamily) components.get(i).distribution).M(x, posteriori[i]));

            double sumAlpha = 0.0;
            for (int i = 0; i < m; i++)
                sumAlpha += newConfig.get(i).priori;

            for (int i = 0; i < m; i++)
                newConfig.get(i).priori /= sumAlpha;

            double newL = 0.0;
            for (double xi : x) {
                double p = 0.0;
                for (Component c : newConfig) {
                    p += c.priori * c.distribution.p(xi);
                }
                if (p > 0) newL += Math.log(p);
            }

            if (newL > L) {
                L = newL;
                components.clear();
                components.addAll(newConfig);
            } else {
                break;
            }
        }

        return L;
    }
}
