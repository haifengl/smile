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

import java.io.Serializable;
import smile.math.matrix.Matrix;

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
     * The "length" is in the sense of the minimum description
     * length principle.
     * @return the number of parameters of the distribution.
     */
    int length();

    /**
     * Shannon entropy of the distribution.
     * @return Shannon entropy
     */
    double entropy();

    /**
     * The mean vector of distribution.
     * @return the mean vector.
     */
    double[] mean();

    /**
     * The covariance matrix of distribution.
     * @return the covariance matrix.
     */
    Matrix cov();
    
    /**
     * The probability density function for continuous distribution
     * or probability mass function for discrete distribution at x.
     * @param x a real vector.
     * @return the desnity.
     */
    double p(double[] x);

    /**
     * The density at x in log scale, which may prevents the underflow problem.
     * @param x a real vector.
     * @return the log density.
     */
    double logp(double[] x);

    /**
     * Cumulative distribution function. That is the probability to the left of x.
     * @param x a real vector.
     * @return the probability.
     */
    double cdf(double[] x);

    /**
     * The likelihood of the sample set following this distribution.
     *
     * @param x a set of samples.
     * @return the likelihood.
     */
    default double likelihood(double[][] x) {
        return Math.exp(logLikelihood(x));
    }

    /**
     * The log likelihood of the sample set following this distribution.
     *
     * @param x a set of samples.
     * @return the log likelihood.
     */
    default double logLikelihood(double[][] x) {
        double L = 0.0;

        for (double[] xi : x)
            L += logp(xi);

        return L;
    }
}
