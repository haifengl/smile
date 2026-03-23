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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.formula;

import java.util.List;
import java.util.Set;
import smile.data.type.StructType;

/**
 * Remove a factor from the formula.
 *
 * @author Haifeng Li
 */
public class Delete implements Term {
    /** The term to delete. */
    final Term x;

    /**
     * Constructor.
     *
     * @param x the term to delete.
     */
    public Delete(Term x) {
        this.x = x;
    }

    @Override
    public String toString() {
        return String.format("- %s", x);
    }

    @Override
    public List<Feature> bind(StructType schema) {
        throw new IllegalStateException("Delete.bind() should not be called.");
    }

    @Override
    public Set<String> variables() {
        return x.variables();
    }

    @Override
    public List<Term> expand() {
        return x.expand().stream()
                .map(Delete::new)
                .map(t -> (Term) t)
                .toList();
    }
}
