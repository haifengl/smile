/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * The accuracy is the proportion of true results (both true positives and
 * true negatives) in the population.
 * 
 * @author Haifeng Li
 */
public class Accuracy implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int match = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] == prediction[i]) {
                match++;
            }
        }

        return (double) match / truth.length;
    }

    @Override
    public String toString() {
        return "Accuracy";
    }
}
