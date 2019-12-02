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
import smile.math.special.Erf;

/**
 * A log-normal distribution is a probability distribution of a random variable
 * whose logarithm is normally distributed. The log-normal distribution is the single-tailed probability distribution
 * of any random variable whose logarithm is normally distributed. If X is a
 * random variable with a normal distribution, then Y = exp(X) has a log-normal
 * distribution; likewise, if Y is log-normally distributed, then log(Y) is
 * normally distributed.
 * A variable might be modeled as log-normal if it can be thought of as
 * the multiplicative product of many independent random variables each of
 * which is positive.
 *
 * @author Haifeng Li
 */
public class LogNormalDistribution extends AbstractDistribution {
    private static final long serialVersionUID = 2L;

    /** The mean of normal distribution. */
    public final double mu;
    /** The standard deviation of normal distribution. */
    public final double sigma;
    private double mean;
    private double var;
    private double entropy;
    private GaussianDistribution gaussian;

    /**
     * Constructor.
     * @param mu the mean of normal distribution.
     * @param sigma the standard deviation of normal distribution.
     */
    public LogNormalDistribution(double mu, double sigma) {
        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid sigma: " + sigma);
        }
        this.mu = mu;
        this.sigma = sigma;

        mean = Math.exp(mu + sigma * sigma / 2);
        var = (Math.exp(mu * mu) - 1) * Math.exp(2 * mu + sigma * sigma);
        entropy = 0.5 + 0.5 * Math.log(2 * Math.PI * sigma * sigma) + mu;
    }

    /**
     * Estimates the distribution parameters by MLE.
     */
    public static LogNormalDistribution fit(double[] data) {
        double[] x = new double[data.length];
        for (int i = 0; i < x.length; i++) {
            if (data[i] <= 0.0) {
                throw new IllegalArgumentException("Invalid input data: " + data[i]);
            }

            x[i] = Math.log(data[i]);
        }

        double mu = MathEx.mean(x);
        double sigma = MathEx.sd(x);
        return new LogNormalDistribution(mu, sigma);
    }

    @Override
    public int length() {
        return 2;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double variance() {
        return var;
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Log Normal Distribution(%.4f, %.4f)", mu, sigma);
    }

    @Override
    public double rand() {
        if (gaussian == null) {
            gaussian = new GaussianDistribution(mu, sigma);
        }

        return Math.exp(gaussian.rand());
    }

    @Override
    public double p(double x) {
        if (x < 0.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        if (x == 0.0) {
            return 0.0;
        }

        double t = (Math.log(x) - mu) / sigma;
        return (0.398942280401432678 / (sigma * x)) * Math.exp(-0.5 * t * t);
    }

    @Override
    public double logp(double x) {
        return Math.log(p(x));
    }

    @Override
    public double cdf(double x) {
        if (x < 0.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        if (x == 0.0) {
            return 0.;
        }

        return 0.5 * Erf.erfc(-0.707106781186547524 * (Math.log(x) - mu) / sigma);
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        return Math.exp(-1.41421356237309505 * sigma * Erf.inverfc(2.0 * p) + mu);
    }
}
