/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.validation;

import smile.sort.QuickSort;

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
 * We calculate AUC based on Mann-Whitney U test
 * (https://en.wikipedia.org/wiki/Mann-Whitney_U_test).
 *
 * @author Haifeng Li
 */
public class AUC {

    public AUC() {
    }

    /**
     * Caulculate AUC for binary classifier.
     * @param truth The sample labels
     * @param probability The posterior probability of positive class.
     * @return AUC
     */
    public static double measure(int[] truth, double[] probability) {
        if (truth.length != probability.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, probability.length));
        }

        // for large sample size, overflow may happen for pos * neg.
        // switch to double to prevent it.
        double pos = 0;
        double neg = 0;

        for (int i = 0; i < truth.length; i++) {
            if (truth[i] == 0) {
                neg++;
            } else if (truth[i] == 1) {
                pos++;
            } else {
                throw new IllegalArgumentException("AUC is only for binary classification. Invalid label: " + truth[i]);
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
}