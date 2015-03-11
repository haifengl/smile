/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.stat.distribution;

/**
 * Probability distribution of multivariate random variable.
 * 
 * @see Distribution
 *
 * @author Haifeng Li
 */
public interface MultivariateDistribution {
    /**
     * The number of parameters of the distribution.
     */
    public int npara();

    /**
     * Shannon entropy of the distribution.
     */
    public double entropy();

    /**
     * The mean vector of distribution.
     */
    public double[] mean();

    /**
     * The covariance matrix of distribution.
     */
    public double[][] cov();
    
    /**
     * The probability density function for continuous distribution
     * or probability mass function for discrete distribution at x.
     */
    public double p(double[] x);

    /**
     * The density at x in log scale, which may prevents the underflow problem.
     */
    public double logp(double[] x);

    /**
     * Cumulative distribution function. That is the probability to the left of x.
     */
    public double cdf(double[] x);

    /**
     * The likelihood of the sample set following this distribution.
     *
     * @param x sample set. Each row is a sample.
     */
    public double likelihood(double[][] x);

    /**
     * The log likelihood of the sample set following this distribution.
     *
     * @param x sample set. Each row is a sample.
     */
    public double logLikelihood(double[][] x);
}
