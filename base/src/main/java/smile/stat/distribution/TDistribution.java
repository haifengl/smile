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

import smile.math.special.Beta;
import smile.math.special.Gamma;

import java.io.Serial;

/**
 * Student's t-distribution (or simply the t-distribution) is a probability
 * distribution that arises in the problem of estimating the mean of a
 * normally distributed population when the sample size is small.
 * Student's t-distribution arises when (as in nearly all practical statistical
 * work) the population standard deviation is unknown and has to be estimated
 * from the data. It is
 * the basis of the popular Student's t-tests for the statistical significance
 * of the difference between two sample means, and for confidence intervals
 * for the difference between two population means. The Student's
 * t-distribution is a special case of the generalised hyperbolic distribution.
 *
 * @author Haifeng Li
 */
public class TDistribution implements Distribution {
    @Serial
    private static final long serialVersionUID = 2L;

    /** The degree of freedom. */
    public  final int nu;
    /** Shannon entropy. */
    private final double entropy;
    /** The constant factor in PDF. */
    private final double np;
    /** The constant factor in PDF. */
    private final double fac;

    /**
     * Constructor.
     * @param nu degree of freedom.
     */
    public TDistribution(int nu) {
        if (nu < 1) {
            throw new IllegalArgumentException("Invalid nu = " + nu);
        }

        this.nu = nu;

        entropy = 0.5 * (nu + 1) * (Gamma.digamma((nu + 1) / 2.0) - Gamma.digamma(nu / 2.0)) + Math.log(Math.sqrt(nu) * Beta.beta(nu / 2.0, 0.5));
        np = 0.5 * (nu + 1.0);
        fac = Gamma.lgamma(np) - Gamma.lgamma(0.5 * nu);
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public double mean() {
        if (nu == 1) {
            throw new UnsupportedOperationException("Mean is undefined for T distribution with nu = 1");
        }

        return 0.0;
    }

    @Override
    public double variance() {
        return nu / (nu - 2.0);
    }

    @Override
    public double sd() {
        return Math.sqrt(nu / (nu - 2.0));
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("t-distribution(%d)", nu);
    }

    @Override
    public double rand() {
        return inverseTransformSampling();
    }

    @Override
    public double p(double x) {
        return Math.exp(-np * Math.log(1.0 + x * x / nu) + fac) / Math.sqrt(Math.PI * nu);
    }

    @Override
    public double logp(double x) {
        return -np * Math.log(1.0 + x * x / nu) + fac - Math.log(Math.sqrt(Math.PI * nu));
    }

    @Override
    public double cdf(double x) {
        double p = 0.5 * Beta.regularizedIncompleteBetaFunction(0.5 * nu, 0.5, nu / (nu + x * x));

        if (x >= 0) {
            return 1.0 - p;
        } else {
            return p;
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        double x = Beta.inverseRegularizedIncompleteBetaFunction(0.5 * nu, 0.5, 2.0 * Math.min(p, 1.0 - p));
        x = Math.sqrt(nu * (1.0 - x) / x);
        return p >= 0.5 ? x : -x;
    }

    /**
     * Two-tailed cdf.
     * @param x a real number.
     * @return the two-tailed cdf.
     */
    public double cdf2tailed(double x) {
        if (x < 0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        return 1.0 - Beta.regularizedIncompleteBetaFunction(0.5 * nu, 0.5, nu / (nu + x * x));
    }

    /**
     * Two-tailed quantile.
     * @param p a probability.
     * @return the two-tailed quantile.
     */
    public double quantile2tailed(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        double x = Beta.inverseRegularizedIncompleteBetaFunction(0.5 * nu, 0.5, 1.0 - p);
        return Math.sqrt(nu * (1.0 - x) / x);
    }
}
