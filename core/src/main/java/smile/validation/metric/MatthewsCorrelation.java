/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.validation.metric;
import java.io.Serial;

/** Matthews correlation coefficient (MCC).
 * The MCC is a balanced metric for binary classification that evaluates
 * the quality of predictions even when the classes are of very different sizes.
 * It returns a value in [-1, +1], where +1 represents a perfect prediction,
 * 0 indicates random prediction, and -1 indicates total disagreement between
 * ground truth and prediction.
 * @author Haifeng Li
 **/
public class MatthewsCorrelation implements ClassificationMetric {
    @Serial
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public static final MatthewsCorrelation instance = new MatthewsCorrelation();

    /** Constructor. */
    public MatthewsCorrelation() {}

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction);
    }

    @Override
    public String toString() {
        return "MatthewsCorrelation";
    }

    /**
     * Calculates Matthews correlation coefficient.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction) {
        if (truth.length != prediction.length)
            throw new IllegalArgumentException(String.format(
                    "The vector sizes don't match: truth.length = %d, prediction.length = %d",
                    truth.length, prediction.length));

        ConfusionMatrix confusion = ConfusionMatrix.of(truth, prediction);
        int[][] matrix = confusion.matrix();
        if (matrix.length != 2 || matrix[0].length != 2 || matrix[1].length != 2)
            throw new IllegalArgumentException("MCC can only be applied to binary classification: " + confusion);
        // Compute in long to avoid int overflow of tp * tn on large samples (cf. AUC).
        long tp = matrix[1][1];
        long tn = matrix[0][0];
        long fp = matrix[0][1];
        long fn = matrix[1][0];

        double numerator = (double) (tp * tn - fp * fn);
        double denominator = Math.sqrt((double) (tp + fp) * (tp + fn) * (tn + fp) * (tn + fn));
        // A zero marginal makes the denominator 0; MCC is 0 by convention.
        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }
} // class MatthewsCorrelation
