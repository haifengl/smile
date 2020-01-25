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
import smile.math.special.Gamma;

/**
 * The Gamma distribution is a continuous probability distributions with
 * a scale parameter &theta; and a shape parameter k. If k is an
 * integer then the distribution represents the sum of k independent
 * exponentially distributed random variables, each of which has a rate
 * parameter of &theta;). The gamma distribution is frequently a probability
 * model for waiting times; for instance, the waiting time until death in life testing.
 * The probability density function is
 * f(x; k,&theta;) = x<sup>k-1</sup>e<sup>-x/&theta;</sup> / (&theta;<sup>k</sup>&Gamma;(k))
 * for x &gt; 0 and k, &theta; &gt; 0.
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
public class GammaDistribution extends AbstractDistribution implements ExponentialFamily {
    private static final long serialVersionUID = 2L;

    /** The scale parameter. */
    public final double theta;
    /** The shape parameter. */
    public final double k;
    private double logTheta;
    private double thetaGammaK;
    private double logGammaK;
    private double entropy;

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
     */
    public static GammaDistribution fit(double[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] <= 0) {
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

        double shape = (3 - s + Math.sqrt((MathEx.sqr(s - 3) + 24 * s))) / (12 * s);
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
     * Only support shape parameter k of integer.
     */
    @Override
    public double rand() {
        if (k - Math.floor(k) != 0.0) {
            throw new IllegalArgumentException("Gamma random number generator support only integer shape parameter.");
        }

        double r = 0.0;

        for (int i = 0; i < k; i++) {
            r += Math.log(MathEx.random());
        }

        r *= -theta;

        return r;
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

