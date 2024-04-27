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

import java.io.Serial;
import java.util.Arrays;
import smile.math.MathEx;

/**
 * Kernel density estimation is a non-parametric way of estimating the
 * probability density function of a random variable. Kernel density estimation
 * is a fundamental data smoothing problem where inferences about the population
 * are made, based on a finite data sample. It is also known as the
 * Parzen window method.
 * 
 * @author Haifeng Li
 */
public class KernelDensity implements Distribution {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The samples to estimate the density function.
     */
    private final double[] x;
    /**
     * The kernel -- a symmetric but not necessarily positive function that
     * integrates to one. Here we just use Gaussian density function.
     */
    private final GaussianDistribution gaussian;
    /**
     * {@code h > 0} is a smoothing parameter called the bandwidth.
     */
    private final double h;
    /**
     * The mean value.
     */
    private final double mean;
    /**
     * The standard deviation.
     */
    private final double sd;
    /**
     * The variance.
     */
    private final double variance;

    /**
     * Constructor. The bandwidth of kernel will be estimated by the rule of thumb.
     * @param x the samples to estimate the density function.
     */
    public KernelDensity(double[] x) {
        this.x = x;
        this.mean = MathEx.mean(x);
        this.variance = MathEx.var(x);
        this.sd = Math.sqrt(variance);
        if (sd == 0.0) {
            throw new IllegalArgumentException("Samples has no variance.");
        }

        Arrays.sort(x);

        int n = x.length;
        double iqr = x[n*3/4] - x[n/4];
        // safeguard iqr == 0
        double spread = iqr > 0 ? Math.min(sd, iqr/1.34) : sd;
        h = 1.06 * spread * Math.pow(x.length, -0.2);
        gaussian = new GaussianDistribution(0, h);
    }

    /**
     * Constructor.
     * @param x the samples to estimate the density function.
     * @param h a bandwidth parameter for smoothing.
     */
    public KernelDensity(double[] x, double h) {
        if (h <= 0) {
            throw new IllegalArgumentException("Invalid bandwidth: " + h);
        }

        this.x = x;
        this.h = h;
        this.mean = MathEx.mean(x);
        this.variance = MathEx.var(x);
        this.sd = Math.sqrt(variance);
        gaussian = new GaussianDistribution(0, h);

        Arrays.sort(x);
    }

    /**
     * Returns the bandwidth of kernel.
     * @return the bandwidth of kernel
     */
    public double bandwidth() {
        return h;
    }

    @Override
    public int length() {
        return 0;
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
    public double sd() {
        return sd;
    }

    /**
     * Shannon's entropy. Not supported.
     */
    @Override
    public double entropy() {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * Random number generator. Not supported.
     */
    @Override
    public double rand() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public double p(double x) {
        int start = Arrays.binarySearch(this.x, x-5*h);
        if (start < 0) {
            start = -start - 1;
        }

        int end = Arrays.binarySearch(this.x, x+5*h);
        if (end < 0) {
            end = -end - 1;
        }

        double p = 0.0;
        for (int i = start; i < end; i++) {
            p += gaussian.p(this.x[i] - x);
        }

        return p / this.x.length;
    }

    @Override
    public double logp(double x) {
        return Math.log(p(x));
    }

    /**
     * Cumulative distribution function. Not supported.
     */
    @Override
    public double cdf(double x) {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * Inverse of CDF. Not supported.
     */
    @Override
    public double quantile(double p) {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * The likelihood of the samples. Not supported.
     */
    @Override
    public double likelihood(double[] x) {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * The log likelihood of the samples. Not supported.
     */
    @Override
    public double logLikelihood(double[] x) {
        throw new UnsupportedOperationException("Not supported.");
    }
}
