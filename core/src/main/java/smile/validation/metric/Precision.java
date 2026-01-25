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
import smile.math.MathEx;

/**
 * The precision or positive predictive value (PPV) is ratio of true positives
 * to combined true and false positives, which is different from sensitivity.
 * <pre>
 *     PPV = TP / (TP + FP)
 * </pre>
 *
 * @author Haifeng Li
 */
public class Precision implements ClassificationMetric {
    @Serial
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public static final Precision instance = new Precision();
    /** The aggregating strategy for multi-classes. */
    private final Averaging strategy;

    /**
     * Constructor.
     */
    public Precision() {
        this(null);
    }

    /**
     * Constructor.
     * @param strategy The aggregating strategy for multi-classes.
     */
    public Precision(Averaging strategy) {
        this.strategy = strategy;
    }

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction, strategy);
    }

    @Override
    public String toString() {
        return strategy == null ? "Precision" : strategy + "-Precision";
    }

    /**
     * Calculates the precision of binary classification.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction) {
        for (int t : truth) {
            if (t != 0 && t != 1) {
                throw new IllegalArgumentException("Precision can only be applied to binary classification: " + t);
            }
        }

        for (int p : prediction) {
            if (p != 0 && p != 1) {
                throw new IllegalArgumentException("Precision can only be applied to binary classification: " + p);
            }
        }

        return of(truth, prediction, null);
    }

    /**
     * Calculates the precision.
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
        int[] fp = new int[length];
        int[] size = new int[numClasses];

        int n = truth.length;
        for (var target : truth) {
            ++size[target];
        }

        if (strategy == null) {
            for (int i = 0; i < n; i++) {
                if (prediction[i] == 1) {
                    if (truth[i] == 1) {
                        tp[0]++;
                    } else {
                        fp[0]++;
                    }
                }
            }
        } else if (strategy == Averaging.Micro) {
            for (int i = 0; i < n; i++) {
                tp[0] += truth[i] == prediction[i] ? 1 : 0;
                fp[0] += truth[i] != prediction[i] ? 1 : 0;
            }
        } else {
            for (int i = 0; i < n; i++) {
                tp[truth[i]] += truth[i] == prediction[i] ?  1 : 0;
                fp[prediction[i]] += truth[i] != prediction[i] ?  1 : 0;
            }
        }

        double[] precision = new double[tp.length];
        for (int i = 0; i < tp.length; i++) {
            precision[i] = (double) tp[i] / (tp[i] + fp[i]);
        }

        if (strategy == Averaging.Macro) {
            return MathEx.mean(precision);
        } else if (strategy == Averaging.Weighted) {
            double weighted = 0.0;
            for (int i = 0; i < numClasses; i++) {
                weighted += precision[i] * size[i];
            }
            return weighted / n;
        }
        return precision[0];
    }
}
