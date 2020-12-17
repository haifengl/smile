/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.validation.metric;

/**
 * The number of errors in the population.
 *
 * @author Haifeng Li
 */
public class Error implements ClassificationMetric {
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static Error instance = new Error();

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction);
    }

    /**
     * Calculates the number of errors.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static int of(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int error = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] != prediction[i]) {
                error++;
            }
        }

        return error;
    }

    @Override
    public String toString() {
        return "Error";
    }
}
