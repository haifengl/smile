/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * Fall-out, false alarm rate, or false positive rate (FPR)
 * <p>
 * FPR = FP / N = FP / (FP + TN)
 * <p>
 * Fall-out is actually Type I error and closely related to specificity
 * (1 - specificity).
 *
 * @author Haifeng Li
 */
public class Fallout implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int tn = 0;
        int n = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] != 1) {
                n++;

                if (prediction[i] == truth[i]) {
                    tn++;
                }
            }
        }

        return 1.0 - (double) tn / n;
    }

    @Override
    public String toString() {
        return "Fall-out";
    }
}
