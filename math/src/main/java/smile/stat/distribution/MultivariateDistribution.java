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
 * Probability distribution of multivariate random variable.
 * 
 * @see Distribution
 *
 * @author Haifeng Li
 */
public interface MultivariateDistribution extends Serializable {
    /**
     * The number of parameters of the distribution.
     */
    int npara();

    /**
     * Shannon entropy of the distribution.
     */
    double entropy();

    /**
     * The mean vector of distribution.
     */
    double[] mean();

    /**
     * The covariance matrix of distribution.
     */
    double[][] cov();
    
    /**
     * The probability density function for continuous distribution
     * or probability mass function for discrete distribution at x.
     */
    double p(double[] x);

    /**
     * The density at x in log scale, which may prevents the underflow problem.
     */
    double logp(double[] x);

    /**
     * Cumulative distribution function. That is the probability to the left of x.
     */
    double cdf(double[] x);

    /**
     * The likelihood of the sample set following this distribution.
     *
     * @param x sample set. Each row is a sample.
     */
    double likelihood(double[][] x);

    /**
     * The log likelihood of the sample set following this distribution.
     *
     * @param x sample set. Each row is a sample.
     */
    double logLikelihood(double[][] x);
}
