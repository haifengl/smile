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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * A formula extracts the variables from a context
 * (e.g. Java Class or DataFrame).
 *
 * @author Haifeng Li
 */
public class Formula {
    /** The predictor terms. */
    private Term[] terms;
    /** The factors after binding to a schema and expanding the terms. */
    private Factor[] factors;
    /** The formula output schema. */
    private StructType schema;

    /**
     * Constructor.
     * @param terms the predictor terms.
     */
    public Formula(Term... terms) {
        this.terms = terms;
    }

    /**
     * Apply the formula on a DataFrame to generate the model data.
     */
    public DataFrame apply(DataFrame df) {

        throw new UnsupportedOperationException();
    }

    public Tuple apply(Tuple t) {
        if (schema == null) {
            schema = schema(t.schema());
        }

        return new Tuple() {
            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public int size() {
                return schema.fields().length;
            }

            @Override
            public Object get(int i) {
                return factors[i].apply(t);
            }

            @Override
            public int getInt(int i) {
                return factors[i].applyAsInt(t);
            }

            @Override
            public long getLong(int i) {
                return factors[i].applyAsLong(t);
            }

            @Override
            public float getFloat(int i) {
                return factors[i].applyAsFloat(t);
            }

            @Override
            public double getDouble(int i) {
                return factors[i].applyAsDouble(t);
            }

            @Override
            public int fieldIndex(String name) {
                return schema.fieldIndex(name);
            }

            @Override
            public String toString() {
                return toString(",");
            }
        };
    }

    /** Returns the schema of formula after binding to an input type. */
    public StructType schema(StructType inputType) {
        Arrays.stream(terms).forEach(term -> term.bind(inputType));

        List<Factor> factors = Arrays.stream(terms)
                .filter(term -> !(term instanceof All) && !(term instanceof Remove))
                .flatMap(term -> term.factors().stream())
                .collect(Collectors.toList());

        List<Factor> removes = Arrays.stream(terms)
                .filter(term -> term instanceof Remove)
                .flatMap(term -> term.factors().stream())
                .collect(Collectors.toList());

        Set<String> variables = factors.stream()
            .flatMap(factor -> factor.variables().stream())
            .collect(Collectors.toSet());

        List<Factor> all = Arrays.stream(terms)
                .filter(term -> term instanceof All)
                .flatMap(term -> term.factors().stream())
                .filter(factor -> !variables.contains(factor.name()))
                .collect(Collectors.toList());

        all.addAll(factors);
        all.removeAll(removes);
        this.factors = all.toArray(new Factor[all.size()]);

        List<StructField> fields = all.stream()
                .map(factor -> new StructField(factor.toString(), factor.type()))
                .collect(Collectors.toList());
        return new StructType(fields);
    }
}
