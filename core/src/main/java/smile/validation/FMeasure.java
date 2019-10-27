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
 * The F-score (or F-measure) considers both the precision and the recall of the test
 * to compute the score. The precision p is the number of correct positive results
 * divided by the number of all positive results, and the recall r is the number of
 * correct positive results divided by the number of positive results that should
 * have been returned.
 *
 * The traditional or balanced F-score (F1 score) is the harmonic mean of
 * precision and recall, where an F1 score reaches its best value at 1 and worst at 0.
 *
 * The general formula involves a positive real &beta; so that F-score measures
 * the effectiveness of retrieval with respect to a user who attaches &beta; times
 * as much importance to recall as precision.
 *
 * @author Haifeng Li
 */
public class FMeasure implements ClassificationMeasure {
    public final static FMeasure instance = new FMeasure();

    @Override
    public double measure(int[] truth, int[] prediction) {
        double p = new Precision().measure(truth, prediction);
        double r = new Recall().measure(truth, prediction);
        return (1 + beta2) * (p * r) / (beta2 * p + r);
    }

    /**
     * A positive value such that F-score measures the effectiveness of
     * retrieval with respect to a user who attaches &beta; times
     * as much importance to recall as precision. The default value 1.0
     * corresponds to F1-score.
     */
    private double beta2 = 1.0;

    /** Constructor of F1 score. */
    public FMeasure() {

    }

    /** Constructor of general F-score.
     *
     * @param beta a positive value such that F-score measures
     * the effectiveness of retrieval with respect to a user who attaches &beta; times
     * as much importance to recall as precision.
     */
    public FMeasure(double beta) {
        if (beta <= 0.0)
            throw new IllegalArgumentException("Negative beta");
        this.beta2 = beta * beta;
    }

    /** Calculates the F1 score. */
    public static double of(int[] truth, int[] prediction) {
        return instance.measure(truth, prediction);
    }
}
