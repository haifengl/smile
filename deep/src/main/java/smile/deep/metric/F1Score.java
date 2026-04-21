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
package smile.deep.metric;

import smile.deep.tensor.Tensor;

/**
 * F1 score is the harmonic mean of precision and recall:
 * <pre>
 *     F1 = 2 * (precision * recall) / (precision + recall)
 *        = 2 * TP / (2 * TP + FP + FN)
 * </pre>
 * It reaches its best value at 1 (perfect precision and recall) and
 * its worst value at 0.
 *
 * @author Haifeng Li
 */
public class F1Score implements Metric {
    /** The underlying precision tracker. */
    private final Precision precision;
    /** The underlying recall tracker. */
    private final Recall recall;
    /** The averaging strategy label (for name). */
    private final Averaging strategy;

    /**
     * Constructor for binary classification.
     */
    public F1Score() {
        this.strategy = null;
        this.precision = new Precision();
        this.recall    = new Recall();
    }

    /**
     * Constructor for binary classification with a custom threshold.
     * @param threshold the threshold for converting model output to binary label.
     */
    public F1Score(double threshold) {
        this.strategy = null;
        this.precision = new Precision(threshold);
        this.recall    = new Recall(threshold);
    }

    /**
     * Constructor for multi-class classification.
     * @param strategy the averaging strategy (Macro, Micro, or Weighted).
     */
    public F1Score(Averaging strategy) {
        this.strategy = strategy;
        this.precision = new Precision(strategy);
        this.recall    = new Recall(strategy);
    }

    @Override
    public String name() {
        return strategy == null ? "F1" : strategy + "-F1";
    }

    @Override
    public String toString() {
        return String.format("%s = %.2f", name(), 100 * compute());
    }

    @Override
    public void update(Tensor output, Tensor target) {
        precision.update(output, target);
        recall.update(output, target);
    }

    @Override
    public double compute() {
        double p = precision.compute();
        double r = recall.compute();
        double denom = p + r;
        return denom == 0.0 ? 0.0 : 2.0 * p * r / denom;
    }

    @Override
    public void reset() {
        precision.reset();
        recall.reset();
    }
}

