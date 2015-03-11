/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math;

/**
 * An interface representing a multivariate real function.
 *
 * @author Haifeng Li
 */
public interface MultivariateFunction {
    /**
     * Compute the value of the function at x.
     */
    public double f(double[] x);
}
