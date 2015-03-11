/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

import smile.math.Math;

/**
 * Absolute deviation error.
 * 
 * @author Haifeng Li
 */
public class AbsoluteDeviation implements RegressionMeasure {

    @Override
    public double measure(double[] truth, double[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int n = truth.length;
        double error = 0.0;
        for (int i = 0; i < n; i++) {
            error += Math.abs(truth[i] - prediction[i]);
        }

        return error/n;
    }

    @Override
    public String toString() {
        return "Absolute Deviation";
    }
}
