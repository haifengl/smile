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
import smile.data.type.*;
import smile.data.vector.*;
import smile.data.vector.Vector;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;

/**
 * A formula extracts the variables from a context
 * (e.g. Java Class or DataFrame).
 *
 * @author Haifeng Li
 */
public class Formula implements Serializable {
    /** The left-hand side of formula. */
    private Term response;
    /** The right-hand side of formula. */
    private HyperTerm[] predictors;
    /** The terms after binding to a schema and expanding the hyper-terms. */
    private Term[] terms;
    /** The formula output schema. */
    private StructType schema;

    /**
     * Constructor.
     * @param response the response formula, i.e. dependent variable.
     */
    public Formula(Term response) {
        this.response = response;
        this.predictors = new HyperTerm[] { new All() };
    }

    /**
     * Constructor.
     * @param predictors the right-hand side of formula, i.e. independent/predictor variables.
     */
    public Formula(HyperTerm[] predictors) {
        this.predictors = predictors;
    }

    /**
     * Constructor.
     * @param response the left-hand side of formula, i.e. dependent variable.
     * @param predictors the right-hand side of formula, i.e. independent/predictor variables.
     */
    public Formula(Term response, HyperTerm[] predictors) {
        this.response = response;
        this.predictors = predictors;
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
     * @param predictors the right-hand side of formula, i.e. independent/predictor variables.
     */
    public static Formula rhs(HyperTerm... predictors) {
        return new Formula(predictors);
    }

    /**
     * Factory method.
     * @param response the left-hand side of formula, i.e. dependent variable.
     * @param predictors the right-hand side of formula, i.e. independent/predictor variables.
     */
    public static Formula of(Term response, HyperTerm... predictors) {
        return new Formula(response, predictors);
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
        Arrays.stream(predictors).forEach(term -> term.bind(inputSchema));

        List<Term> factors = Arrays.stream(predictors)
                .filter(term -> !(term instanceof All) && !(term instanceof Delete))
                .flatMap(term -> term.terms().stream())
                .collect(Collectors.toList());

        if (response != null) {
            response.bind(inputSchema);
            factors.add(response);
        }

        List<Term> removes = Arrays.stream(predictors)
                .filter(term -> term instanceof Delete)
                .flatMap(term -> term.terms().stream())
                .collect(Collectors.toList());

        Set<String> variables = factors.stream()
            .flatMap(factor -> factor.variables().stream())
            .collect(Collectors.toSet());

        Optional<All> hasAll = Arrays.stream(predictors)
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
     * Returns a data frame with the variables needed to use formula.
     * @param df The input DataFrame.
     */
    public DataFrame frame(DataFrame df) {
        bind(df.schema());
        Term[] terms = terms();
        BaseVector[] vectors = new BaseVector[terms.length];
        for (int i = 0; i < terms.length; i++) {
            vectors[i] = terms[i].apply(df);
        }

        return DataFrame.of(vectors);
    }
    /**
     * Creates a design (or model) matrix without bias column.
     * @param df The input DataFrame.
     */
    public DenseMatrix matrix(DataFrame df) {
        return matrix(df, false);
    }

    /**
     * Creates a design (or model) matrix.
     * @param df The input DataFrame.
     * @param bias If true, the design matrix includes an all-one column for intercept.
     */
    public DenseMatrix matrix(DataFrame df, boolean bias) {
        bind(df.schema());
        Term[] terms = terms();

        int nrows = df.nrows();
        int ncols = terms.length;
        int j0 = 0;

        if (response != null) {
            ncols -= 1;
            j0 = 1;
        }

        ncols += bias ? 1 : 0;
        DenseMatrix m = Matrix.of(nrows, ncols, 0.0);
        if (bias) for (int i = 0; i < nrows; i++) m.set(i, ncols-1, 1.0);

        for (int j = 0, jj = j0; jj < terms.length; j++, jj++) {
            BaseVector v = terms[jj].apply(df);
            DataType type = terms[jj].type();
            switch (type.id()) {
                case Double:
                case Integer:
                case Float:
                case Long:
                case Boolean:
                case Byte:
                case Short:
                case Char: {
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
                    break;
                }

                case String: {
                    for (int i = 0; i < nrows; i++) {
                        String s = (String) v.get(i);
                        m.set(i, j, s == null ? Double.NaN : Double.valueOf(s));
                    }
                    break;
                }

                case Object: {
                    Class clazz = ((ObjectType) type).getObjectClass();
                    if (clazz == Boolean.class) {
                        for (int i = 0; i < nrows; i++) {
                            Boolean b = (Boolean) v.get(i);
                            if (b != null)
                                m.set(i, j, b.booleanValue() ? 1 : 0);
                            else
                                m.set(i, j, Double.NaN);
                        }
                    } else if (Number.class.isAssignableFrom(clazz)) {
                        for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
                    } else {
                        throw new UnsupportedOperationException(String.format("DataFrame.toMatrix() doesn't support type %s", type));
                    }
                    break;
                }

                default:
                    throw new UnsupportedOperationException(String.format("DataFrame.toMatrix() doesn't support type %s", type));
            }
        }

        return m;
    }


    /**
     * Returns the response vector.
     * @param df The input DataFrame.
     */
    public BaseVector response(DataFrame df) {
        if (response == null) {
            throw new UnsupportedOperationException("Formula doesn't have a response variable");
        }

        response.bind(df.schema());
        return response.apply(df);
    }
}
