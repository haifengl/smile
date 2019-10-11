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

import java.util.HashSet;
import java.util.Set;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.StructType;

/**
 * The term of a + b add expression.
 *
 * @author Haifeng Li
 */
public abstract class Operator extends AbstractTerm {
    /** The operator name. */
    String name;
    /** The left operand. */
    Term a;
    /** The right operand. */
    Term b;
    /** The data type of output. */
    DataType type;
    /** The lambda with type promotion. */
    java.util.function.Function<Tuple, Object> lambda;

    /**
     * Constructor.
     *
     * @param name the operator name.
     * @param a the left operand.
     * @param b the right operand.
     */
    public Operator(String name, Term a, Term b) {
        this.name = name;
        this.a = a;
        this.b = b;
    }

    @Override
    public String name() {
        return String.format("%s %s %s", a.name(), name, b.name());
    }

    @Override
    public DataType type() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("(%s)", name());
    }

    @Override
    public Set<String> variables() {
        Set<String> vars = new HashSet<>(a.variables());
        vars.addAll(b.variables());
        return vars;
    }

    @Override
    public Object apply(Tuple o) {
        Object x = a.apply(o);
        Object y = b.apply(o);

        if (x == null || y == null)
            return null;

        return lambda.apply(o);
    }

    @Override
    public void bind(StructType schema) {
        a.bind(schema);
        b.bind(schema);
        type = DataType.prompt(a.type(), b.type());
    }
}
