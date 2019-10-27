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

import java.util.HashSet;
import java.util.Set;

/**
 * Generates the confusion matrix based on truth and prediction vectors
 * @author owlmsj
 */
public class ConfusionMatrix {

    /** Confusion matrix. */
    private int[][] matrix;

    /** Constructor. */
    public ConfusionMatrix(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
             throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        Set<Integer> y = new HashSet<>();

        // Sometimes, small test data doesn't have all the classes.
        for (int i = 0; i < truth.length; i++) {
            y.add(truth[i]);
            y.add(prediction[i]);
        }

        int k = 0;
        for (int c : y) {
            if (k < c) k = c;
        }
        matrix = new int[k+1][k+1];

        for (int i = 0; i < truth.length; i++) {
            matrix[truth[i]][prediction[i]] += 1;
        }
    }

    /** Returns the confusion matrix. */
    public int[][] getMatrix() {
        return matrix;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROW=truth and COL=predicted\n");

        for(int i = 0; i < matrix.length; i++){
            sb.append(String.format("class %2d |", i));
            for(int j = 0; j < matrix.length; j++){
                sb.append(String.format("%8d |", matrix[i][j]));
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }
}
