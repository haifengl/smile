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

import java.util.Collections;
import java.util.List;

/**
 * A term is recursively constructed from constant symbols,
 * variables and function symbols. A term returns a single value
 * when applied to a data object (e.g. Tuple).
 *
 * @author Haifeng Li
 */
public interface Term extends HyperTerm {
    @Override
    default List<Term> terms() {
        return Collections.singletonList(this);
    }

    /** Returns the data type of output values. */
    DataType type();

    /** Applies the term on a data object. */
    Object apply(Tuple o);

    /** Applies the term on a data object and produces an double-valued result. */
    default double applyAsDouble(Tuple o) {
        throw new UnsupportedOperationException();
    }

    /** Applies the term on a data object and produces an float-valued result. */
    default float applyAsFloat(Tuple o) {
        throw new UnsupportedOperationException();
    }

    /** Applies the term on a data object and produces an int-valued result. */
    default int applyAsInt(Tuple o) {
        throw new UnsupportedOperationException();
    }

    /** Applies the term on a data object and produces an long-valued result. */
    default long applyAsLong(Tuple o) {
        throw new UnsupportedOperationException();
    }

    /** Returns true if the term represents a plain variable. */
    default boolean isVariable() {
        return false;
    }

    /** Returns true if the term represents a constant value. */
    default boolean isConstant() {
        return false;
    }
}
