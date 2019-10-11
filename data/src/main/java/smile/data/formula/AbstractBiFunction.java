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
import smile.data.type.StructType;

/**
 * This class provides a skeletal implementation of the bi-function term.
 *
 * @author Haifeng Li
 */
public abstract class AbstractBiFunction extends AbstractTerm {
    /** The name of function. */
    String name;
    /** The first parameter of function. */
    Term x;
    /** The second parameter of function. */
    Term y;

    /**
     * Constructor.
     *
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     */
    public AbstractBiFunction(String name, Term x, Term y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    @Override
    public String name() {
        return String.format("%s(%s, %s)", name, x.name(), y.name());
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public Set<String> variables() {
        Set<String> vars = new HashSet<>(x.variables());
        vars.addAll(y.variables());
        return vars;
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);
        y.bind(schema);
    }
}
