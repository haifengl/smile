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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.metric;

import smile.deep.tensor.Tensor;

/**
 * The accuracy is the proportion of true results (both true positives and
 * true negatives) in the population.
 *
 * @author Haifeng Li
 */
public class Accuracy implements Metric {
    /** The threshold for converting input into binary labels. */
    final double threshold;
    /** The number of correct predictions. */
    long correct = 0;
    /** The number of samples. */
    long size = 0;

    /**
     * Constructor.
     */
    public Accuracy() {
        this(0.5);
    }

    /**
     * Constructor.
     * @param threshold The threshold for converting input into binary labels.
     */
    public Accuracy(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String toString() {
        return String.format("Accuracy = %.2f", 100 * compute());
    }

    @Override
    public String name() {
        return "Accuracy";
    }

    @Override
    public void update(Tensor output, Tensor target) {
        Tensor prediction = output.dim() == 2 ?
                output.argmax(1, false) : // get the index of the max log-probability
                Tensor.where(output.lt(threshold), 0, 1);  // get class label by thresholding
        correct += prediction.eq(target).sum().intValue();
        size += target.size(0);
    }

    @Override
    public double compute() {
        return (double) correct / size;
    }

    @Override
    public void reset() {
        correct = 0;
        size = 0;
    }
}
