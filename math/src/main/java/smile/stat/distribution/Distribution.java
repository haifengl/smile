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
     */
    int npara();

    /**
     * The mean of distribution.
     */
    double mean();
    
    /**
     * The variance of distribution.
     */
    double var();
    
    /**
     * The standard deviation of distribution.
     */
    double sd();

    /**
     * Shannon entropy of the distribution.
     */
    double entropy();

    /**
     * Generates a random number following this distribution.
     */
    double rand();
    
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
    double likelihood(double[] x);
    
    /**
     * The log likelihood of the sample set following this distribution.
     */
    double logLikelihood(double[] x);
}
