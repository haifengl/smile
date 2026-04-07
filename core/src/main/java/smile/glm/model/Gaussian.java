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
package smile.glm.model;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * The response variable is of Gaussian (normal) distribution.
 * This family is used for ordinary linear regression and Gaussian GAMs.
 * The canonical link is the identity function, giving the standard
 * linear-Gaussian model.
 *
 * <p>The Gaussian family supports three link functions:
 * <ul>
 *   <li>{@link #identity()} – the canonical identity link {@code g(mu) = mu}.</li>
 *   <li>{@link #log()} – the log link {@code g(mu) = log(mu)}, useful when
 *       the mean must be positive.</li>
 *   <li>{@link #inverse()} – the inverse link {@code g(mu) = 1/mu}.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public interface Gaussian {

    /**
     * Identity link function (canonical link for Gaussian).
     * {@code g(mu) = mu},  {@code g^{-1}(eta) = eta}.
     *
     * @return the identity link model.
     */
    static Model identity() {
        return new Model() {
            @Override
            public String toString() {
                return "Gaussian(identity)";
            }

            @Override
            public double link(double mu) {
                return mu;
            }

            @Override
            public double invlink(double eta) {
                return eta;
            }

            @Override
            public double dlink(double mu) {
                return 1.0;
            }

            @Override
            public double variance(double mu) {
                return 1.0;
            }

            @Override
            public double mustart(double y) {
                return y;
            }

            @Override
            public double deviance(double[] y, double[] mu, double[] residuals) {
                return IntStream.range(0, y.length).mapToDouble(i -> {
                    double d = y[i] - mu[i];
                    residuals[i] = d; // deviance residual = raw residual for Gaussian
                    return d * d;
                }).sum();
            }

            @Override
            public double nullDeviance(double[] y, double mu) {
                return Arrays.stream(y).map(yi -> (yi - mu) * (yi - mu)).sum();
            }

            @Override
            public double logLikelihood(double[] y, double[] mu) {
                // Gaussian log-likelihood: -n/2 * log(2*pi*sigma^2) - 1/(2*sigma^2) * sum((y-mu)^2)
                // Under unit variance assumption (sigma^2 = 1), simplified form:
                int n = y.length;
                double sse = IntStream.range(0, n).mapToDouble(i -> {
                    double r = y[i] - mu[i];
                    return r * r;
                }).sum();
                // Estimate sigma^2 as sse/n for the log-likelihood
                double sigma2 = sse / n;
                if (sigma2 <= 0) sigma2 = Double.MIN_VALUE;
                return -0.5 * n * (Math.log(2.0 * Math.PI * sigma2) + 1.0);
            }
        };
    }

    /**
     * Log link function for Gaussian.
     * {@code g(mu) = log(mu)},  {@code g^{-1}(eta) = exp(eta)}.
     * Useful when the mean must be positive.
     *
     * @return the log link model.
     */
    static Model log() {
        return new Model() {
            @Override
            public String toString() {
                return "Gaussian(log)";
            }

            @Override
            public double link(double mu) {
                return Math.log(mu);
            }

            @Override
            public double invlink(double eta) {
                return Math.exp(eta);
            }

            @Override
            public double dlink(double mu) {
                return 1.0 / mu;
            }

            @Override
            public double variance(double mu) {
                return 1.0;
            }

            @Override
            public double mustart(double y) {
                return y > 0 ? y : 0.1;
            }

            @Override
            public double deviance(double[] y, double[] mu, double[] residuals) {
                return IntStream.range(0, y.length).mapToDouble(i -> {
                    double d = y[i] - mu[i];
                    residuals[i] = d;
                    return d * d;
                }).sum();
            }

            @Override
            public double nullDeviance(double[] y, double mu) {
                return Arrays.stream(y).map(yi -> (yi - mu) * (yi - mu)).sum();
            }

            @Override
            public double logLikelihood(double[] y, double[] mu) {
                int n = y.length;
                double sse = IntStream.range(0, n).mapToDouble(i -> {
                    double r = y[i] - mu[i];
                    return r * r;
                }).sum();
                double sigma2 = sse / n;
                if (sigma2 <= 0) sigma2 = Double.MIN_VALUE;
                return -0.5 * n * (Math.log(2.0 * Math.PI * sigma2) + 1.0);
            }
        };
    }

    /**
     * Inverse link function for Gaussian.
     * {@code g(mu) = 1/mu},  {@code g^{-1}(eta) = 1/eta}.
     *
     * @return the inverse link model.
     */
    static Model inverse() {
        return new Model() {
            @Override
            public String toString() {
                return "Gaussian(inverse)";
            }

            @Override
            public double link(double mu) {
                return 1.0 / mu;
            }

            @Override
            public double invlink(double eta) {
                return 1.0 / eta;
            }

            @Override
            public double dlink(double mu) {
                return -1.0 / (mu * mu);
            }

            @Override
            public double variance(double mu) {
                return 1.0;
            }

            @Override
            public double mustart(double y) {
                return y != 0 ? y : 0.1;
            }

            @Override
            public double deviance(double[] y, double[] mu, double[] residuals) {
                return IntStream.range(0, y.length).mapToDouble(i -> {
                    double d = y[i] - mu[i];
                    residuals[i] = d;
                    return d * d;
                }).sum();
            }

            @Override
            public double nullDeviance(double[] y, double mu) {
                return Arrays.stream(y).map(yi -> (yi - mu) * (yi - mu)).sum();
            }

            @Override
            public double logLikelihood(double[] y, double[] mu) {
                int n = y.length;
                double sse = IntStream.range(0, n).mapToDouble(i -> {
                    double r = y[i] - mu[i];
                    return r * r;
                }).sum();
                double sigma2 = sse / n;
                if (sigma2 <= 0) sigma2 = Double.MIN_VALUE;
                return -0.5 * n * (Math.log(2.0 * Math.PI * sigma2) + 1.0);
            }
        };
    }
}

