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
 * The precision or positive predictive value (PPV) is ratio of true positives
 * to combined true and false positives, which is different from sensitivity.
 * <pre>
 *     PPV = TP / (TP + FP)
 * </pre>
 *
 * @author Haifeng Li
 */
public class Precision implements Metric {
    /** The aggregating strategy for multi-classes. */
    final Averaging strategy;
    /** The threshold for converting input into binary labels. */
    final double threshold;
    /** True positive. */
    Tensor tp;
    /** False positive. */
    Tensor fp;
    /** Sample size per class. */
    Tensor size;

    /**
     * Constructor.
     */
    public Precision() {
        this(0.5);
    }

    /**
     * Constructor.
     * @param threshold The threshold for converting input into binary labels.
     */
    public Precision(double threshold) {
        this.strategy = null;
        this.threshold = threshold;
    }

    /**
     * Constructor.
     * @param strategy The aggregating strategy for multi-classes.
     */
    public Precision(Averaging strategy) {
        this.strategy = strategy;
        this.threshold = 0.5;
    }

    @Override
    public String toString() {
        return String.format("%s = %.2f", name(), 100 * compute());
    }

    @Override
    public String name() {
        return strategy == null ? "Precision" : strategy + "-Precision";
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
            this.fp = output.newZeros(length);
            this.size = output.newZeros(numClasses);
        }

        Tensor prediction = output.dim() == 2 ?
                output.argmax(1, false) : // get the index of the max log-probability
                Tensor.where(output.lt(threshold), 0, 1);  // get class label by thresholding

        Tensor tp, fp;
        Tensor one = target.newOnes(target.size(0));
        Tensor size = target.newZeros(numClasses).scatterReduce_(0, target, one, "sum");
        if (strategy == null) {
            tp = prediction.mul(target).sum();
            fp = prediction.sum().sub(tp);
        } else {
            Tensor eq = prediction.eq(target);
            Tensor ne = prediction.ne(target);
            if (strategy == Averaging.Micro) {
                tp = prediction.eq(target).sum();
                fp = prediction.ne(target).sum();
            } else {
                tp = target.newZeros(numClasses).scatterReduce_(0, target.get(eq), one, "sum");
                fp = target.newZeros(numClasses).scatterReduce_(0, prediction.get(ne), one, "sum");
            }
        }

        this.tp.add_(tp);
        this.fp.add_(fp);
        this.size.add_(size);
    }

    @Override
    public double compute() {
        Tensor precision = tp.div(tp.add(fp));
        if (strategy == Averaging.Macro) {
            precision = precision.mean();
        } else if (strategy == Averaging.Weighted) {
            precision = precision.mul(size).sum().div(size.sum());
        }
        return precision.doubleValue();
    }

    @Override
    public void reset() {
        if (tp != null) {
            tp.fill_(0);
            fp.fill_(0);
            size.fill_(0);
        }
    }
}
