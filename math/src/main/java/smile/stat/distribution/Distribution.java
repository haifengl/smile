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

import java.io.Serializable;

/**
 * Probability distribution of univariate random variable. A probability
 * distribution identifies either the probability of each value
 * of a random variable (when the variable is discrete), or
 * the probability of the value falling within a particular interval (when
 * the variable is continuous). When the random variable takes values in the
 * set of real numbers, the probability distribution is completely described
 * by the cumulative distribution function, whose value at each real x is the
 * probability that the random variable is smaller than or equal to x.
 *
 * @see MultivariateDistribution
 *
 * @author Haifeng Li
 */
public interface Distribution extends Serializable {

    /**
     * The number of parameters of the distribution.
     * The "length" is in the sense of the minimum description
     * length principle.
     */
    int length();

    /**
     * The mean of distribution.
     */
    double mean();
    
    /**
     * The variance of distribution.
     */
    double variance();
    
    /**
     * The standard deviation of distribution.
     */
    default double sd() {
        return Math.sqrt(variance());
    }

    /**
     * Shannon entropy of the distribution.
     */
    double entropy();

    /**
     * Generates a random number following this distribution.
     */
    double rand();

    /**
     * Generates a set of random numbers following this distribution.
     */
    default double[] rand(int n) {
        double[] data = new double[n];
        for (int i = 0; i < n; i++) {
            data[i] = rand();
        }
        return data;
    }

    /**
     * The probability density function for continuous distribution
     * or probability mass function for discrete distribution at x.
     */
    double p(double x);

    /**
     * The density at x in log scale, which may prevents the underflow problem.
     */
    double logp(double x);

    /**
     * Cumulative distribution function. That is the probability to the left of x.
     */
    double cdf(double x);

    /**
     * The quantile, the probability to the left of quantile is p. It is
     * actually the inverse of cdf.
     */
    double quantile(double p);

    /**
     * The likelihood of the sample set following this distribution.
     */
    default double likelihood(double[] x) {
        return Math.exp(logLikelihood(x));
    }
    
    /**
     * The log likelihood of the sample set following this distribution.
     */
    default double logLikelihood(double[] x) {
        double L = 0.0;

        for (double xi : x)
            L += logp(xi);

        return L;
    }
}
