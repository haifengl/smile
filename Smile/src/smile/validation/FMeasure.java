/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * The F-measure (also F1 score or F-score) considers both the precision p and
 * the recall r of the test to compute the score. The F-measure is the harmonic
 * mean of precision and recall,
 * <p>
 * F-measure = 2 * precision * recall / (precision + recall)
 * <p>
 * where an F-measure reaches its best value at 1 and worst score at 0.:


 *
 * @author Haifeng Li
 */
public class FMeasure implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int tp = 0;
        int p = 0;
        int pp = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] == 1) {
                pp++;
            }

            if (prediction[i] == 1) {
                p++;

                if (truth[i] == 1) {
                    tp++;
                }
            }
        }

        double precision = (double) tp / p;
        double recall = (double) tp / pp;

        return 2 * precision * recall / (precision + recall);
    }

    @Override
    public String toString() {
        return "F-Measure";
    }
}
