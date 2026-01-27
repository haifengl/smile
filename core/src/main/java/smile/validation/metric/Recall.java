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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.validation.metric;

import java.io.Serial;
import smile.math.MathEx;

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
    public static final Recall instance = new Recall();
    /** The aggregating strategy for multi-classes. */
    private final Averaging strategy;

    /**
     * Constructor.
     */
    public Recall() {
        this(null);
    }

    /**
     * Constructor.
     * @param strategy The aggregating strategy for multi-classes.
     */
    public Recall(Averaging strategy) {
        this.strategy = strategy;
    }

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction, strategy);
    }

    @Override
    public String toString() {
        return strategy == null ? "Recall" : strategy + "-Recall";
    }

    /**
     * Calculates the recall/sensitivity of binary classification.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction) {
        for (int t : truth) {
            if (t != 0 && t != 1) {
                throw new IllegalArgumentException("Recall can only be applied to binary classification: " + t);
            }
        }

        for (int p : prediction) {
            if (p != 0 && p != 1) {
                throw new IllegalArgumentException("Recall can only be applied to binary classification: " + p);
            }
        }

        return of(truth, prediction, null);
    }

    /**
     * Calculates the recall/sensitivity.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @param strategy The aggregating strategy for multi-classes.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction, Averaging strategy) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int numClasses = Math.max(MathEx.max(truth), MathEx.max(prediction)) + 1;
        if (numClasses > 2 && strategy == null) {
            throw new IllegalArgumentException("Averaging strategy is null for multi-class");
        }

        int length = strategy == Averaging.Macro || strategy == Averaging.Weighted ? numClasses : 1;
        int[] tp = new int[length];
        int[] size = new int[numClasses];

        int n = truth.length;
        for (var target : truth) {
            ++size[target];
        }

        if (strategy == null) {
            for (int i = 0; i < n; i++) {
                if (prediction[i] == 1 && truth[i] == 1) {
                    tp[0]++;
                }
            }
        } else if (strategy == Averaging.Micro) {
            for (int i = 0; i < n; i++) {
                tp[0] += truth[i] == prediction[i] ?  1 : 0;
            }
        } else {
            for (int i = 0; i < n; i++) {
                tp[truth[i]] += truth[i] == prediction[i] ?  1 : 0;
            }
        }

        double[] recall = new double[tp.length];
        if (tp.length == 1) {
            recall[0] = (double) tp[0] / (strategy == null ? size[1] : n);
        } else {
            for (int i = 0; i < tp.length; i++) {
                recall[i] = (double) tp[i] / size[i];
            }
        }

        if (strategy == Averaging.Macro) {
            return MathEx.mean(recall);
        } else if (strategy == Averaging.Weighted) {
            double weighted = 0.0;
            for (int i = 0; i < numClasses; i++) {
                weighted += recall[i] * size[i];
            }
            return weighted / n;
        }
        return recall[0];
    }
}
