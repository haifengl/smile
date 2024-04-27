/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.stat.distribution;

import smile.math.MathEx;
import smile.math.special.Gamma;

import java.io.Serial;

/**
 * The Weibull distribution is one of the most widely used lifetime distributions
 * in reliability engineering. It is a versatile distribution that can take on
 * the characteristics of other types of distributions, based on the value of
 * the shape parameter. The distribution has two parameters: {@code k > 0}
 * is the shape parameter and &lambda; {@code > 0} is the scale parameter
 * of the distribution. The probability density function is
 * f(x;&lambda;,k) = k/&lambda; (x/&lambda;)<sup>k-1</sup>e<sup>-(x/&lambda;)^k</sup>
 * for {@code x >= 0}.
 * <p>
 * The Weibull distribution is often used in the field of life data analysis
 * due to its flexibility - it can mimic the behavior of other statistical
 * distributions such as the normal and the exponential. If the failure rate
 * decreases over time, then {@code k < 1}. If the failure rate is
 * constant over time, then {@code k = 1}. If the failure rate increases
 * over time, then {@code k > 1}.
 * <p>
 * An understanding of the failure rate may provide insight as to what is
 * causing the failures:
 * <ul>
 * <li> A decreasing failure rate would suggest "infant mortality". That is,
 * defective items fail early and the failure rate decreases over time as
 * they fall out of the population.
 * <li> A constant failure rate suggests that items are failing from random
 * events.
 * <li> An increasing failure rate suggests "wear out" - parts are more likely
 * to fail as time goes on.
 * </ul>
 *
 * Under certain parameterizations, the Weibull distribution reduces to several
 * other familiar distributions:
 * <ul>
 * <li> When <code>k = 1</code>, it is the exponential distribution.
 * <li> When <code>k = 2</code>, it becomes equivalent to the Rayleigh
 * distribution, which models the modulus of a two-dimensional uncorrelated
 * bivariate normal vector.
 * <li> When <code>k = 3.4</code>, it appears similar to the normal distribution.
 * <li> As k goes to infinity, the Weibull distribution asymptotically
 * approaches the Dirac delta function.
 * </ul>
 *
 * @author Haifeng Li
 */
public class WeibullDistribution implements Distribution {
    @Serial
    private static final long serialVersionUID = 2L;

    /** The shape parameter. */
    public  final double k;
    /** The scale parameter. */
    public  final double lambda;
    /** The mean value. */
    private final double mean;
    /** The variance. */
    private final double variance;
    /** Shannon entropy. */
    private final double entropy;

    /**
     * Constructor. The default scale parameter is 1.0.
     * @param k the shape parameter.
     */
    public WeibullDistribution(double k) {
        this(k, 1.0);
    }

    /**
     * Constructor.
     * @param k the shape parameter.
     * @param lambda the scale parameter.
     */
    public WeibullDistribution(double k, double lambda) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid shape: " + k);
        }

        if (lambda <= 0) {
            throw new IllegalArgumentException("Invalid scale: " + lambda);
        }

        this.k = k;
        this.lambda = lambda;

        mean = lambda * Gamma.gamma(1 + 1 / k);
        variance = lambda * lambda * Gamma.gamma(1 + 2 / k) - mean * mean;
        entropy = 0.5772156649015328606 * (1 - 1 / k) + Math.log(lambda / k) + 1;
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
        return variance;
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Weibull Distribution(%.4f, %.4f)", k, lambda);
    }

    @Override
    public double rand() {
        double r = MathEx.random();
        return lambda * Math.pow(-Math.log(1 - r), 1 / k);
    }

    @Override
    public double p(double x) {
        if (x <= 0) {
            return 0.0;
        } else {
            return (k / lambda) * Math.pow(x / lambda, k - 1) * Math.exp(-Math.pow(x / lambda, k));
        }
    }

    @Override
    public double logp(double x) {
        if (x <= 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return Math.log(k / lambda) + (k - 1) * Math.log(x / lambda) - Math.pow(x / lambda, k);
        }
    }

    @Override
    public double cdf(double x) {
        if (x <= 0) {
            return 0.0;
        } else {
            return 1 - Math.exp(-Math.pow(x / lambda, k));
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        return lambda * Math.pow(-Math.log(1 - p), 1 / k);
    }
}

