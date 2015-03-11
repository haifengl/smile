/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * The false discovery rate (FDR) is ratio of false positives
 * to combined true and false positives, which is actually 1 - precision.
 * <p>
 * FDR = FP / (TP + FP)
 *
 * @author Haifeng Li
 */
public class FDR implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int fp = 0;
        int p = 0;
        for (int i = 0; i < truth.length; i++) {
            if (prediction[i] == 1) {
                p++;

                if (truth[i] != 1) {
                    fp++;
                }
            }
        }

        return (double) fp / p;
    }

    @Override
    public String toString() {
        return "False Discovery Rate";
    }
}
