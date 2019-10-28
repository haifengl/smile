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
    public final static Fallout instance = new Fallout();

    @Override
    public double measure(int[] truth, int[] prediction) {
        return of(truth, prediction);
    }

    /** Calculates the false alarm rate. */
    public static double of(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int tn = 0;
        int n = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] != 0 && truth[i] != 1) {
                throw new IllegalArgumentException("Fallout can only be applied to binary classification: " + truth[i]);
            }

            if (prediction[i] != 0 && prediction[i] != 1) {
                throw new IllegalArgumentException("Fallout can only be applied to binary classification: " + prediction[i]);
            }

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
