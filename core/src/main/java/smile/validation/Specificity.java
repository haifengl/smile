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

/**
 * Specificity (SPC) or True Negative Rate is a statistical measures of the
 * performance of a binary classification test. Specificity measures the
 * proportion of negatives which are correctly identified.
 * <p>
 * SPC = TN / N = TN / (FP + TN) = 1 - FPR
 * <p>
 * Sensitivity and specificity are closely related to the concepts of type
 * I and type II errors. For any test, there is usually a trade-off between
 * the measures. This trade-off can be represented graphically using an ROC curve.
 * <p>
 * In this implementation, the class label 1 is regarded as positive and 0
 * is regarded as negative.
 *
 * @author Haifeng Li
 */
public class Specificity implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
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
