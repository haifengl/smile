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
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/**
 * The generic term of applying an integer function.
 *
 * @author Haifeng Li
 */
public class IntFunction extends AbstractFunction {
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
