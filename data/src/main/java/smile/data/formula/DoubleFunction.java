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
 * The generic term of applying a double function.
 *
 * @author Haifeng Li
 */
public class DoubleFunction extends AbstractFunction {
    /** The function on a double. */
    private smile.math.Function lambda;

    /**
     * Constructor.
     *
     * @param name the name of function.
     * @param x the term that the function is applied to.
     * @param lambda the function/lambda.
     */
    public DoubleFunction(String name, Term x, smile.math.Function lambda) {
        super(name, x);
        this.lambda = lambda;
    }

    @Override
    public DataType type() {
        return x.type().id() == DataType.ID.Object ? DataTypes.DoubleObjectType : DataTypes.DoubleType;
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);

        if (!(x.type().isDouble() || x.type().isFloat() || x.type().isInt() || x.type().isLong() || x.type().isShort() || x.type().isByte())) {
            throw new IllegalStateException(String.format("Invalid expression: %s(%s)", name, x.type()));
        }
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return lambda.apply(x.applyAsDouble(o));
    }

    @Override
    public Double apply(Tuple o) {
        Object y = x.apply(o);
        if (y == null) return null;
        else return lambda.apply(((Number) y).doubleValue());
    }
}
