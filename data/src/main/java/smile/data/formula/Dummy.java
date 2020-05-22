/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.data.formula;

import java.util.*;
import java.util.stream.Collectors;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructType;

/**
 * Dummy encoding of categorical features. Suppose one categorical variable
 * has k levels. One-hot encoding converts it into k variables, while dummy
 * encoding converts it into k-1 variables.
 *
 * @author Haifeng Li
 */
class Dummy implements HyperTerm {
    /** The name of variable. */
    private String[] variables;
    /** The terms after binding to the schema. */
    private List<DummyVariable> terms;

    /**
     * Constructor.
     * @param variables the factor names. If empty, all nominal factors
     *                  in the schema will be one-hot encoded.
     */
    public Dummy(String... variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        if (variables == null || variables.length == 0) {
            return "dummy";
        } else {
            return String.format("dummy(%s)", Arrays.stream(variables).collect(Collectors.joining(", ")));
        }
    }

    @Override
    public List<DummyVariable> terms() {
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
            if (scale.size() > 2) {
                int[] values = scale.values();
                for (int i = 1; i < values.length; i++) {
                    int value = values[i];
                    terms.add(new DummyVariable(name, column, value, scale.level(value)));
                }
            }
        }
    }
}
