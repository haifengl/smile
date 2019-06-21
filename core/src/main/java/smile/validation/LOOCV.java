/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.validation;

/**
 * Leave-one-out cross validation. LOOCV uses a single observation
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
