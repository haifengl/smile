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

import smile.data.Tuple;
import smile.data.type.DataType;

/**
 * A factor is a term that generates single value
 * when applied to a data object (e.g. Tuple).
 *
 * @author Haifeng Li
 */
public interface Factor extends Term {
    /** Apply the factor formula on a data object. */
    Object apply(Tuple o);

    /** Apply the factor formula on a data object. */
    double applyAsDouble(Tuple o);

    /** Apply the factor formula on a data object. */
    float applyAsFloat(Tuple o);

    /** Apply the factor formula on a data object. */
    int applyAsInt(Tuple o);

    /** Apply the factor formula on a data object. */
    long applyAsLong(Tuple o);

    /** Returns the name of factor. */
    String name();

    /** Returns the data type of output values. */
    DataType type();
}
