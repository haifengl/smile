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
import smile.data.type.StructType;

/**
 * The term of a / b expression.
 *
 * @author Haifeng Li
 */
class Div extends Operator {
    /**
     * Constructor.
     *
     * @param a the first factor.
     * @param b the second factor.
     */
    public Div(Term a, Term b) {
        super("/", a, b);
    }

    @Override
    public int applyAsInt(Tuple o) {
        return a.applyAsInt(o) / b.applyAsInt(o);
    }

    @Override
    public long applyAsLong(Tuple o) {
        return a.applyAsLong(o) / b.applyAsLong(o);
    }

    @Override
    public float applyAsFloat(Tuple o) {
        return a.applyAsFloat(o) / b.applyAsFloat(o);
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return a.applyAsDouble(o) / b.applyAsDouble(o);
    }

    @Override
    public void bind(StructType schema) {
        super.bind(schema);

        if (type.isInt()) {
            lambda = (Tuple o) -> a.applyAsInt(o) / b.applyAsInt(o);
        } else if (type.isLong()) {
            lambda = (Tuple o) -> a.applyAsLong(o) / b.applyAsLong(o);
        } else if (type.isFloat()) {
            lambda = (Tuple o) -> a.applyAsFloat(o) / b.applyAsFloat(o);
        } else if (type.isDouble()) {
            lambda = (Tuple o) -> a.applyAsDouble(o) / b.applyAsDouble(o);
        }
    }
}
