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
 * Log loss is a evaluation metric for binary classifiers and it is sometimes
 * the optimization objective as well in case of logistic regression and neural
 * networks. Log Loss takes into account the uncertainty of the prediction
 * based on how much it varies from the actual label. This provides a more
 * nuanced view of the performance of the model. In general, minimizing
 * Log Loss gives greater accuracy for the classifier. However, it is
 * susceptible in case of imbalanced data.
 *
 * @author Haifeng Li
 */
public class LogLoss implements ProbabilisticClassificationMetric {
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static LogLoss instance = new LogLoss();

    @Override
    public double score(int[] truth, double[] probability) {
        return of(truth, probability);
    }

    /**
     * Calculates the Log Loss for binary classifier.
     * @param truth the ground truth.
     * @param probability the posterior probability of positive class.
     * @return Log Loss
     */
    public static double of(int[] truth, double[] probability) {
        if (truth.length != probability.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, probability.length));
        }

        int n = truth.length;
        double loss = 0.0;

        for (int i = 0; i < n; i++) {
            if (truth[i] == 0) {
                loss -= Math.log(1.0 - probability[i]);
            } else if (truth[i] == 1) {
                loss -= Math.log(probability[i]);
            } else {
                throw new IllegalArgumentException("LogLoss is only for binary classification. Invalid label: " + truth[i]);
            }
        }

        return loss / n;
    }

    @Override
    public String toString() {
        return "LogLoss";
    }
}