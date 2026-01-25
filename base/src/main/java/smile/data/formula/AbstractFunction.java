/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.formula;

import java.util.Set;

/**
 * This class provides a skeletal implementation of the function term.
 *
 * @author Haifeng Li
 */
public abstract class AbstractFunction implements Term {
    /** The name of function. */
    final String name;
    /** The operand. */
    final Term x;

    /**
     * Constructor.
     *
     * @param name the name of function.
     * @param x the term that the function is applied to.
     */
    public AbstractFunction(String name, Term x) {
        this.name = name;
        this.x = x;
    }

    @Override
    public String toString() {
        if (x instanceof Operator)
            return String.format("%s%s", name, x);
        else
            return String.format("%s(%s)", name, x);
    }

    @Override
    public Set<String> variables() {
        return x.variables();
    }
}
