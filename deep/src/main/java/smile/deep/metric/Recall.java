/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * Recall or true positive rate (TPR) (also called hit rate, sensitivity) is
 * a statistical measures of the performance of a binary classification test.
 * Recall is the proportion of actual positives which are correctly identified
 * as such.
 * <pre>
 *     TPR = TP / P = TP / (TP + FN)
 * </pre>
 * Recall and precision are closely related to the concepts of type I and
 * type II errors. For any test, there is usually a trade-off between the
 * measures. This trade-off can be represented graphically using an ROC curve.
 * <p>
 * In this implementation, the class label 1 is regarded as positive and 0
 * is regarded as negative.
 *
 * @author Haifeng Li
 */
public class Recall implements Metric {
    /** The aggregating strategy for multi-classes. */
    final Averaging strategy;
    /** The threshold for converting input into binary labels. */
    final double threshold;
    /** True positive. */
    Tensor tp;
    /** Sample size per class. */
    Tensor size;

    /**
     * Constructor.
     */
    public Recall() {
        this(0.5);
    }

    /**
     * Constructor.
     * @param threshold The threshold for converting input into binary labels.
     */
    public Recall(double threshold) {
        this.strategy = null;
        this.threshold = threshold;
    }

    /**
     * Constructor.
     * @param strategy The aggregating strategy for multi-classes.
     */
    public Recall(Averaging strategy) {
        this.strategy = strategy;
        this.threshold = 0.5;
    }

    @Override
    public String toString() {
        return String.format("%s = %.2f", name(), 100 * compute());
    }

    @Override
    public String name() {
        return strategy == null ? "Recall" : strategy + "-Recall";
    }

    @Override
    public void update(Tensor output, Tensor target) {
        long numClasses = output.dim() == 2 ? output.size(1) : 2;
        if (numClasses > 2 && strategy == null) {
            throw new IllegalArgumentException("Averaging strategy is null for multi-class");
        }

        if (this.tp == null) {
            long length = strategy == Averaging.Macro || strategy == Averaging.Weighted ? numClasses : 1;
            this.tp = output.newZeros(length);
            this.size = output.newZeros(numClasses);
        }

        Tensor prediction = output.dim() == 2 ?
                output.argmax(1, false) : // get the index of the max log-probability
                Tensor.where(output.lt(threshold), 0, 1);  // get class label by thresholding

        Tensor tp;
        Tensor one = target.newOnes(target.size(0));
        Tensor size = target.newZeros(numClasses).scatterReduce_(0, target, one, "sum");
        if (strategy == null) {
            tp = prediction.mul(target).sum();
        } else {
            Tensor eq = prediction.eq(target);
            if (strategy == Averaging.Micro) {
                tp = prediction.eq(target).sum();
            } else {
                tp = target.newZeros(numClasses).scatterReduce_(0, target.get(eq), one, "sum");
            }
        }

        this.tp.add_(tp);
        this.size.add_(size);
    }

    @Override
    public double compute() {
        Tensor recall;
        if (tp.size(0) == 1) {
            recall = strategy == null ? tp.div(size.getLong(1)) : tp.div(size.sum());
        } else {
            recall = tp.div(size);
        }

        if (strategy == Averaging.Macro) {
            recall = recall.mean();
        } else if (strategy == Averaging.Weighted) {
            recall = recall.mul(size).sum().div(size.sum());
        }
        return recall.doubleValue();
    }

    @Override
    public void reset() {
        if (tp != null) {
            tp.fill_(0);
            size.fill_(0);
        }
    }
}
