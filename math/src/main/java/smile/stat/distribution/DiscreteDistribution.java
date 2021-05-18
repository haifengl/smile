/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.stat.distribution;

import smile.math.MathEx;

/**
 * Univariate discrete distributions. Basically, this class adds common
 * distribution methods that accept integer argument beside float argument.
 * A quantile function is provided based on bisection searching.
 * Likelihood and log likelihood functions are also implemented here.
 *
 * @author Haifeng Li
 */
public abstract class DiscreteDistribution extends AbstractDistribution {
    /**
     * Generates an integer random number following this discrete distribution.
     * @return an integer random number.
     */
    public int randi() {
        return (int) rand();
    }

    /**
     * Generates a set of integer random numbers following this discrete distribution.
     * @param n the number of random numbers to generate.
     * @return a set of integer random numbers.
     */
    public int[] randi(int n) {
        int[] data = new int[n];
        for (int i = 0; i < n; i++) {
            data[i] = randi();
        }
        return data;
    }

    /**
     * The probability mass function.
     * @param x a real value.
     * @return the probability.
     */
    public abstract double p(int x);

    @Override
    public double p(double x) {
        if (!MathEx.isInt(x)) {
            throw new IllegalArgumentException("x is not an integer");
        }

        return p((int)x);
    }

    /**
     * The probability mass function in log scale.
     * @param x a real value.
     * @return the log probability.
     */
    public abstract double logp(int x);
    
    @Override
    public double logp(double x) {
        if (!MathEx.isInt(x)) {
            throw new IllegalArgumentException("x is not an integer");
        }

        return logp((int)x);
    }
    
    /**
     * The likelihood given a sample set following the distribution.
     * @param x a set of samples.
     * @return the likelihood.
     */
    public double likelihood(int[] x) {
        return Math.exp(logLikelihood(x));        
    }    
    
    /**
     * The likelihood given a sample set following the distribution.
     * @param x a set of samples.
     * @return the log likelihood.
     */
    public double logLikelihood(int[] x) {
        double L = 0.0;
        
        for (double xi : x)
            L += logp(xi);
        
        return L;        
    }

    /**
     * Inversion of cdf by bisection numeric root finding of
     * <code>cdf(x) = p</code> for discrete distribution.
     * @param p the probability.
     * @param xmin the lower bound of search range.
     * @param xmax the upper bound of search range.
     * @return an integer {@code n} such that
     *         {@code P(<n) <= p <= P(<n+1)}.
     */
    protected double quantile(double p, int xmin, int xmax) {
        while (xmax - xmin > 1) {
            int xmed = (xmax + xmin) / 2;
            if (cdf(xmed) > p) {
                xmax = xmed;
            } else {
                xmin = xmed;
            }
        }

        if (cdf(xmin) >= p)
            return xmin;
        else
            return xmax;
    }
}
