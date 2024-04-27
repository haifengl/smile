/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.validation.metric;

import java.io.Serial;

/**
 * In information retrieval area, sensitivity is called recall.
 *
 * @see Sensitivity
 *
 * @author Haifeng Li
 */
public class Recall implements ClassificationMetric {
    @Serial
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static Recall instance = new Recall();

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction);
    }

    /**
     * Calculates the recall/sensitivity.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int tp = 0;
        int p = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] != 0 && truth[i] != 1) {
                throw new IllegalArgumentException("Recall can only be applied to binary classification: " + truth[i]);
            }

            if (prediction[i] != 0 && prediction[i] != 1) {
                throw new IllegalArgumentException("Recall can only be applied to binary classification: " + prediction[i]);
            }

            if (truth[i] == 1) {
                p++;

                if (prediction[i] == 1) {
                    tp++;
                }
            }
        }

        return (double) tp / p;
    }

    @Override
    public String toString() {
        return "Recall";
    }
}
