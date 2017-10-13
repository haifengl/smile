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
 * Fall-out, false alarm rate, or false positive rate (FPR)
 * <p>
 * FPR = FP / N = FP / (FP + TN)
 * <p>
 * Fall-out is actually Type I error and closely related to specificity
 * (1 - specificity).
 *
 * @author Haifeng Li
 */
public class Fallout implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int tn = 0;
        int n = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] != 0 && truth[i] != 1) {
                throw new IllegalArgumentException("Fallout can only be applied to binary classification: " + truth[i]);
            }

            if (prediction[i] != 0 && prediction[i] != 1) {
                throw new IllegalArgumentException("Fallout can only be applied to binary classification: " + prediction[i]);
            }

            if (truth[i] != 1) {
                n++;

                if (prediction[i] == truth[i]) {
                    tn++;
                }
            }
        }

        return 1.0 - (double) tn / n;
    }

    @Override
    public String toString() {
        return "Fall-out";
    }
}
