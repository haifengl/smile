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

import java.util.*;
import java.util.stream.Collectors;

import smile.data.Tuple;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
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
    private final String name;
    /** The terms after binding to the schema. */
    private List<OneHotEncoder> terms;
    /** Column index after binding to a schema. */
    private int index = -1;

    /**
     * Constructor.
     * @param name the name of variable/column.
     */
    public OneHot(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("OneHot(%s)", name);
    }

    @Override
    public List<? extends Term> terms() {
        return terms;
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(name);
    }

    @Override
    public void bind(StructType schema) {
        index = schema.fieldIndex(name);

        Measure measure = schema.measure().get(name);

        if (measure == null || !(measure instanceof NominalScale)) {
            throw new UnsupportedOperationException(String.format("The variable %s is not of nominal", name));
        }

        NominalScale scale = (NominalScale) measure;
        String[] levels = scale.levels();
        terms = new ArrayList<>();
        for (int i = 0; i < levels.length; i++) {
            terms.add(new OneHotEncoder(i, levels[i]));
        }
    }

    /** The one-hot term. */
    class OneHotEncoder extends AbstractTerm implements Term {
        /** The index value of level. */
        int i;
        /** The level of nominal scale. */
        String level;

        /**
         * Constructor.
         */
        public OneHotEncoder(int i, String level) {
            this.i = i;
            this.level = level;
        }

        @Override
        public String toString() {
            return String.format("%s_%s", name, level);
        }

        @Override
        public Set<String> variables() {
            return Collections.singleton(name);
        }

        @Override
        public Object apply(Tuple o) {
            return i == ((Number) o.get(index)).intValue() ? (byte) 1 : (byte) 0;
        }

        @Override
        public DataType type() {
            return DataTypes.ByteType;
        }

        @Override
        public void bind(StructType schema) {

        }
    }
}
