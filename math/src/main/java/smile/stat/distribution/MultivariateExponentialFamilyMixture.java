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

/**
 * The finite mixture of distributions from multivariate exponential family.
 * The EM algorithm can be used to learn the mixture model from data.
 *
 * @author Haifeng Li
 */
public class MultivariateExponentialFamilyMixture extends MultivariateMixture {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MultivariateExponentialFamilyMixture.class);

    /** The log-likelihood when the distribution is fit on a sample data. */
    public final double L;
    /** The BIC score when the distribution is fit on a sample data. */
    public final double bic;

    /**
     * Constructor.
     * @param components a list of multivariate exponential family distributions.
     */
    public MultivariateExponentialFamilyMixture(Component... components) {
        this(0.0, 1, components);
    }

    /**
     * Constructor.
     * @param components a list of discrete exponential family distributions.
     * @param L the log-likelihood.
     * @param n the number of samples to fit the distribution.
     */
    MultivariateExponentialFamilyMixture(double L, int n, Component... components) {
        super(components);

        for (Component component : components) {
            if (component.distribution instanceof MultivariateExponentialFamily == false) {
                throw new IllegalArgumentException("Component " + component + " is not of multivariate exponential family.");
            }
        }

        this.L = L;
        this.bic = L - 0.5 * length() * Math.log(n);
    }

    /**
     * Fits the mixture model with the EM algorithm.
     * @param x the training data.
     * @param components the initial configuration of mixture. Components may have
     *                   different distribution form.
     */
    public static MultivariateExponentialFamilyMixture fit(double[][] x, Component... components) {
        return fit(x, components, 0.2, 500, 1E-4);
    }

    /**
     * Fits the mixture model with the EM algorithm.
     *
     * @param x the training data.
     * @param components the initial configuration of mixture. Components may have
     *                   different distribution form.
     * @param gamma the regularization parameter.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance of convergence test.
     */
    public static MultivariateExponentialFamilyMixture fit(double[][] x, Component[] components, double gamma, int maxIter, double tol) {
        if (x.length < components.length / 2) {
            throw new IllegalArgumentException("Too many components");
        }

        if (gamma < 0.0 || gamma > 0.2) {
            throw new IllegalArgumentException("Invalid regularization factor gamma.");
        }

        int n = x.length;
        int k = components.length;

        double[][] posteriori = new double[k][n];

        // Log Likelihood
        double L = 0.0;

        // EM loop until convergence
        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            // Expectation step
            for (int i = 0; i < k; i++) {
                Component c = components[i];

                for (int j = 0; j < n; j++) {
                    posteriori[i][j] = c.priori * c.distribution.p(x[j]);
                }
            }

            // Normalize posteriori probability.
            for (int j = 0; j < n; j++) {
                double p = 0.0;

                for (int i = 0; i < k; i++) {
                    p += posteriori[i][j];
                }

                for (int i = 0; i < k; i++) {
                    posteriori[i][j] /= p;
                }

                // Adjust posterior probabilites based on Regularized EM algorithm.
                if (gamma > 0) {
                    for (int i = 0; i < k; i++) {
                        posteriori[i][j] *= (1 + gamma * MathEx.log2(posteriori[i][j]));
                        if (Double.isNaN(posteriori[i][j]) || posteriori[i][j] < 0.0) {
                            posteriori[i][j] = 0.0;
                        }
                    }
                }
            }

            // Maximization step
            double Z = 0.0;
            for (int i = 0; i < k; i++) {
                components[i] = ((MultivariateExponentialFamily) components[i].distribution).M(x, posteriori[i]);
                Z += components[i].priori;
            }

            for (int i = 0; i < k; i++) {
                components[i] = new Component(components[i].priori / Z, components[i].distribution);
            }

            double loglikelihood = 0.0;
            for (double[] xi : x) {
                double p = 0.0;
                for (Component c : components) {
                    p += c.priori * c.distribution.p(xi);
                }
                if (p > 0) loglikelihood += Math.log(p);
            }


            diff = loglikelihood - L;
            L = loglikelihood;

            if (iter % 10 == 0) {
                logger.info(String.format("The log-likelihood after %d iterations: %.4f", iter, L));
            }
        }

        return new MultivariateExponentialFamilyMixture(L, x.length, components);
    }
}
