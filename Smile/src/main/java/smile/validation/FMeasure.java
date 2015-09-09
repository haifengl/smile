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
 * The F-measure (also F1 score or F-score) considers both the precision p and
 * the recall r of the test to compute the score. The F-measure is the harmonic
 * mean of precision and recall,
 * <p>
 * F-measure = 2 * precision * recall / (precision + recall)
 * <p>
 * where an F-measure reaches its best value at 1 and worst score at 0.:


 *
 * @author Haifeng Li
 */
public class FMeasure implements ClassificationMeasure {

    @Override
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int tp = 0;
        int p = 0;
        int pp = 0;
        for (int i = 0; i < truth.length; i++) {
            if (truth[i] == 1) {
                pp++;
            }

            if (prediction[i] == 1) {
                p++;

                if (truth[i] == 1) {
                    tp++;
                }
            }
        }

        double precision = (double) tp / p;
        double recall = (double) tp / pp;

        return 2 * precision * recall / (precision + recall);
    }

    @Override
    public String toString() {
        return "F-Measure";
    }
}
