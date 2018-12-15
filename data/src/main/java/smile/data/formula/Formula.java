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
    /** The predictor terms. */
    private Term[] terms;
    /** The factors after binding to a schema and expanding the terms. */
    private Function[] factors;
    /** The formula output schema. */
    private StructType schema;

    /**
     * Constructor.
     * @param terms the predictor terms.
     */
    public Formula(Term... terms) {
        this.terms = terms;
    }

    /** Returns the factors of formula. This should be called after bind() called. */
    public Function[] factors() {
        return factors;
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
            public String toString() {
                return schema.toString(this);
            }
        };
    }

    /** Binds the formula to a schema and returns the output schema of formula. */
    public StructType bind(StructType inputSchema) {
        Arrays.stream(terms).forEach(term -> term.bind(inputSchema));

        List<Function> factors = Arrays.stream(terms)
                .filter(term -> !(term instanceof All) && !(term instanceof Remove))
                .flatMap(term -> term.factors().stream())
                .collect(Collectors.toList());

        List<Function> removes = Arrays.stream(terms)
                .filter(term -> term instanceof Remove)
                .flatMap(term -> term.factors().stream())
                .collect(Collectors.toList());

        Set<String> variables = factors.stream()
            .flatMap(factor -> factor.variables().stream())
            .collect(Collectors.toSet());

        Optional<All> hasAll = Arrays.stream(terms)
                .filter(term -> term instanceof All)
                .map(term -> (All) term)
                .findAny();

        List<Function> result = new ArrayList<>();
        if (hasAll.isPresent()) {
            All all = hasAll.get();
            java.util.stream.Stream<Variable> stream = all.factors().stream();
            if (all.rest()) {
                stream = stream.filter(factor -> !variables.contains(factor.name()));
            }

            result.addAll(stream.collect(Collectors.toList()));
        }

        result.addAll(factors);
        result.removeAll(removes);
        this.factors = result.toArray(new Function[result.size()]);

        List<StructField> fields = result.stream()
                .map(factor -> new StructField(factor.toString(), factor.type()))
                .collect(Collectors.toList());

        schema = DataTypes.struct(fields);
        return schema;
    }

    /** Returns all columns not otherwise in the formula. */
    public static Term all() {
        return new All();
    }

    /** Returns all columns if rest is true or only those not otherwise in the formula. */
    public static Term all(boolean rest) {
        return new All(rest);
    }

    /** Returns a column factor. */
    public static Function col(String name) {
        return new Variable(name);
    }

    /** Removes a column from the formula/model. */
    public static Function remove(String name) {
        return remove(col(name));
    }

    /** Removes a factor from the formula/model. */
    public static Function remove(Function x) {
        return new Remove(x);
    }

    /** Adds two factors. */
    public static Function add(Function a, Function b) {
        return new Add(a, b);
    }

    /** Adds two factors. */
    public static Function add(String a, String b) {
        return new Add(col(a), col(b));
    }

    /** Adds two factors. */
    public static Function add(Function a, String b) {
        return new Add(a, col(b));
    }

    /** Adds two factors. */
    public static Function add(String a, Function b) {
        return new Add(col(a), b);
    }

    /** Subtracts two factors. */
    public static Function sub(Function a, Function b) {
        return new Sub(a, b);
    }

    /** Subtracts two factors. */
    public static Function sub(String a, String b) {
        return new Sub(col(a), col(b));
    }

    /** Subtracts two factors. */
    public static Function sub(Function a, String b) {
        return new Sub(a, col(b));
    }

    /** Subtracts two factors. */
    public static Function sub(String a, Function b) {
        return new Sub(col(a), b);
    }

    /** Multiplies two factors. */
    public static Function mul(Function a, Function b) {
        return new Mul(a, b);
    }

    /** Multiplies two factors. */
    public static Function mul(String a, String b) {
        return new Mul(col(a), col(b));
    }

    /** Multiplies two factors. */
    public static Function mul(Function a, String b) {
        return new Mul(a, col(b));
    }

    /** Multiplies two factors. */
    public static Function mul(String a, Function b) {
        return new Mul(col(a), b);
    }

    /** Divides two factors. */
    public static Function div(Function a, Function b) {
        return new Div(a, b);
    }

    /** Divides two factors. */
    public static Function div(String a, String b) {
        return new Div(col(a), col(b));
    }

    /** Divides two factors. */
    public static Function div(Function a, String b) {
        return new Div(a, col(b));
    }

    /** Divides two factors. */
    public static Function div(String a, Function b) {
        return new Div(col(a), b);
    }

    /** Returns a const boolean factor. */
    public static Function val(final boolean x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.BooleanType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const char factor. */
    public static Function val(final char x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.CharType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const byte factor. */
    public static Function val(final byte x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.ByteType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const short factor. */
    public static Function val(final short x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.ShortType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const integer factor. */
    public static Function val(final int x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.IntegerType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public int applyAsInt(Tuple o) {
                return x;
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const long factor. */
    public static Function val(final long x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.LongType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public long applyAsLong(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const float factor. */
    public static Function val(final float x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.FloatType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public float applyAsFloat(Tuple o) {
                return x;
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const double factor. */
    public static Function val(final double x) {
        return new Function() {
            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return DataTypes.DoubleType;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return x;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a const object factor. */
    public static Function val(final Object x) {
        final DataType type = x instanceof String ? DataTypes.StringType :
                x instanceof LocalDate ? DataTypes.DateType :
                x instanceof LocalDateTime ? DataTypes.DateTimeType :
                x instanceof LocalTime ? DataTypes.TimeType :
                DataType.of(x.getClass());

        return new Function() {
            @Override
            public String name() {
                return x.toString();
            }

            @Override
            public String toString() {
                return x.toString();
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return Collections.emptySet();
            }

            @Override
            public DataType type() {
                return type;
            }

            @Override
            public void bind(StructType schema) {
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given column.
     * @param name the function name.
     * @param x the column name.
     * @param f the lambda to apply on the column.
     */
    public static <T> Function apply(final String name, final String x, ToIntFunction<T> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    @SuppressWarnings("unchecked")
    public static <T> Function apply(final String name, final Function x, ToIntFunction<T> f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public String toString() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return x.variables();
            }

            @Override
            public DataType type() {
                return DataTypes.IntegerType;
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
            }

            @Override
            public int applyAsInt(Tuple o) {
                return f.applyAsInt((T) x.apply(o));
            }

            @Override
            public long applyAsLong(Tuple o) {
                return f.applyAsInt((T) x.apply(o));
            }

            @Override
            public float applyAsFloat(Tuple o) {
                return f.applyAsInt((T) x.apply(o));
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return f.applyAsInt((T) x.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsInt((T) x.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given column.
     * @param name the function name.
     * @param x the column name.
     * @param f the lambda to apply on the column.
     */
    public static <T> Function apply(final String name, final String x, ToLongFunction<T> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    @SuppressWarnings("unchecked")
    public static <T> Function apply(final String name, final Function x, ToLongFunction<T> f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public String toString() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return x.variables();
            }

            @Override
            public DataType type() {
                return DataTypes.LongType;
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
            }

            @Override
            public long applyAsLong(Tuple o) {
                return f.applyAsLong((T) x.apply(o));
            }

            @Override
            public float applyAsFloat(Tuple o) {
                return f.applyAsLong((T) x.apply(o));
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return f.applyAsLong((T) x.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsLong((T) x.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given column.
     * @param name the function name.
     * @param x the column name.
     * @param f the lambda to apply on the column.
     */
    public static <T> Function apply(final String name, final String x, ToDoubleFunction<T> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    @SuppressWarnings("unchecked")
    public static <T> Function apply(final String name, final Function x, ToDoubleFunction<T> f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public String toString() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return x.variables();
            }

            @Override
            public DataType type() {
                return DataTypes.DoubleType;
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return f.applyAsDouble((T) x.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsDouble((T) x.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given column.
     * @param name the function name.
     * @param x the column name.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the column.
     */
    public static <T, R> Function apply(final String name, final String x, final Class<R> clazz, java.util.function.Function f) {
        return apply(name, col(x), clazz, f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the factor.
     */
    @SuppressWarnings("unchecked")
    public static <T, R> Function apply(final String name, final Function x, final Class<R> clazz, java.util.function.Function f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public String toString() {
                return String.format("%s(%s)", name, x);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                return x.variables();
            }

            @Override
            public DataType type() {
                return DataTypes.object(clazz);
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
            }

            @Override
            public Object apply(Tuple o) {
                return f.apply((T) x.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given columns.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the columns.
     */
    public static <T, U> Function apply(final String name, final String x, final String y, ToIntBiFunction<T, U> f) {
        return apply(name, col(x), col(y), f);
    }

    /**
     * Returns a factor that applies a lambda on given factors.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the factors.
     */
    @SuppressWarnings("unchecked")
    public static <T, U> Function apply(final String name, final Function x, final Function y, ToIntBiFunction<T, U> f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public String toString() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                Set<String> vars = new HashSet<>(x.variables());
                vars.addAll(y.variables());
                return vars;
            }

            @Override
            public DataType type() {
                return DataTypes.IntegerType;
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
                y.bind(schema);
            }

            @Override
            public int applyAsInt(Tuple o) {
                return f.applyAsInt((T) x.apply(o), (U) y.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsInt((T) x.apply(o), (U) y.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given columns.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the columns.
     */
    public static <T, U> Function apply(final String name, final String x, final String y, ToLongBiFunction<T, U> f) {
        return apply(name, col(x), col(y), f);
    }

    /**
     * Returns a factor that applies a lambda on given factors.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the factors.
     */
    @SuppressWarnings("unchecked")
    public static <T, U> Function apply(final String name, final Function x, final Function y, ToLongBiFunction<T, U> f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public String toString() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                Set<String> vars = new HashSet<>(x.variables());
                vars.addAll(y.variables());
                return vars;
            }

            @Override
            public DataType type() {
                return DataTypes.LongType;
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
                y.bind(schema);
            }

            @Override
            public long applyAsLong(Tuple o) {
                return f.applyAsLong((T) x.apply(o), (U) y.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsLong((T) x.apply(o), (U) y.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given columns.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the columns.
     */
    public static <T, U> Function apply(final String name, final String x, final String y, ToDoubleBiFunction<T, U> f) {
        return apply(name, col(x), col(y), f);
    }

    /**
     * Returns a factor that applies a lambda on given factors.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the factors.
     */
    @SuppressWarnings("unchecked")
    public static <T, U> Function apply(final String name, final Function x, final Function y, ToDoubleBiFunction<T, U> f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public String toString() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                Set<String> vars = new HashSet<>(x.variables());
                vars.addAll(y.variables());
                return vars;
            }

            @Override
            public DataType type() {
                return DataTypes.DoubleType;
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
                y.bind(schema);
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return f.applyAsDouble((T) x.apply(o), (U) y.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsDouble((T) x.apply(o), (U) y.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given columns.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the columns.
     */
    public static <T, U, R> Function apply(final String name, final String x, final String y, final Class<R> clazz, BiFunction<T, U, R> f) {
        return apply(name, col(x), col(y), clazz, f);
    }

    /**
     * Returns a factor that applies a lambda on given factors.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the factors.
     */
    @SuppressWarnings("unchecked")
    public static <T, U, R> Function apply(final String name, final Function x, final Function y, final Class<R> clazz, BiFunction<T, U, R> f) {
        return new Function() {
            @Override
            public String name() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public String toString() {
                return String.format("%s(%s, %s)", name, x, y);
            }

            @Override
            public boolean equals(Object o) {
                return name().equals(o);
            }

            @Override
            public List<? extends Function> factors() {
                return Collections.singletonList(this);
            }

            @Override
            public Set<String> variables() {
                Set<String> vars = new HashSet<>(x.variables());
                vars.addAll(y.variables());
                return vars;
            }

            @Override
            public DataType type() {
                return DataTypes.object(clazz);
            }

            @Override
            public void bind(StructType schema) {
                x.bind(schema);
            }

            @Override
            public Object apply(Tuple o) {
                return f.apply((T) x.apply(o), (U) y.apply(o));
            }
        };
    }
}
