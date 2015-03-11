/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

import smile.math.Math;

/**
 * Root mean squared error.
 * 
 * @author Haifeng Li
 */
public class RMSE implements RegressionMeasure {

    @Override
    public double measure(double[] truth, double[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int n = truth.length;
        double rss = 0.0;
        for (int i = 0; i < n; i++) {
            rss += Math.sqr(truth[i] - prediction[i]);
        }

        return Math.sqrt(rss/n);
    }

    @Override
    public String toString() {
        return "RMSE";
    }
}
