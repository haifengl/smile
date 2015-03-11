/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.regression;

/**
 * Regression model with online learning capability. Online learning is a
 * model of induction that learns one instance at a time. More formally,
 * an online algorithm proceeds in a sequence of trials.
 * 
 * @param <T> the type of input object
 * 
 * @author Haifeng Li
 */
public interface OnlineRegression <T> extends Regression <T> {
    /**
     * Online update the regression model with a new training instance.
     * In general, this method may be NOT multi-thread safe.
     * 
     * @param x training instance.
     * @param y response variable.
     */
    public void learn(T x, double y);
}
