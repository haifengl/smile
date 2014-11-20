/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * An abstract interface to measure the regression performance.
 *
 * @author Haifeng Li
 */
public interface RegressionMeasure {

    /**
     * Returns an index to measure the quality of regression.
     * @param truth the true response values.
     * @param prediction the predicted response values.
     */
    public double measure(double[] truth, double[] prediction);
}
