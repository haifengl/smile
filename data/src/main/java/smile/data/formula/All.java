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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import smile.data.type.StructType;

/**
 * All columns in the input DataFrame.
 *
 * @author Haifeng Li
 */
class All implements HyperTerm {
    /** All columns in the schema. */
    private List<Variable> columns;

    /**
     * Constructor. All columns not otherwise in the formula.
     */
    public All() {

    }

    @Override
    public String toString() {
        return ".";
    }

    @Override
    public List<Variable> terms() {
        return columns;
    }

    @Override
    public Set<String> variables() {
        return columns.stream().map(Variable::name).collect(Collectors.toSet());
    }

    @Override
    public void bind(StructType schema) {
        columns = Arrays.stream(schema.fields())
                .map(field -> new Variable(field.name))
                .collect(Collectors.toList());

        columns.forEach(column -> column.bind(schema));
    }
}
