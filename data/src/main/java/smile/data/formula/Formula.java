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

import smile.data.DataFrame;

/**
 * A formula extracts the variables from a context
 * (e.g. Java Class or DataFrame).
 *
 * @author Haifeng Li
 */
public class Formula {
    /** The predictor terms. */
    private Term[] x;

    /**
     * Constructor.
     * @param x the predictor terms.
     */
    public Formula(Term... x) {
        this.x = x;
    }

    /**
     * Apply the formula on a DataFrame to generate the model data.
     */
    public DataFrame apply(DataFrame df) {
        throw new UnsupportedOperationException();
    }
}
