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
 * MCC is a correlation coefficient between prediction and actual values. It is considered as a balanced measure for binary classification, even in unbalanced data sets.
 * It  varies between -1 and +1. 1 when there is perfect agreement between ground truth and prediction, -1 when there is a perfect disagreement between ground truth and predictions.
 * MCC of 0 means the model is not better then random.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Matthews_correlation_coefficient">Matthews correlation coefficient</a>
 *
 * @author digital-thinking
 */
public class MCCMeasure implements ClassificationMeasure {
    public double measure(int[] truth, int[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }
        ConfusionMatrix confusionMatrix = new ConfusionMatrix(truth, prediction);
        int[][] matrix = confusionMatrix.getMatrix();

        if (matrix.length != 2 || matrix[0].length != 2) {
            throw new IllegalArgumentException("MCC can only be applied to binary classification: " + confusionMatrix.toString());
        }

        int tp = matrix[0][0];
        int tn = matrix[1][1];
        int fp = matrix[0][1];
        int fn = matrix[1][0];

        int numerator = (tp * tn - fp * fn);
        double denominator = Math.sqrt(tp + fp) * Math.sqrt(tp + fn) * Math.sqrt(tn + fp) * Math.sqrt(tn + fn);

        if (numerator == 0) {
            return 0;
        }

        if ( denominator == 0) {
            throw new IllegalArgumentException("MCC can not be applied, denominator is 0 " + confusionMatrix.toString());
        }

        return numerator / denominator;

    }
}
