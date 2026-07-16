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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.stat.distribution;

import java.io.Serial;
import smile.math.MathEx;

/**
 * The finite mixture of distributions from multivariate exponential family.
 * The EM algorithm can be used to learn the mixture model from data.
 *
 * @author Haifeng Li
 */
public class MultivariateExponentialFamilyMixture extends MultivariateMixture {
    @Serial
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
            if (!(component.distribution() instanceof MultivariateExponentialFamily)) {
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
     * @return the distribution.
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
     * @return the distribution.
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
            // Expectation step — log-sum-exp for numerical stability
            for (int j = 0; j < n; j++) {
                double maxLog = Double.NEGATIVE_INFINITY;
                double[] logPost = new double[k];
                for (int i = 0; i < k; i++) {
                    Component c = components[i];
                    logPost[i] = Math.log(c.priori()) + c.distribution().logp(x[j]);
                    if (logPost[i] > maxLog) maxLog = logPost[i];
                }
                double sumExp = 0.0;
                for (int i = 0; i < k; i++) sumExp += Math.exp(logPost[i] - maxLog);
                double logZ = maxLog + Math.log(sumExp);
                for (int i = 0; i < k; i++) {
                    posteriori[i][j] = Math.exp(logPost[i] - logZ);
                }

                // Adjust posterior probabilities based on Regularized EM algorithm.
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
                components[i] = ((MultivariateExponentialFamily) components[i].distribution()).M(x, posteriori[i]);
                Z += components[i].priori();
            }

            for (int i = 0; i < k; i++) {
                components[i] = new Component(components[i].priori() / Z, components[i].distribution());
            }

            double loglikelihood = 0.0;
            for (double[] xi : x) {
                double maxLog = Double.NEGATIVE_INFINITY;
                double[] logTerms = new double[k];
                for (int i = 0; i < k; i++) {
                    logTerms[i] = Math.log(components[i].priori()) + components[i].distribution().logp(xi);
                    if (logTerms[i] > maxLog) maxLog = logTerms[i];
                }
                double sumExp = 0.0;
                for (int i = 0; i < k; i++) sumExp += Math.exp(logTerms[i] - maxLog);
                loglikelihood += maxLog + Math.log(sumExp);
            }


            diff = loglikelihood - L;
            L = loglikelihood;

            if (iter % 10 == 0) {
                logger.info("The log-likelihood after {} iterations: {}", iter, L);
            }
        }

        return new MultivariateExponentialFamilyMixture(L, x.length, components);
    }
}
