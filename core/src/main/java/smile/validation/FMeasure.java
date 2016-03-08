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
 * The F-score (or F-measure) considers both the precision and the recall of the test
 * to compute the score. The precision p is the number of correct positive results
 * divided by the number of all positive results, and the recall r is the number of
 * correct positive results divided by the number of positive results that should
 * have been returned.
 *
 * The traditional or balanced F-score (F1 score) is the harmonic mean of
 * precision and recall, where an F1 score reaches its best value at 1 and worst at 0.
 *
 * The general formula involves a positive real &beta; so that F-score measures
 * the effectiveness of retrieval with respect to a user who attaches &beta; times
 * as much importance to recall as precision.
 *
 * @author Haifeng Li
 */
public class FMeasure implements ClassificationMeasure {
    /**
     * A positive value such that F-score measures the effectiveness of
     * retrieval with respect to a user who attaches &beta; times
     * as much importance to recall as precision. The default value 1.0
     * corresponds to F1-score.
     */
    private double beta2 = 1.0;

    /** Constructor of F1 score. */
    public FMeasure() {

    }

    /** Constructor of general F-score.
     *
     * @param beta a positive value such that F-score measures
     * the effectiveness of retrieval with respect to a user who attaches &beta; times
     * as much importance to recall as precision.
     */
    public FMeasure(double beta) {
        if (beta <= 0.0)
            throw new IllegalArgumentException("Negative beta");
        this.beta2 = beta * beta;
    }

    @Override
    public double measure(int[] truth, int[] prediction) {
        double p = new Precision().measure(truth, prediction);
        double r = new Recall().measure(truth, prediction);
        return (1 + beta2) * (p * r) / (beta2 * p + r);
    }
}
