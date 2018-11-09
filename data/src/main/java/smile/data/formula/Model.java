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
package smile.data.formula;

import java.util.List;
import smile.data.DataFrame;
import smile.data.Dataset;
import smile.data.Instance;

/**
 * A model specifies the predictors and the response.
 * Given a context (e.g. DataFrame), the formula can be used to
 * generate the model data.
 *
 * @author Haifeng Li
 */
public class Model {
    /** The response variable. */
    private Factor y;
    /** The predictor terms. */
    private List<Term> x;

    /**
     * Constructor.
     * @param y the response variable.
     * @param x the predictor terms.
     */
    public Model(Factor y, List<Term> x) {
        this.y = y;
        this.x = x;
    }

    /**
     * Apply the formula on a DataFrame to generate the model data.
     */
    public Dataset<Instance> apply(DataFrame df) {
        throw new UnsupportedOperationException();
    }
}
