/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.stat.distribution;

/**
 * This is the base class of multivariate distributions. Likelihood and
 * log likelihood functions are implemented here.
 *
 * @author Haifeng Li
 */
public abstract class AbstractMultivariateDistribution implements MultivariateDistribution {

    /**
     * The likelihood given a sample set following the distribution.
     */
    @Override
    public double likelihood(double[][] x) {
        return Math.exp(logLikelihood(x));
    }

    /**
     * The likelihood given a sample set following the distribution.
     */
    @Override
    public double logLikelihood(double[][] x) {
        double L = 0.0;

        for (double[] xi : x)
            L += logp(xi);

        return L;
    }
}
