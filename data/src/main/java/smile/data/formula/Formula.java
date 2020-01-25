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

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.*;
import smile.data.vector.*;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;

/**
 * A formula extracts the variables from a context
 * (e.g. Java Class or DataFrame).
 *
 * @author Haifeng Li
 */
public class Formula implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The left-hand side of formula. */
    private Term response = null;
    /** The right-hand side of formula. */
    private HyperTerm[] predictors;
    /** The formula output schema. */
    private transient StructType schema;
    /** The right hand side schema. */
    private transient StructType xschema;
    /** The terms (only predictors) after binding to a schema and expanding the hyper-terms. */
    private transient Term[] x;
    /** The terms (both predictors and response) after binding to a schema and expanding the hyper-terms. */
    private transient Term[] xy;

    /**
     * Constructor.
     * @param response the response formula, i.e. dependent variable.
     */
    public Formula(String response) {
        this(new Variable(response));
    }

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
    public Formula(String response, HyperTerm[] predictors) {
        this(new Variable(response), predictors);
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

    /** Returns a formula with only predictors. */
    public Formula predictors() {
        return rhs(predictors);
    }

    /** Returns the response term. */
    public Optional<Term> response() {
        return Optional.ofNullable(response);
    }

    @Override
    public String toString() {
        String r = response == null ? "" : response.toString();
        String p = Arrays.stream(predictors).map(predictor -> {
            String s = predictor.toString();
            if (!s.startsWith("- ")) s = "+ " + s;
            return s;
        }).collect(Collectors.joining(" "));

        if (p.startsWith("+ ")) p = p.substring(2);
        return String.format("%s ~ %s", r, p);
    }

    /**
     * Factory method.
     * @param lhs the left-hand side of formula, i.e. dependent variable.
     */
    public static Formula lhs(String lhs) {
        return new Formula(lhs);
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
    public static Formula rhs(String... predictors) {
        return new Formula(
                Arrays.stream(predictors)
                        .map(predictor -> new Variable(predictor))
                        .toArray(Term[]::new)
        );
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
    public static Formula of(String response, String... predictors) {
        return new Formula(
                response,
                Arrays.stream(predictors)
                        .map(predictor -> new Variable(predictor))
                        .toArray(Term[]::new)
        );
    }

    /**
     * Factory method.
     * @param response the left-hand side of formula, i.e. dependent variable.
     * @param predictors the right-hand side of formula, i.e. independent/predictor variables.
     */
    public static Formula of(String response, HyperTerm... predictors) {
        return new Formula(response, predictors);
    }

    /**
     * Factory method.
     * @param response the left-hand side of formula, i.e. dependent variable.
     * @param predictors the right-hand side of formula, i.e. independent/predictor variables.
     */
    public static Formula of(Term response, HyperTerm... predictors) {
        return new Formula(response, predictors);
    }

    /**
     * Returns the schema of output data frame.
     */
    public StructType schema() {
        return schema;
    }

    /**
     * Returns the schema of design matrix.
     */
    public StructType xschema() {
        return xschema;
    }

    /** Binds the formula to a schema and returns the output schema of formula. */
    public StructType bind(StructType inputSchema) {
        return bind(inputSchema, true);
    }

    /**
     * Binds the formula to a schema and returns the output schema of formula.
     * @param inputSchema the schema to bind with
     * @param forced if true, bind the formula to the input schema even if it
     *               was bound to another schema before.
     */
    private StructType bind(StructType inputSchema, boolean forced) {
        if (schema != null && !forced) {
            return schema;
        }

        if (response != null) response.bind(inputSchema);
        Arrays.stream(predictors).forEach(term -> term.bind(inputSchema));

        Set<String> columns = new HashSet<>();
        if (response != null) columns.addAll(response.variables());
        Arrays.stream(predictors)
                .filter((predictor -> !(predictor instanceof All)))
                .flatMap(predictor -> predictor.terms().stream())
                .filter(term -> term instanceof Variable)
                .forEach(term -> columns.add(term.name()));

        List<Term> factors = new ArrayList<>();
        if (response != null) factors.add(response);

        factors.addAll(Arrays.stream(predictors)
                .filter(term -> !(term instanceof Delete))
                .flatMap(term -> {
                    if (term instanceof Delete) {
                        return Stream.empty();
                    } else if (term instanceof All) {
                        return term.terms().stream().filter(t -> !columns.contains(t.name()));
                    } else {
                        return term.terms().stream();
                    }
                })
                .collect(Collectors.toList()));

        List<Term> removes = Arrays.stream(predictors)
                .filter(term -> term instanceof Delete)
                .flatMap(term -> term.terms().stream())
                .collect(Collectors.toList());

        factors.removeAll(removes);

        xy = factors.toArray(new Term[factors.size()]);

        StructField[] fields = factors.stream()
                .map(factor -> factor.field())
                .toArray(StructField[]::new);

        schema = DataTypes.struct(fields);

        if (response != null) {
            x = Arrays.copyOfRange(xy, 1, xy.length);
            xschema = DataTypes.struct(Arrays.copyOfRange(fields, 1, fields.length));
        } else {
            x = xy;
            xschema = schema;
        }

        return schema;
    }

    /**
     * Apply the formula on a tuple to generate the model data.
     */
    public Tuple apply(Tuple t) {
        bind(t.schema(), false);

        return new smile.data.AbstractTuple() {
            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public Object get(int i) {
                return xy[i].apply(t);
            }

            @Override
            public int getInt(int i) {
                return xy[i].applyAsInt(t);
            }

            @Override
            public long getLong(int i) {
                return xy[i].applyAsLong(t);
            }

            @Override
            public float getFloat(int i) {
                return xy[i].applyAsFloat(t);
            }

            @Override
            public double getDouble(int i) {
                return xy[i].applyAsDouble(t);
            }

            @Override
            public String toString() {
                return schema.toString(this);
            }
        };
    }

    /**
     * Apply the formula on a tuple to generate the predictors data.
     */
    public Tuple x(Tuple t) {
        bind(t.schema(), false);

        return new smile.data.AbstractTuple() {
            @Override
            public StructType schema() {
                return xschema;
            }

            @Override
            public Object get(int i) {
                return x[i].apply(t);
            }

            @Override
            public int getInt(int i) {
                return x[i].applyAsInt(t);
            }

            @Override
            public long getLong(int i) {
                return x[i].applyAsLong(t);
            }

            @Override
            public float getFloat(int i) {
                return x[i].applyAsFloat(t);
            }

            @Override
            public double getDouble(int i) {
                return x[i].applyAsDouble(t);
            }

            @Override
            public String toString() {
                return xschema.toString(this);
            }
        };
    }

    /**
     * Returns the real values of predictors.
     */
    public double[] xarray(Tuple t) {
        return Arrays.stream(x).mapToDouble(term -> term.applyAsDouble(t)).toArray();
    }

    /**
     * Returns a data frame of predictors and response variable
     * @param df The input DataFrame.
     */
    public DataFrame apply(DataFrame df) {
        bind(df.schema(), true);
        BaseVector[] vectors = Arrays.stream(xy).map(term -> term.apply(df)).toArray(BaseVector[]::new);
        return DataFrame.of(vectors);
    }

    /**
     * Returns a data frame of predictors.
     * @param df The input DataFrame.
     */
    public DataFrame x(DataFrame df) {
        bind(df.schema(), true);
        BaseVector[] vectors = Arrays.stream(x).map(term -> term.apply(df)).toArray(BaseVector[]::new);
        return DataFrame.of(vectors);
    }

    /**
     * Creates a design matrix of predictors without bias column.
     * @param df The input DataFrame.
     */
    public DenseMatrix matrix(DataFrame df) {
        return matrix(df, false);
    }

    /**
     * Creates a design matrix of predictors.
     * @param df The input DataFrame.
     * @param bias If true, the design matrix includes an all-one column for intercept.
     */
    public DenseMatrix matrix(DataFrame df, boolean bias) {
        bind(df.schema(), true);

        int nrows = df.nrows();
        int ncols = x.length;

        ncols += bias ? 1 : 0;
        DenseMatrix m = Matrix.of(nrows, ncols, 0.0);
        if (bias) for (int i = 0; i < nrows; i++) m.set(i, ncols-1, 1.0);

        for (int j = 0; j < x.length; j++) {
            BaseVector v = x[j].apply(df);
            DataType type = x[j].type();
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
    public BaseVector y(DataFrame df) {
        if (response == null) return null;

        response.bind(df.schema());
        return response.apply(df);
    }

    /**
     * Returns the real-valued response value.
     */
    public double y(Tuple t) {
        if (response == null) return 0.0;

        response.bind(t.schema());
        return response.applyAsDouble(t);
    }

    /**
     * Returns the integer-valued response value.
     */
    public int yint(Tuple t) {
        if (response == null) return -1;

        response.bind(t.schema());
        return response.applyAsInt(t);
    }
}
