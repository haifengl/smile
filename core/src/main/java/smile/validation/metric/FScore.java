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
 * The F-score (or F-measure) considers both the precision and the recall of the test
 * to compute the score. The precision p is the number of correct positive results
 * divided by the number of all positive results, and the recall r is the number of
 * correct positive results divided by the number of positive results that should
 * have been returned.
 * <p>
 * The traditional or balanced F-score (F1 score) is the harmonic mean of
 * precision and recall, where an F1 score reaches its best value at 1 and worst at 0.
 * <p>
 * The general formula involves a positive real &beta; so that F-score measures
 * the effectiveness of retrieval with respect to a user who attaches &beta; times
 * as much importance to recall as precision.
 *
 * @author Haifeng Li
 */
public class FScore implements ClassificationMetric {
    @Serial
    private static final long serialVersionUID = 2L;
    /** The F_1 score, the harmonic mean of precision and recall. */
    public final static FScore F1 = new FScore(1.0);
    /** The F_2 score, which weighs recall higher than precision. */
    public final static FScore F2 = new FScore(2.0);
    /** The F_0.5 score, which weighs recall lower than precision. */
    public final static FScore FHalf = new FScore(0.5);

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(beta, truth, prediction);
    }

    /**
     * A positive value such that F-score measures the effectiveness of
     * retrieval with respect to a user who attaches &beta; times
     * as much importance to recall as precision. The default value 1.0
     * corresponds to F1-score.
     */
    private final double beta;

    /** Constructor of F1 score. */
    public FScore() {
        this(1.0);
    }

    /** Constructor of general F-score.
     *
     * @param beta a positive value such that F-score measures
     *             the effectiveness of retrieval with respect
     *             to a user who attaches &beta; times as much
     *             importance to recall as precision.
     */
    public FScore(double beta) {
        if (beta <= 0.0) {
            throw new IllegalArgumentException("Negative beta");
        }

        this.beta = beta;
    }

    /**
     * Calculates the F1 score.
     * @param beta a positive value such that F-score measures
     *             the effectiveness of retrieval with respect
     *             to a user who attaches &beta; times as much
     *             importance to recall as precision.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(double beta, int[] truth, int[] prediction) {
        double beta2 = beta * beta;
        double p = Precision.of(truth, prediction);
        double r = Recall.of(truth, prediction);
        return (1 + beta2) * (p * r) / (beta2 * p + r);
    }

    @Override
    public String toString() {
        return String.format("F-Score(%f)", beta);
    }
}
