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

import java.util.function.*;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;

/**
 * Predefined terms.
 *
 * @author Haifeng Li
 */
public interface Terms {
    /** Returns a variable. */
    static Variable $(String x) {
        return new Variable(x);
    }

    /** Returns all columns not otherwise in the formula. */
    static All all() {
        return new All();
    }

    /** Factor interaction of two or more factors. */
    static FactorInteraction interact(String... factors) {
        return new FactorInteraction(factors);
    }

    /** Factor crossing of two or more factors. */
    static FactorCrossing cross(String... factors) {
        return new FactorCrossing(factors);
    }

    /** Factor crossing of two or more factors. */
    static FactorCrossing cross(int order, String... factors) {
        return new FactorCrossing(order, factors);
    }

    /** Deletes a variable from the formula. */
    static Delete delete(String x) {
        return delete($(x));
    }

    /** Deletes a term from the formula. */
    static Delete delete(Term x) {
        return new Delete(x);
    }

    /** One-hot encoding of nominal factors. */
    static OneHot onehot(String... factors) {
        return new OneHot(factors);
    }

    /** Extracts date/time features. */
    static Date date(String x, DateFeature... features) {
        return new Date(x, features);
    }

    /** Adds two terms. */
    static Term add(Term a, Term b) {
        return new Add(a, b);
    }

    /** Adds two terms. */
    static Term add(String a, String b) {
        return new Add($(a), $(b));
    }

    /** Adds two terms. */
    static Term add(Term a, String b) {
        return new Add(a, $(b));
    }

    /** Adds two terms. */
    static Term add(String a, Term b) {
        return new Add($(a), b);
    }

    /** Subtracts two terms. */
    static Term sub(Term a, Term b) {
        return new Sub(a, b);
    }

    /** Subtracts two terms. */
    static Term sub(String a, String b) {
        return new Sub($(a), $(b));
    }

    /** Subtracts two terms. */
    static Term sub(Term a, String b) {
        return new Sub(a, $(b));
    }

    /** Subtracts two terms. */
    static Term sub(String a, Term b) {
        return new Sub($(a), b);
    }

    /** Multiplies two terms. */
    static Term mul(Term a, Term b) {
        return new Mul(a, b);
    }

    /** Multiplies two terms. */
    static Term mul(String a, String b) {
        return new Mul($(a), $(b));
    }

    /** Multiplies two terms. */
    static Term mul(Term a, String b) {
        return new Mul(a, $(b));
    }

    /** Multiplies two terms. */
    static Term mul(String a, Term b) {
        return new Mul($(a), b);
    }

    /** Divides two terms. */
    static Term div(Term a, Term b) {
        return new Div(a, b);
    }

    /** Divides two terms. */
    static Term div(String a, String b) {
        return new Div($(a), $(b));
    }

    /** Divides two terms. */
    static Term div(Term a, String b) {
        return new Div(a, $(b));
    }

    /** Divides two terms. */
    static Term div(String a, Term b) {
        return new Div($(a), b);
    }
    
    /** Applies Math.abs. */
    static Abs abs(String x) {
        return abs($(x));
    }

    /** Applies Math.abs. */
    static Abs abs(Term x) {
        return new Abs(x);
    }

    /** Applies Math.ceil. */
    static DoubleFunction ceil(String x) {
        return ceil($(x));
    }

    /** Applies Math.ceil. */
    static DoubleFunction ceil(Term x) {
        return new DoubleFunction("ceil", x, Math::ceil);
    }

    /** Applies Math.floor. */
    static DoubleFunction floor(String x) {
        return floor($(x));
    }

    /** Applies Math.floor. */
    static DoubleFunction floor(Term x) {
        return new DoubleFunction("floor", x, Math::floor);
    }

    /** Applies Math.round. */
    static Round round(String x) {
        return round($(x));
    }

    /** Applies Math.round. */
    static Round round(Term x) {
        return new Round(x);
    }

    /** Applies Math.rint. */
    static DoubleFunction rint(String x) {
        return rint($(x));
    }

    /** Applies Math.rint. */
    static DoubleFunction rint(Term x) {
        return new DoubleFunction("rint", x, Math::rint);
    }

    /** Applies Math.exp. */
    static DoubleFunction exp(String x) {
        return exp($(x));
    }

    /** Applies Math.exp. */
    static DoubleFunction exp(Term x) {
        return new DoubleFunction("exp", x, Math::exp);
    }

    /** Applies Math.expm1. */
    static DoubleFunction expm1(String x) {
        return expm1($(x));
    }

    /** Applies Math.expm1. */
    static DoubleFunction expm1(Term x) {
        return new DoubleFunction("expm1", x, Math::expm1);
    }

    /** Applies Math.log. */
    static DoubleFunction log(String x) {
        return log($(x));
    }

    /** Applies Math.log. */
    static DoubleFunction log(Term x) {
        return new DoubleFunction("log", x, Math::log);
    }

