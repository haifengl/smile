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
 * The accuracy is the proportion of true results (both true positives and
 * true negatives) in the population.
 * 
 * @author Haifeng Li
 */
public class Accuracy implements ClassificationMeasure {
    public final static Accuracy instance = new Accuracy();

    @Override
    public double measure(int[] truth, int[] prediction) {
        return of(truth, prediction);
    }

    /** Calculates the classification accuracy. */
    public static double of(int[] truth, int[] prediction) {
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
