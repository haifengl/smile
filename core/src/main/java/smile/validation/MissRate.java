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
 * Miss rate, or false negative rate (FNR)
 * <p>
 * FNR = FN / P = FN / (FN + TP)
 * <p>
 * Miss rate is the proportion of positives which yield negative test outcomes
 * and closely related to sensitivity (1 - sensitivity).
 *
 * @author Noam segev
 */
public class MissRate implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int fn = 0;
        int p = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] == 1) {
                p++;

                if (prediction[i] != 1) {
                    fn++;
                }
            }
        }

        return (double) fn / p;
    }

    @Override
    public String toString() {
        return "Miss Rate";
    }
}
