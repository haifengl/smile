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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import smile.data.DataFrame;
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
        schema = schema(df.schema());
        return df.stream().map(this::apply).collect(DataFrame.toDataFrame());
    }

    /**
     * Apply the formula on a Tuple to generate the model data.
     */
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
    private StructType schema(StructType inputType) {
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

    /** Returns all columns not otherwise in the formula. */
    public static Term all() {
        return new All();
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
    public static Factor cst(final boolean x) {
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
    public static Factor cst(final char x) {
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
    public static Factor cst(final byte x) {
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
    public static Factor cst(final short x) {
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
    public static Factor cst(final int x) {
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
    public static Factor cst(final long x) {
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
    public static Factor cst(final float x) {
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
    public static Factor cst(final double x) {
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
    public static Factor cst(final Object x) {
        final DataType type = x instanceof String ? DataTypes.StringType :
                x instanceof LocalDate ? DataTypes.DateType :
                x instanceof LocalDateTime ? DataTypes.DateTimeType :
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
    public static Factor apply(final String name, final String x, ToIntFunction<Object> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    public static Factor apply(final String name, final Factor x, ToIntFunction<Object> f) {
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
                return f.applyAsInt(x.apply(o));
            }

            @Override
            public long applyAsLong(Tuple o) {
                return f.applyAsInt(x.apply(o));
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return f.applyAsInt(x.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsInt(x.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given column.
     * @param name the function name.
     * @param x the column name.
     * @param f the lambda to apply on the column.
     */
    public static Factor apply(final String name, final String x, ToLongFunction<Object> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    public static Factor apply(final String name, final Factor x, ToLongFunction<Object> f) {
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
                return f.applyAsLong(x.apply(o));
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return f.applyAsLong(x.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsLong(x.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given column.
     * @param name the function name.
     * @param x the column name.
     * @param f the lambda to apply on the column.
     */
    public static Factor apply(final String name, final String x, ToDoubleFunction<Object> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    public static Factor apply(final String name, final Factor x, ToDoubleFunction<Object> f) {
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
                return f.applyAsDouble(x.apply(o));
            }

            @Override
            public Object apply(Tuple o) {
                return f.applyAsDouble(x.apply(o));
            }
        };
    }

    /**
     * Returns a factor that applies a lambda on given column.
     * @param name the function name.
     * @param x the column name.
     * @param f the lambda to apply on the column.
     */
    public static Factor apply(final String name, final String x, Function<Object, Object> f) {
        return apply(name, col(x), f);
    }

    /**
     * Returns a factor that applies a lambda on given factor.
     * @param name the function name.
     * @param x the factor.
     * @param f the lambda to apply on the factor.
     */
    public static Factor apply(final String name, final Factor x, Function<Object, Object> f) {
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
            public Object apply(Tuple o) {
                return f.apply(x.apply(o));
            }
        };
    }
}
