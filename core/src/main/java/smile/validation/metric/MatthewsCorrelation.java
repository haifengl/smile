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
 * Matthews correlation coefficient. The MCC is in essence a correlation
 * coefficient between the observed and predicted binary classifications.
 * It is considered as a balanced measure for binary classification,
 * even in unbalanced data sets. It  varies between -1 (perfect
 * disagreement) and +1 (perfect agreement). When it is 0,
 * the model is not better then random.
 *
 * @author digital-thinking
 */
public class MatthewsCorrelation implements ClassificationMetric {
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static MatthewsCorrelation instance = new MatthewsCorrelation();

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction);
    }

    /**
     * Calculates Matthews correlation coefficient.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        ConfusionMatrix confusion = ConfusionMatrix.of(truth, prediction);
        int[][] matrix = confusion.matrix;

        if (matrix.length != 2 || matrix[0].length != 2) {
            throw new IllegalArgumentException("MCC can only be applied to binary classification: " + confusion);
        }

        int tp = matrix[0][0];
        int tn = matrix[1][1];
        int fp = matrix[0][1];
        int fn = matrix[1][0];

        int numerator = (tp * tn - fp * fn);
        double denominator = Math.sqrt(tp + fp) * Math.sqrt(tp + fn) * Math.sqrt(tn + fp) * Math.sqrt(tn + fn);

        return numerator / denominator;
    }

    @Override
    public String toString() {
        return "MatthewsCorrelation";
    }
}
