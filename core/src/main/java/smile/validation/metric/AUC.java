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

import smile.sort.QuickSort;

import java.io.Serial;

/**
 * The area under the curve (AUC). When using normalized units, the area under
 * the curve is equal to the probability that a classifier will rank a
 * randomly chosen positive instance higher than a randomly chosen negative
 * one (assuming 'positive' ranks higher than 'negative').
 * <p>
 * In statistics, a receiver operating characteristic (ROC), or ROC curve,
 * is a graphical plot that illustrates the performance of a binary classifier
 * system as its discrimination threshold is varied. The curve is created by
 * plotting the true positive rate (TPR) against the false positive rate (FPR)
 * at various threshold settings.
 * <p>
 * AUC is quite noisy as a classification measure and has some other
 * significant problems in model comparison.
 * <p>
 * We calculate AUC based on
 * <a href="https://en.wikipedia.org/wiki/Mann-Whitney_U_test">Mann-Whitney U test</a>.
 *
 * @author Haifeng Li
 */
public class AUC implements ProbabilisticClassificationMetric {
    @Serial
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public static final AUC instance = new AUC();

    /** Constructor. */
    public AUC() {

    }

    @Override
    public double score(int[] truth, double[] probability) {
        return of(truth, probability);
    }

    /**
     * Calculates AUC for binary classifier.
     * @param truth the ground truth.
     * @param probability the posterior probability of positive class.
     * @return AUC
     */
    public static double of(int[] truth, double[] probability) {
        if (truth.length != probability.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, probability.length));
        }

        // for large sample size, overflow may happen for pos * neg.
        // switch to double to prevent it.
        double pos = 0;
        double neg = 0;

        for (int label : truth) {
            if (label == 0) {
                neg++;
            } else if (label == 1) {
                pos++;
            } else {
                throw new IllegalArgumentException("AUC is only for binary classification. Invalid label: " + label);
            }
        }

        int[] label = truth.clone();
        double[] prediction = probability.clone();

        QuickSort.sort(prediction, label);

        double[] rank = new double[label.length];
        for (int i = 0; i < prediction.length; i++) {
            if (i == prediction.length - 1 || prediction[i] != prediction[i+1]) {
                rank[i] = i + 1;
            } else {
                int j = i + 1;
                for (; j < prediction.length && prediction[j] == prediction[i]; j++);
                double r = (i + 1 + j) / 2.0;
                for (int k = i; k < j; k++) rank[k] = r;
                i = j - 1;
            }
        }

        double auc = 0.0;
        for (int i = 0; i < label.length; i++) {
            if (label[i] == 1)
                auc += rank[i];
        }

        auc = (auc - (pos * (pos+1) / 2.0)) / (pos * neg);
        return auc;
    }

    @Override
    public String toString() {
        return "AUC";
    }
}