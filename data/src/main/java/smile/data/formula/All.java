/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    /** If true, keep the original columns. */
    private boolean rest;
    /** All columns in the schema. */
    private List<Variable> columns;

    /**
     * Constructor. All columns not otherwise in the formula.
     */
    public All() {
        this(true);
    }

    /**
     * Constructor.
     * @param rest If true, only columns not in the formula.
     *             Otherwise, keep all the original columns.
     */
    public All(boolean rest) {
        this.rest = rest;
    }

    /**
     * Return true if only columns not in the formula.
     */
    public boolean rest() {
        return rest;
    }

    @Override
    public String toString() {
        return ".";
    }

    @Override
    public List<Variable> factors() {
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
