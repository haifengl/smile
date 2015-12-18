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
 *
 * @author Haifeng Li
 */
public class AUC {

    public AUC() {
    }

    public double measure(int[] truth, double[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int totalPositive = 0;
        int totalNegative = 0;

        int[] label = truth.clone();
        double[] pred = prediction.clone();
        for (int i = 0; i < truth.length; i++) {
            if (label[i] == 0) {
                totalNegative++;
            } else if (label[i] == 1) {
                totalPositive++;
            } else {
                throw new IllegalArgumentException("AUC is only for binary classification. Invalid label: " + label[i]);
            }
        }

        QuickSort.sort(pred, label);

        double fp = 0;
        double tp = 0;
        double fpPrev = 0;
        double tpPrev = 0;
        double area = 0;
        double fPrev = Double.MIN_VALUE;

        for (int i = 0; i < truth.length; i++) {
            double curF = pred[i];
            if (curF != fPrev) {
                area += Math.abs(fp - fpPrev) * ((tp + tpPrev) / 2.0);
                fPrev = curF;
                fpPrev = fp;
                tpPrev = tp;
            }

            if (label[i] == +1) {
                tp++;
            } else {
                fp++;
            }
        }

        area += Math.abs(totalNegative - fpPrev) * ((totalPositive + tpPrev) / 2.0);
        area /= ((double) totalPositive * totalNegative);
        return area;
    }
}