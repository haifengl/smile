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
