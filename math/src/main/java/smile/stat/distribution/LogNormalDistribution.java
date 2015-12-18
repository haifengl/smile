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

import smile.math.special.Erf;
import smile.math.Math;

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
    private double mu;
    private double sigma;
    private double mean;
    private double var;
    private double entropy;
    private GaussianDistribution gaussian;

    /**
     * Constructor.
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
     * Constructor. Parameter will be estimated from the data by MLE.
     */
    public LogNormalDistribution(double[] data) {
        double[] x = new double[data.length];
        for (int i = 0; i < x.length; i++) {
            if (data[i] <= 0.0) {
                throw new IllegalArgumentException("Invalid input data: " + data[i]);
            }

            x[i] = Math.log(data[i]);
        }

        this.mu = Math.mean(x);
        this.sigma = Math.sd(x);

        mean = Math.exp(mu + sigma * sigma / 2);
        var = (Math.exp(mu * mu) - 1) * Math.exp(2 * mu + sigma * sigma);
        entropy = 0.5 + 0.5 * Math.log(2 * Math.PI * sigma * sigma) + mu;
    }

    /**
     * Returns the parameter mu, which is the mean of normal distribution log(X).
     */
    public double getMu() {
        return mu;
    }

    /**
     * Returns the parameter sigma, which is the standard deviation of normal distribution log(X).
     */
    public double getSigma() {
        return sigma;
    }

    @Override
    public int npara() {
        return 2;
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
        return Math.sqrt(var);
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
