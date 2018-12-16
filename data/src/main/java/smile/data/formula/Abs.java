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
import smile.data.type.StructType;

/**
 * The term of abs function.
 *
 * @author Haifeng Li
 */
class Abs extends AbstractFunction {
    /**
     * Constructor.
     *
     * @param x the term that the function is applied to.
     */
    public Abs(Term x) {
        super("abs", x);
    }

    @Override
    public Object apply(Tuple o) {
        Object y = x.apply(o);
        if (y == null) return null;

        if (y instanceof Double) return Math.abs((double) y);
        else if (y instanceof Integer) return Math.abs((int) y);
        else if (y instanceof Float) return Math.abs((float) y);
        else if (y instanceof Long) return Math.abs((long) y);
        else throw new IllegalArgumentException("Invalid argument for abs(): " + y);
    }

    @Override
    public int applyAsInt(Tuple o) {
        return Math.abs(x.applyAsInt(o));
    }

    @Override
    public long applyAsLong(Tuple o) {
        return Math.abs(x.applyAsLong(o));
    }

    @Override
    public float applyAsFloat(Tuple o) {
        return Math.abs(x.applyAsFloat(o));
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return Math.abs(x.applyAsDouble(o));
    }

    @Override
    public DataType type() {
        return x.type();
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);

        if (!(x.type().isInt() ||
              x.type().isLong() ||
              x.type().isDouble() ||
              x.type().isFloat())) {
            throw new IllegalStateException(String.format("Invalid expression: abs(%s)", x.type()));
        }
    }
}
