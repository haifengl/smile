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

    /** Returns the factors of formula. This should be called after bind() called. */
    public Factor[] factors() {
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
                return schema.toString(this);
            }
        };
    }

    /** Binds the formula to a schema and returns the output schema of formula. */
    public StructType bind(StructType inputSchema) {
        Arrays.stream(terms).forEach(term -> term.bind(inputSchema));

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

        Optional<All> hasAll = Arrays.stream(terms)
                .filter(term -> term instanceof All)
                .map(term -> (All) term)
                .findAny();

        List<Factor> result = new ArrayList<>();
        if (hasAll.isPresent()) {
            All all = hasAll.get();
            java.util.stream.Stream<Column> stream = all.factors().stream();
            if (all.rest()) {
                stream = stream.filter(factor -> !variables.contains(factor.name()));
            }

            result.addAll(stream.collect(Collectors.toList()));
        }

        result.addAll(factors);
        result.removeAll(removes);
        this.factors = result.toArray(new Factor[result.size()]);

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
    public static Factor col(String name) {
        return new Column(name);
    }

    /** Removes a column from the formula/model. */
    public static Factor remove(String name) {
        return remove(col(name));
    }

    /** Removes a factor from the formula/model. */
    public static Factor remove(Factor x) {
        return new Remove(x);
    }

    /** Returns the absolute value of a factor. */
    public static Factor abs(String name) {
        return abs(col(name));
    }

    /** Returns the absolute value of a factor. */
    public static Factor abs(Factor x) {
        return new Abs(x);
    }

    /** Returns the exp of a factor. */
    public static Factor exp(String name) {
        return exp(col(name));
    }

    /** Returns the exp of a factor. */
    public static Factor exp(Factor x) {
        return new Exp(x);
    }

    /** Returns the log of a factor. */
    public static Factor log(String name) {
        return log(col(name));
    }

    /** Returns the log of a factor. */
    public static Factor log(Factor x) {
        return new Log(x);
    }

    /** Returns the log10 of a factor. */
    public static Factor log10(String name) {
        return log10(col(name));
    }

    /** Returns the log10 of a factor. */
    public static Factor log10(Factor x) {
        return new Log10(x);
    }

    /** Returns the ceil of a factor. */
    public static Factor ceil(String name) {
        return ceil(col(name));
    }

    /** Returns the ceil of a factor. */
    public static Factor ceil(Factor x) {
        return new Ceil(x);
    }

    /** Returns the floor of a factor. */
    public static Factor floor(String name) {
        return floor(col(name));
    }

    /** Returns the floor of a factor. */
    public static Factor floor(Factor x) {
        return new Floor(x);
    }

    /** Returns the round of a factor. */
    public static Factor round(String name) {
        return round(col(name));
    }

    /** Returns the log of a factor. */
    public static Factor round(Factor x) {
        return new Round(x);
    }

    /** Returns the signum of a factor. */
    public static Factor signum(String name) {
        return signum(col(name));
    }

    /** Returns the signum of a factor. */
    public static Factor signum(Factor x) {
        return new Signum(x);
    }

    /** Returns the square root of a factor. */
    public static Factor sqrt(String name) {
        return sqrt(col(name));
    }

    /** Returns the square root of a factor. */
    public static Factor sqrt(Factor x) {
        return new Sqrt(x);
    }

    /** Adds two factors. */
    public static Factor add(Factor a, Factor b) {
        return new Add(a, b);
    }

    /** Adds two factors. */
    public static Factor add(String a, String b) {
        return new Add(col(a), col(b));
    }

    /** Adds two factors. */
    public static Factor add(Factor a, String b) {
        return new Add(a, col(b));
    }

    /** Adds two factors. */
    public static Factor add(String a, Factor b) {
        return new Add(col(a), b);
    }

    /** Subtracts two factors. */
    public static Factor sub(Factor a, Factor b) {
        return new Sub(a, b);
    }

    /** Subtracts two factors. */
    public static Factor sub(String a, String b) {
        return new Sub(col(a), col(b));
    }

    /** Subtracts two factors. */
    public static Factor sub(Factor a, String b) {
        return new Sub(a, col(b));
    }

    /** Subtracts two factors. */
    public static Factor sub(String a, Factor b) {
        return new Sub(col(a), b);
    }

    /** Multiplies two factors. */
    public static Factor mul(Factor a, Factor b) {
        return new Mul(a, b);
    }

    /** Multiplies two factors. */
    public static Factor mul(String a, String b) {
        return new Mul(col(a), col(b));
    }

    /** Multiplies two factors. */
    public static Factor mul(Factor a, String b) {
        return new Mul(a, col(b));
    }

    /** Multiplies two factors. */
    public static Factor mul(String a, Factor b) {
        return new Mul(col(a), b);
    }

    /** Divides two factors. */
    public static Factor div(Factor a, Factor b) {
        return new Div(a, b);
    }

    /** Divides two factors. */
    public static Factor div(String a, String b) {
        return new Div(col(a), col(b));
    }

    /** Divides two factors. */
    public static Factor div(Factor a, String b) {
        return new Div(a, col(b));
    }

    /** Divides two factors. */
    public static Factor div(String a, Factor b) {
        return new Div(col(a), b);
    }

    /** Returns a const boolean factor. */
    public static Factor val(final boolean x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final char x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final byte x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final short x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final int x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final long x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final float x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final double x) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static Factor val(final Object x) {
        final DataType type = x instanceof String ? DataTypes.StringType :
                x instanceof LocalDate ? DataTypes.DateType :
                x instanceof LocalDateTime ? DataTypes.DateTimeType :
                x instanceof LocalTime ? DataTypes.TimeType :
                DataType.of(x.getClass());

        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T> Factor apply(final String name, final String x, ToIntFunction<T> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    @SuppressWarnings("unchecked")
    public static <T> Factor apply(final String name, final Factor x, ToIntFunction<T> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T> Factor apply(final String name, final String x, ToLongFunction<T> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    @SuppressWarnings("unchecked")
    public static <T> Factor apply(final String name, final Factor x, ToLongFunction<T> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T> Factor apply(final String name, final String x, ToDoubleFunction<T> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    @SuppressWarnings("unchecked")
    public static <T> Factor apply(final String name, final Factor x, ToDoubleFunction<T> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T, R> Factor apply(final String name, final String x, final Class<R> clazz, Function<T, R> f) {
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
    public static <T, R> Factor apply(final String name, final Factor x, final Class<R> clazz, Function<T, R> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T, U> Factor apply(final String name, final String x, final String y, ToIntBiFunction<T, U> f) {
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
    public static <T, U> Factor apply(final String name, final Factor x, final Factor y, ToIntBiFunction<T, U> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T, U> Factor apply(final String name, final String x, final String y, ToLongBiFunction<T, U> f) {
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
    public static <T, U> Factor apply(final String name, final Factor x, final Factor y, ToLongBiFunction<T, U> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T, U> Factor apply(final String name, final String x, final String y, ToDoubleBiFunction<T, U> f) {
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
    public static <T, U> Factor apply(final String name, final Factor x, final Factor y, ToDoubleBiFunction<T, U> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
    public static <T, U, R> Factor apply(final String name, final String x, final String y, final Class<R> clazz, BiFunction<T, U, R> f) {
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
    public static <T, U, R> Factor apply(final String name, final Factor x, final Factor y, final Class<R> clazz, BiFunction<T, U, R> f) {
        return new Factor() {
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
            public List<? extends Factor> factors() {
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
