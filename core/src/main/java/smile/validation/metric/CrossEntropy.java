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
 * Cross entropy generalizes the log loss metric to multiclass problems.
 *
 * @author Haifeng Li
 */
public interface CrossEntropy {
    /**
     * Calculates the cross entropy for multiclass classifier.
     * @param truth The sample labels
     * @param probability The posterior probability of samples.
     * @return Cross entropy
     */
    static double of(int[] truth, double[][] probability) {
        if (truth.length != probability.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, probability.length));
        }

        int n = truth.length;
        double loss = 0.0;

        for (int i = 0; i < n; i++) {
            loss -= Math.log(probability[i][truth[i]]);
        }

        return loss / n;
    }
}