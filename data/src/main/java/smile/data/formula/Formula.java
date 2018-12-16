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

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * A formula extracts the variables from a context
 * (e.g. Java Class or DataFrame).
 *
 * @author Haifeng Li
 */
public class Formula implements Serializable {
    /** The hyper-terms. */
    private HyperTerm[] hyperterms;
    /** The terms after binding to a schema and expanding the hyper-terms. */
    private Term[] terms;
    /** The formula output schema. */
    private StructType schema;

    /**
     * Constructor.
     * @param terms the predictor terms.
     */
    public Formula(HyperTerm... terms) {
        this.hyperterms = terms;
    }

    /** Returns the terms of formula. This should be called after bind() called. */
    public Term[] terms() {
        return terms;
    }

    /**
     * Apply the formula on a tuple to generate the model data.
     */
    public Tuple apply(Tuple t) {
        return new Tuple() {
            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public Object get(int i) {
                return terms[i].apply(t);
            }

            @Override
            public int getInt(int i) {
                return terms[i].applyAsInt(t);
            }

            @Override
            public long getLong(int i) {
                return terms[i].applyAsLong(t);
            }

            @Override
            public float getFloat(int i) {
                return terms[i].applyAsFloat(t);
            }

            @Override
            public double getDouble(int i) {
                return terms[i].applyAsDouble(t);
            }

            @Override
            public String toString() {
                return schema.toString(this);
            }
        };
    }

    /** Binds the formula to a schema and returns the output schema of formula. */
    public StructType bind(StructType inputSchema) {
        Arrays.stream(hyperterms).forEach(term -> term.bind(inputSchema));

        List<Term> factors = Arrays.stream(hyperterms)
                .filter(term -> !(term instanceof All) && !(term instanceof Delete))
                .flatMap(term -> term.terms().stream())
                .collect(Collectors.toList());

        List<Term> removes = Arrays.stream(hyperterms)
                .filter(term -> term instanceof Delete)
                .flatMap(term -> term.terms().stream())
                .collect(Collectors.toList());

        Set<String> variables = factors.stream()
            .flatMap(factor -> factor.variables().stream())
            .collect(Collectors.toSet());

        Optional<All> hasAll = Arrays.stream(hyperterms)
                .filter(term -> term instanceof All)
                .map(term -> (All) term)
                .findAny();

        List<Term> result = new ArrayList<>();
        if (hasAll.isPresent()) {
            All all = hasAll.get();
            java.util.stream.Stream<Variable> stream = all.terms().stream();
            if (all.rest()) {
                stream = stream.filter(term -> !variables.contains(term.name()));
            }

            result.addAll(stream.collect(Collectors.toList()));
        }

        result.addAll(factors);
        result.removeAll(removes);
        this.terms = result.toArray(new Term[result.size()]);

        List<StructField> fields = result.stream()
                .map(factor -> new StructField(factor.toString(), factor.type()))
                .collect(Collectors.toList());

        schema = DataTypes.struct(fields);
        return schema;
    }
}
