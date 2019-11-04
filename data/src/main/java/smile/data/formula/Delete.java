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

import java.util.List;
import java.util.Set;
import smile.data.type.StructType;

/**
 * Remove a factor from the formula.
 *
 * @author Haifeng Li
 */
class Delete implements HyperTerm {
    /** The term to delete. */
    HyperTerm x;

    /**
     * Constructor.
     *
     * @param x the term to delete.
     */
    public Delete(HyperTerm x) {
        this.x = x;
    }

    @Override
    public String toString() {
        return String.format("- %s", x);
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);
    }

    @Override
    public List<? extends Term> terms() {
        return x.terms();
    }

    @Override
    public Set<String> variables() {
        return x.variables();
    }
}
