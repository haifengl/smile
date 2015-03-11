/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * Leave-one-out cross validation. LOOCV involves using a single observation
 * from the original sample as the validation data, and the remaining
 * observations as the training data. This is repeated such that each
 * observation in the sample is used once as the validation data. This is
 * the same as a K-fold cross-validation with K being equal to the number of
 * observations in the original sample. Leave-one-out cross-validation is
 * usually very expensive from a computational point of view because of the
 * large number of times the training process is repeated.
 * 
 * @author Haifeng Li
 */
public class LOOCV {
    /**
     * The index of training instances.
     */
    public final int[][] train;
    /**
     * The index of testing instances.
     */
    public final int[] test;

    /**
     * Constructor.
     * @param n the number of samples.
     */
    public LOOCV(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        train = new int[n][n-1];
        test = new int[n];

        for (int i = 0; i < n; i++) {
            test[i] = i;
            for (int j = 0, p = 0; j < n; j++) {
                if (j != i) {
                    train[i][p++] = j;
                }
            }
        }
    }
}