    /** Applies Math.log1p. */
    static DoubleFunction log1p(String x) {
        return log1p($(x));
    }

    /** Applies Math.log1p. */
    static DoubleFunction log1p(Term x) {
        return new DoubleFunction("log1p", x, Math::log1p);
    }

    /** Applies Math.log10. */
    static DoubleFunction log10(String x) {
        return log10($(x));
    }

    /** Applies Math.log10. */
    static DoubleFunction log10(Term x) {
        return new DoubleFunction("log10", x, Math::log10);
    }

    /** Applies MathEx.log2. */
    static DoubleFunction log2(String x) {
        return log2($(x));
    }

    /** Applies MathEx.log2. */
    static DoubleFunction log2(Term x) {
        return new DoubleFunction("log2", x, smile.math.MathEx::log2);
    }

    /** Applies Math.signum. */
    static DoubleFunction signum(String x) {
        return signum($(x));
    }

    /** Applies Math.signum. */
    static DoubleFunction signum(Term x) {
        return new DoubleFunction("signum", x, Math::signum);
    }

    /** Applies Integer.signum. */
    static IntFunction sign(String x) {
        return sign($(x));
    }

    /** Applies Integer.signum. */
    static IntFunction sign(Term x) {
        return new IntFunction("sign", x, Integer::signum);
    }

    /** Applies Math.sqrt. */
    static DoubleFunction sqrt(String x) {
        return sqrt($(x));
    }

    /** Applies Math.sqrt. */
    static DoubleFunction sqrt(Term x) {
        return new DoubleFunction("sqrt", x, Math::sqrt);
    }

    /** Applies Math.cbrt. */
    static DoubleFunction cbrt(String x) {
        return cbrt($(x));
    }

    /** Applies Math.cbrt. */
    static DoubleFunction cbrt(Term x) {
        return new DoubleFunction("cbrt", x, Math::cbrt);
    }

    /** Applies Math.sin. */
    static DoubleFunction sin(String x) {
        return sin($(x));
    }

    /** Applies Math.sin. */
    static DoubleFunction sin(Term x) {
        return new DoubleFunction("sin", x, Math::sin);
    }

    /** Applies Math.cos. */
    static DoubleFunction cos(String x) {
        return cos($(x));
    }

    /** Applies Math.cos. */
    static DoubleFunction cos(Term x) {
        return new DoubleFunction("cos", x, Math::cos);
    }

    /** Applies Math.tan. */
    static DoubleFunction tan(String x) {
        return tan($(x));
    }

    /** Applies Math.tan. */
    static DoubleFunction tan(Term x) {
        return new DoubleFunction("tan", x, Math::tan);
    }

    /** Applies Math.sinh. */
    static DoubleFunction sinh(String x) {
        return sinh($(x));
    }

    /** Applies Math.sinh. */
    static DoubleFunction sinh(Term x) {
        return new DoubleFunction("sinh", x, Math::sinh);
    }

    /** Applies Math.cosh. */
    static DoubleFunction cosh(String x) {
        return cosh($(x));
    }

    /** Applies Math.cosh. */
    static DoubleFunction cosh(Term x) {
        return new DoubleFunction("cosh", x, Math::cosh);
    }

    /** Applies Math.tanh. */
    static DoubleFunction tanh(String x) {
        return tanh($(x));
    }

    /** Applies Math.tanh. */
    static DoubleFunction tanh(Term x) {
        return new DoubleFunction("tanh", x, Math::tanh);
    }

    /** Applies Math.asin. */
    static DoubleFunction asin(String x) {
        return asin($(x));
    }

    /** Applies Math.asin. */
    static DoubleFunction asin(Term x) {
        return new DoubleFunction("asin", x, Math::asin);
    }

    /** Applies Math.acos. */
    static DoubleFunction acos(String x) {
        return acos($(x));
    }

    /** Applies Math.acos. */
    static DoubleFunction acos(Term x) {
        return new DoubleFunction("acos", x, Math::acos);
    }

    /** Applies Math.atan. */
    static DoubleFunction atan(String x) {
        return atan($(x));
    }

    /** Applies Math.atan. */
    static DoubleFunction atan(Term x) {
        return new DoubleFunction("atan", x, Math::acos);
    }

    /** Applies Math.ulp. */
    static DoubleFunction ulp(String x) {
        return ulp($(x));
    }

    /** Applies Math.ulp. */
    static DoubleFunction ulp(Term x) {
        return new DoubleFunction("ulp", x, Math::ulp);
    }

