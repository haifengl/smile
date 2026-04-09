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

import smile.math.MathEx;
import smile.math.special.Gamma;

import java.io.Serial;

/**
 * The Gamma distribution is a continuous probability distributions with
 * a scale parameter &theta; and a shape parameter k. If k is an
 * integer then the distribution represents the sum of k independent
 * exponentially distributed random variables, each of which has a rate
 * parameter of &theta;). The gamma distribution is frequently a probability
 * model for waiting times; for instance, the waiting time until death in life testing.
 * The probability density function is
 * f(x; k,&theta;) = x<sup>k-1</sup>e<sup>-x/&theta;</sup> / (&theta;<sup>k</sup>&Gamma;(k))
 * for {@code x > 0} and k, &theta; {@code > 0}.
 * <ul>
 * <li> If X &sim; &Gamma;(k=1, &theta;=1/&lambda;), then X has an exponential
 * distribution with rate parameter &lambda;.
 * <li> If X &sim; &Gamma;(k=&nu;/2, &theta;=2), then X is identical to
 * &Chi;<sup>2</sup>(&nu;), the chi-square distribution with &nu; degrees of
 * freedom. Conversely, if Q &sim; &Chi;<sup>2</sup>(&nu;), and c is a positive
 * constant, then c &sdot; Q &sim; &Gamma;(k=&nu;/2, &theta;=2c).
 * <li> If k is an integer, the gamma distribution is an Erlang distribution
 * and is the probability distribution of the waiting time until the k-th
 * "arrival" in a one-dimensional Poisson process with intensity 1/&theta;.
 * <li> If X<sup>2</sup> &sim; &Gamma;(3/2, 2a<sup>2</sup>), then X has a
 * Maxwell-Boltzmann distribution with parameter a.
 * </ul>
 * In Bayesian inference, the gamma distribution is the conjugate prior to
 * many likelihood distributions: the Poisson, exponential, normal
 * (with known mean), Pareto, gamma with known shape, and inverse gamma with
 * known shape parameter.
 *
 * @author Haifeng Li
 */
public class GammaDistribution implements ExponentialFamily {
    @Serial
    private static final long serialVersionUID = 2L;

    /** The scale parameter. */
    public final double theta;
    /** The shape parameter. */
    public final double k;
    /** log(theta) */
    private final double logTheta;
    /** theta * gamma(k) */
    private final double thetaGammaK;
    /** log(theta * gamma(k)) */
    private final double logGammaK;
    /** Shannon entropy. */
    private final double entropy;

    /**
     * Constructor.
     * @param shape the shape parameter.
     * @param scale the scale parameter.
     */
    public GammaDistribution(double shape, double scale) {
        if (shape <= 0) {
            throw new IllegalArgumentException("Invalid shape: " + shape);
        }

        if (scale <= 0) {
            throw new IllegalArgumentException("Invalid scale: " + scale);
        }

        theta = scale;
        k = shape;

        logTheta = Math.log(theta);
        thetaGammaK = theta * Gamma.gamma(k);
        logGammaK = Gamma.lgamma(k);
        entropy = k + Math.log(theta) + Gamma.lgamma(k) + (1 - k) * Gamma.digamma(k);
    }

    /**
     * Estimates the distribution parameters by (approximate) MLE.
     * @param data the training data.
     * @return the distribution.
     */
    public static GammaDistribution fit(double[] data) {
        for (double datum : data) {
            if (datum <= 0) {
                throw new IllegalArgumentException("Samples contain non-positive values.");
            }
        }

        double mu = 0.0;
        double s = 0.0;
        for (double x : data) {
            mu += x;
            s += Math.log(x);
        }

        mu /= data.length;
        s = Math.log(mu) - s / data.length;

        double shape = (3 - s + Math.sqrt((MathEx.pow2(s - 3) + 24 * s))) / (12 * s);
        double scale = mu / shape;
        return new GammaDistribution(shape, scale);
    }

    @Override
    public int length() {
        return 2;
    }

    @Override
    public double mean() {
        return k * theta;
    }

    @Override
    public double variance() {
        return k * theta * theta;
    }

    @Override
    public double sd() {
        return Math.sqrt(k) * theta;
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Gamma Distribution(%.4f, %.4f)", theta, k);
    }

    /**
     * Generates a random sample using the Marsaglia-Tsang (2000) method,
     * which works for any shape parameter k {@code > 0}.
     * <p>
     * Marsaglia, G., and Tsang, W. W. (2000). A Simple Method for
     * Generating Gamma Variables. ACM Transactions on Mathematical
     * Software, 26(3), 363–372.
     */
    @Override
    public double rand() {
        // For k >= 1 use Marsaglia-Tsang directly.
        // For 0 < k < 1, generate Gamma(k+1,...) and multiply by U^(1/k).
        if (k < 1.0) {
            double u;
            do {
                u = MathEx.random();
            } while (u == 0.0);
            return marsagliaTsang(k + 1.0) * Math.pow(u, 1.0 / k) * theta;
        }
        return marsagliaTsang(k) * theta;
    }

    /**
     * Marsaglia-Tsang algorithm for shape >= 1, scale = 1.
     */
    private static double marsagliaTsang(double shape) {
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        GaussianDistribution gaussian = GaussianDistribution.getInstance();
        for (;;) {
            double x, v;
            do {
                x = gaussian.rand();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            double u = MathEx.random();
            double x2 = x * x;
            if (u < 1.0 - 0.0331 * x2 * x2) {
                return d * v;
            }
            if (Math.log(u) < 0.5 * x2 + d * (1.0 - v + Math.log(v))) {
                return d * v;
            }
        }
    }

    @Override
    public double p(double x) {
        if (x < 0) {
            return 0.0;
        } else {
            return Math.pow(x / theta, k - 1) * Math.exp(-x / theta) / thetaGammaK;
        }
    }

    @Override
    public double logp(double x) {
        if (x < 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return (k - 1) * Math.log(x) - x / theta - k * logTheta - logGammaK;
        }
    }

    @Override
    public double cdf(double x) {
        if (x < 0) {
            return 0.0;
        } else {
            return Gamma.regularizedIncompleteGamma(k, x / theta);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        return Gamma.inverseRegularizedIncompleteGamma(k, p) * theta;
    }

    @Override
    public Mixture.Component M(double[] x, double[] posteriori) {
        double alpha = 0.0;
        double mean = 0.0;
        double variance = 0.0;

        for (int i = 0; i < x.length; i++) {
            alpha += posteriori[i];
            mean += x[i] * posteriori[i];
        }

        mean /= alpha;

        for (int i = 0; i < x.length; i++) {
            double d = x[i] - mean;
            variance += d * d * posteriori[i];
        }

        variance /= alpha;

        return new Mixture.Component(alpha, new GammaDistribution(mean * mean / variance, variance / mean));
    }
}

