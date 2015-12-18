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

import java.util.Arrays;
import smile.math.Math;

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

    /**
     * The samples to estimate the density function.
     */
    private double[] x;
    /**
     * The kernel -- a symmetric but not necessarily positive function that
     * integrates to one. Here we just Gaussian density function.
     */
    private GaussianDistribution gaussian;
    /**
     * h > 0 is a smoothing parameter called the bandwidth.
     */
    private double h;
    /**
     * The mean value.
     */
    private double mean;
    /**
     * The standard deviation.
     */
    private double sd;
    /**
     * The variance.
     */
    private double var;

    /**
     * Constructor. The bandwidth of kernel will be estimated by the rule of thumb.
     * @param x the samples to estimate the density function.
     */
    public KernelDensity(double[] x) {
        this.x = x;
        this.mean = Math.mean(x);
        this.var = Math.var(x);
        this.sd = Math.sqrt(var);

        Arrays.sort(x);

        int n = x.length;
        double iqr = x[n*3/4] - x[n/4];
        h = 1.06 * Math.min(sd, iqr/1.34) / Math.pow(x.length, 0.2);
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
        this.mean = Math.mean(x);
        this.var = Math.var(x);
        this.sd = Math.sqrt(var);
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
    public int npara() {
        return 0;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double var() {
        return var;
    }

    @Override
    public double sd() {
        return sd;
    }

    /**
     * Shannon entropy. Not supported.
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