    /** Returns a constant boolean term. */
    static Term val(final boolean x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.BooleanType;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /** Returns a constant char term. */
    static Term val(final char x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.CharType;
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

    /** Returns a constant byte term. */
    static Term val(final byte x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.ByteType;
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

    /** Returns a constant short term. */
    static Term val(final short x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.ShortType;
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

    /** Returns a constant integer term. */
    static Term val(final int x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.IntegerType;
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

    /** Returns a constant long term. */
    static Term val(final long x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.LongType;
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

    /** Returns a constant float term. */
    static Term val(final float x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.FloatType;
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

    /** Returns a constant double term. */
    static Term val(final double x) {
        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return String.valueOf(x);
            }

            @Override
            public DataType type() {
                return DataTypes.DoubleType;
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

    /** Returns a constant object term. */
    static Term val(final Object x) {
        final DataType type = DataType.of(x.getClass());

        return new Constant() {
            @Override
            public String toString() {
                return name();
            }

            @Override
            public String name() {
                return x.toString();
            }

            @Override
            public DataType type() {
                return type;
            }

            @Override
            public Object apply(Tuple o) {
                return x;
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param f the lambda to apply on the variable.
     */
    static <T> Term of(final String name, final String x, ToIntFunction<T> f) {
        return of(name, $(x), f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param f the lambda to apply on the term.
     */
    @SuppressWarnings("unchecked")
    static <T> Term of(final String name, final Term x, ToIntFunction<T> f) {
        return new AbstractFunction(name, x) {
            @Override
            public DataType type() {
                return DataTypes.IntegerType;
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
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param f the lambda to apply on the variable.
     */
    static <T> Term of(final String name, final String x, ToLongFunction<T> f) {
        return of(name, $(x), f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param f the lambda to apply on the term.
     */
    @SuppressWarnings("unchecked")
    static <T> Term of(final String name, final Term x, ToLongFunction<T> f) {
        return new AbstractFunction(name, x) {
            @Override
            public DataType type() {
                return DataTypes.LongType;
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
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param f the lambda to apply on the variable.
     */
    static <T> Term of(final String name, final String x, ToDoubleFunction<T> f) {
        return of(name, $(x), f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param f the lambda to apply on the term.
     */
    @SuppressWarnings("unchecked")
    static <T> Term of(final String name, final Term x, ToDoubleFunction<T> f) {
        return new AbstractFunction(name, x) {
            @Override
            public DataType type() {
                return DataTypes.DoubleType;
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
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the variable.
     */
    static <T, R> Term of(final String name, final String x, final Class<R> clazz, java.util.function.Function f) {
        return of(name, $(x), clazz, f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the term.
     */
    @SuppressWarnings("unchecked")
    static <T, R> Term of(final String name, final Term x, final Class<R> clazz, java.util.function.Function f) {
        return new AbstractFunction(name, x) {
            @Override
            public DataType type() {
                return DataTypes.object(clazz);
            }

            @Override
            public Object apply(Tuple o) {
                return f.apply((T) x.apply(o));
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variables.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the variables.
     */
    static <T, U> Term of(final String name, final String x, final String y, ToIntBiFunction<T, U> f) {
        return of(name, $(x), $(y), f);
    }

    /**
     * Returns a term that applies a lambda on given terms.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the terms.
     */
    @SuppressWarnings("unchecked")
    static <T, U> Term of(final String name, final Term x, final Term y, ToIntBiFunction<T, U> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public DataType type() {
                return DataTypes.IntegerType;
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
     * Returns a term that applies a lambda on given variables.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the variables.
     */
    static <T, U> Term of(final String name, final String x, final String y, ToLongBiFunction<T, U> f) {
        return of(name, $(x), $(y), f);
    }

    /**
     * Returns a term that applies a lambda on given terms.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the terms.
     */
    @SuppressWarnings("unchecked")
    static <T, U> Term of(final String name, final Term x, final Term y, ToLongBiFunction<T, U> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public DataType type() {
                return DataTypes.LongType;
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
     * Returns a term that applies a lambda on given variables.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the variables.
     */
    static <T, U> Term of(final String name, final String x, final String y, ToDoubleBiFunction<T, U> f) {
        return of(name, $(x), $(y), f);
    }

    /**
     * Returns a term that applies a lambda on given terms.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the terms.
     */
    @SuppressWarnings("unchecked")
    static <T, U> Term of(final String name, final Term x, final Term y, ToDoubleBiFunction<T, U> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public DataType type() {
                return DataTypes.DoubleType;
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
     * Returns a term that applies a lambda on given variables.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the variables.
     */
    static <T, U, R> Term of(final String name, final String x, final String y, final Class<R> clazz, BiFunction<T, U, R> f) {
        return of(name, $(x), $(y), clazz, f);
    }

    /**
     * Returns a term that applies a lambda on given terms.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the terms.
     */
    @SuppressWarnings("unchecked")
    static <T, U, R> Term of(final String name, final Term x, final Term y, final Class<R> clazz, BiFunction<T, U, R> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public DataType type() {
                return DataTypes.object(clazz);
            }

            @Override
            public Object apply(Tuple o) {
                return f.apply((T) x.apply(o), (U) y.apply(o));
            }
        };
    }
}
