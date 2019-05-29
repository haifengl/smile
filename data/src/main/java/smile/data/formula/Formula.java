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
import java.util.*;
import java.util.stream.Collectors;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.*;

/**
 * A formula extracts the variables from a context
 * (e.g. Java Class or DataFrame).
 *
 * @author Haifeng Li
 */
public class Formula implements Serializable {
    /** The left-hand side of formula. */
    private Term lhs;
    /** The right-hand side of formula. */
    private HyperTerm[] rhs;
    /** The terms after binding to a schema and expanding the hyper-terms. */
    private Term[] terms;
    /** The formula output schema. */
    private StructType schema;

    /**
     * Constructor.
     * @param lhs the left-hand side of formula, i.e. dependent variable.
     */
    public Formula(Term lhs) {
        this.lhs = lhs;
        this.rhs = new HyperTerm[] { new All() };
    }

    /**
     * Constructor.
     * @param rhs the right-hand side of formula, i.e. independent/predictor variables.
     */
    public Formula(HyperTerm[] rhs) {
        this.rhs = rhs;
    }

    /**
     * Constructor.
     * @param lhs the left-hand side of formula, i.e. dependent variable.
     * @param rhs the right-hand side of formula, i.e. independent/predictor variables.
     */
    public Formula(Term lhs, HyperTerm[] rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    /**
     * Factory method.
     * @param lhs the left-hand side of formula, i.e. dependent variable.
     */
    public static Formula lhs(Term lhs) {
        return new Formula(lhs);
    }

    /**
     * Factory method.
     * @param rhs the right-hand side of formula, i.e. independent/predictor variables.
     */
    public static Formula rhs(HyperTerm... rhs) {
        return new Formula(rhs);
    }

    /**
     * Factory method.
     * @param lhs the left-hand side of formula, i.e. dependent variable.
     * @param rhs the right-hand side of formula, i.e. independent/predictor variables.
     */
    public static Formula of(Term lhs, HyperTerm... rhs) {
        return new Formula(lhs, rhs);
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
        Arrays.stream(rhs).forEach(term -> term.bind(inputSchema));

        List<Term> factors = Arrays.stream(rhs)
                .filter(term -> !(term instanceof All) && !(term instanceof Delete))
                .flatMap(term -> term.terms().stream())
                .collect(Collectors.toList());

        if (lhs != null) {
            lhs.bind(inputSchema);
            factors.add(lhs);
        }

        List<Term> removes = Arrays.stream(rhs)
                .filter(term -> term instanceof Delete)
                .flatMap(term -> term.terms().stream())
                .collect(Collectors.toList());

        Set<String> variables = factors.stream()
            .flatMap(factor -> factor.variables().stream())
            .collect(Collectors.toSet());

        Optional<All> hasAll = Arrays.stream(rhs)
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

    /**
     * Apply the formula on a DataFrame.
     * @param df The input DataFrame.
     */
    public DataFrame apply(DataFrame df) {
        StructType schema = bind(df.schema());
        StructField[] fields = schema.fields();

        Term[] terms = terms();
        BaseVector[] vectors = new BaseVector[fields.length];
        for (int i = 0; i < fields.length; i++) {
            StructField field = fields[i];
            if (terms[i].isVariable()) {
                vectors[i] = df.column(field.name);
            } else {
                vectors[i] = df.apply(terms[i]);
            }
        }

        return DataFrame.of(vectors);
    }
}
