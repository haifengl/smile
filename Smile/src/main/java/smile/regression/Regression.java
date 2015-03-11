/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.regression;

/**
 * Regression analysis includes any techniques for modeling and analyzing
 * the relationship between a dependent variable and one or more independent
 * variables. Most commonly, regression analysis estimates the conditional
 * expectation of the dependent variable given the independent variables.
 * Regression analysis is widely used for prediction and forecasting, where
 * its use has substantial overlap with the field of machine learning. 
 * 
 * @author Haifeng Li
 */
public interface Regression<T> {
    /**
     * Predicts the dependent variable of an instance.
     * @param x the instance.
     * @return the predicted value of dependent variable.
     */
    public double predict(T x);
}
