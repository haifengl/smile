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
import smile.data.type.StructType;

/**
 * The term of a - b expression.
 *
 * @author Haifeng Li
 */
class Sub extends Operator {
    /**
     * Constructor.
     *
     * @param a the first factor.
     * @param b the second factor.
     */
    public Sub(Term a, Term b) {
        super("-", a, b);
    }

    @Override
    public int applyAsInt(Tuple o) {
        return a.applyAsInt(o) - b.applyAsInt(o);
    }

    @Override
    public long applyAsLong(Tuple o) {
        return a.applyAsLong(o) - b.applyAsLong(o);
    }

    @Override
    public float applyAsFloat(Tuple o) {
        return a.applyAsFloat(o) - b.applyAsFloat(o);
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return a.applyAsDouble(o) - b.applyAsDouble(o);
    }

    @Override
    public void bind(StructType schema) {
        super.bind(schema);

        if (type.isInt()) {
            lambda = (Tuple o) -> a.applyAsInt(o) - b.applyAsInt(o);
        } else if (type.isLong()) {
            lambda = (Tuple o) -> a.applyAsLong(o) - b.applyAsLong(o);
        } else if (type.isFloat()) {
            lambda = (Tuple o) -> a.applyAsFloat(o) - b.applyAsFloat(o);
        } else if (type.isDouble()) {
            lambda = (Tuple o) -> a.applyAsDouble(o) - b.applyAsDouble(o);
        }
    }
}
