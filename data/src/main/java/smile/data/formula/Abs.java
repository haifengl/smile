/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
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
