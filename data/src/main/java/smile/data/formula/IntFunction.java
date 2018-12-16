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
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/**
 * The generic term of applying an integer function.
 *
 * @author Haifeng Li
 */
class IntFunction extends AbstractFunction {
    /** The function on an integer. */
    private smile.math.IntFunction lambda;

    /**
     * Constructor.
     *
     * @param name the name of function.
     * @param x the term that the function is applied to.
     * @param lambda the function/lambda.
     */
    public IntFunction(String name, Term x, smile.math.IntFunction lambda) {
        super(name, x);
        this.lambda = lambda;
    }

    @Override
    public DataType type() {
        return x.type().id() == DataType.ID.Object ? DataTypes.IntegerObjectType : DataTypes.IntegerType;
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);

        if (!(x.type().isInt() || x.type().isShort() || x.type().isLong())) {
            throw new IllegalStateException(String.format("Invalid expression: %s(%s)", name, x.type()));
        }
    }

    @Override
    public int applyAsInt(Tuple o) {
        return lambda.apply(x.applyAsInt(o));
    }

    @Override
    public Integer apply(Tuple o) {
        Object y = x.apply(o);
        if (y == null) return null;
        else return lambda.apply(((Number) y).intValue());
    }
}
