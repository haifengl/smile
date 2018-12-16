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
 * The term of round function.
 *
 * @author Haifeng Li
 */
class Round extends AbstractFunction {
    /**
     * Constructor.
     *
     * @param x the term that the function is applied to.
     */
    public Round(Term x) {
        super("round", x);
    }

    @Override
    public DataType type() {
        if (x.type().equals(DataTypes.DoubleType)) return DataTypes.LongType;
        if (x.type().equals(DataTypes.FloatType)) return DataTypes.IntegerType;
        if (x.type().equals(DataTypes.object(Double.class))) return DataTypes.object(Long.class);
        else return DataTypes.object(Integer.class);
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);

        if (!(x.type().isDouble() || x.type().isFloat())) {
            throw new IllegalStateException(String.format("Invalid expression: round(%s)", x.type()));
        }
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return Math.round(x.applyAsDouble(o));
    }

    @Override
    public Long apply(Tuple o) {
        Object y = x.apply(o);
        if (y == null) return null;
        else return Math.round(((Number) y).doubleValue());
    }
}
