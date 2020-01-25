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

import java.util.*;
import java.util.stream.Collectors;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructType;

/**
 * Encode categorical features using a one-hot aka one-of-K scheme.
 * Although some method such as decision trees can handle nominal variable
 * directly, other methods generally require nominal variables converted to
 * multiple binary dummy variables to indicate the presence or absence of
 * a characteristic.
 *
 * @author Haifeng Li
 */
class OneHot implements HyperTerm {
    /** The name of variable. */
    private String[] variables;
    /** The terms after binding to the schema. */
    private List<OneHotEncoder> terms;

    /**
     * Constructor.
     * @param variables the factor names. If empty, all nominal factors
     *               in the schema will be one-hot encoded.
     */
    public OneHot(String... variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        if (variables == null || variables.length == 0) {
            return "one-hot";
        } else {
            return String.format("one-hot(%s)", Arrays.stream(variables).collect(Collectors.joining(", ")));
        }
    }

    @Override
    public List<OneHotEncoder> terms() {
        return terms;
    }

    @Override
    public Set<String> variables() {
        if (variables == null || variables.length == 0) {
            return Collections.emptySet();
        } else {
            Set<String> set = new HashSet<>();
            for (String column : variables) set.add(column);
            return set;
        }
    }

    @Override
    public void bind(StructType schema) {
        if (variables == null || variables.length == 0) {
            variables = Arrays.stream(schema.fields())
                    .filter(field -> field.measure instanceof NominalScale)
                    .map(field -> field.name)
                    .toArray(String[]::new);
        }

        terms = new ArrayList<>();
        for (String name : variables) {
            int column = schema.fieldIndex(name);

            Measure measure = schema.field(column).measure;
            if (measure instanceof NominalScale == false) {
                throw new UnsupportedOperationException(String.format("The variable %s is not of nominal", name));
            }

            NominalScale scale = (NominalScale) measure;
            for (int value : scale.values()) {
                terms.add(new OneHotEncoder(name, column, value, scale.level(value)));
            }
        }
    }
}
