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

import smile.math.special.Beta;
import smile.math.special.Gamma;
import smile.math.Math;

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
public class TDistribution extends AbstractDistribution {
    private int nu;
    private double entropy;
    private double np;
    private double fac;

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
    public int npara() {
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
    public double var() {
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
     */
    public double cdf2tiled(double x) {
        if (x < 0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        return 1.0 - Beta.regularizedIncompleteBetaFunction(0.5 * nu, 0.5, nu / (nu + x * x));
    }

    /**
     * Two-tailed quantile.
     */
    public double quantile2tiled(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        double x = Beta.inverseRegularizedIncompleteBetaFunction(0.5 * nu, 0.5, 1.0 - p);
        return Math.sqrt(nu * (1.0 - x) / x);
    }
}
