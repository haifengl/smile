/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.validation.metric;

/**
 * Specificity (SPC) or True Negative Rate is a statistical measures of the
 * performance of a binary classification test. Specificity measures the
 * proportion of negatives which are correctly identified.
 * <pre>
 *     SPC = TN / N = TN / (FP + TN) = 1 - FPR
 * </pre>
 * Sensitivity and specificity are closely related to the concepts of type
 * I and type II errors. For any test, there is usually a trade-off between
 * the measures. This trade-off can be represented graphically using an ROC curve.
 * <p>
 * In this implementation, the class label 1 is regarded as positive and 0
 * is regarded as negative.
 *
 * @author Haifeng Li
 */
public class Specificity implements ClassificationMetric {
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static Specificity instance = new Specificity();

    @Override
    public double score(int[] truth, int[] prediction) {
        return of(truth, prediction);
    }

    /**
     * Calculates the specificity.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int tn = 0;
        int n = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] != 0 && truth[i] != 1) {
                throw new IllegalArgumentException("Specificity can only be applied to binary classification: " + truth[i]);
            }

            if (prediction[i] != 0 && prediction[i] != 1) {
                throw new IllegalArgumentException("Specificity can only be applied to binary classification: " + prediction[i]);
            }

            if (truth[i] != 1) {
                n++;

                if (prediction[i] == truth[i]) {
                    tn++;
                }
            }
        }

        return (double) tn / n;
    }

    @Override
    public String toString() {
        return "Specificity";
    }
}
