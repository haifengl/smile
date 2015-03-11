/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math;

/**
 * A differentiable function is a function whose derivative exists at each point
 * in its domain.
 *
 * @author Haifeng Li
 */
public interface DifferentiableMultivariateFunction extends MultivariateFunction {

    /**
     * Compute the value and gradient of the function at x.
     */
    public double f(double[] x, double[] gradient);

}
