/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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
    public static final FScore F1 = new FScore(1.0, null);
    /** The F_2 score, which weighs recall higher than precision. */
    public static final FScore F2 = new FScore(2.0, null);
    /** The F_0.5 score, which weighs recall lower than precision. */
    public static final FScore FHalf = new FScore(0.5, null);

    /**
     * A positive value such that F-score measures the effectiveness of
     * retrieval with respect to a user who attaches &beta; times
     * as much importance to recall as precision. The default value 1.0
     * corresponds to F1-score.
     */
    private final double beta;
    /** The aggregating strategy for multi-classes. */
    private final Averaging strategy;

    /**
     * Constructor of F1 score.
     */
    public FScore() {
        this(1.0, null);
    }

    /** Constructor of general F-score.
     *
     * @param beta a positive value such that F-score measures
     *             the effectiveness of retrieval with respect
     *             to a user who attaches &beta; times as much
     *             importance to recall as precision.
     * @param strategy the aggregating strategy for multi-classes.
     */
    public FScore(double beta, Averaging strategy) {
        if (beta <= 0.0) {
            throw new IllegalArgumentException("Negative beta");
        }

        this.beta = beta;
        this.strategy = strategy;
    }

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction, beta, strategy);
    }

    @Override
    public String toString() {
        return String.format("F-Score(%f%s)", beta, strategy == null ? "" : ", " + strategy);
    }

    /**
     * Calculates the F1 score.
     * @param beta a positive value such that F-score measures
     *             the effectiveness of retrieval with respect
     *             to a user who attaches &beta; times as much
     *             importance to recall as precision.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @param strategy the aggregating strategy for multi-classes.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction, double beta, Averaging strategy) {
        double beta2 = beta * beta;
        double p = Precision.of(truth, prediction, strategy);
        double r = Recall.of(truth, prediction, strategy);
        return (1 + beta2) * (p * r) / (beta2 * p + r);
    }
}
