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
public interface DifferentiableFunction extends Function {
    /**
     * Compute the value of the derivative function at x.
     */
    public double df(double x);

}
