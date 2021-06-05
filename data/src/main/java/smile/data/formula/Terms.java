/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data.formula;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * Predefined terms.
 *
 * @author Haifeng Li
 */
public interface Terms {
    /**
     * Creates a variable.
     * @param x the variable.
     * @return the term.
     */
    static Term $(String x) {
        x = x.trim();

        switch (x) {
            case ".":
                return new Dot();
            case "0":
                return new Intercept(false);
            case "1":
                return new Intercept(true);
        }

        String[] tokens = x.split(":");
        if (tokens.length > 1) {
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = tokens[i].trim();
            }
            return interact(tokens);
        }

        if (x.startsWith("(") && x.endsWith(")")) {
            String y = x.substring(1, x.length() - 1);
            tokens = y.split("[+]", 2);
            if (tokens.length == 2) {
                return add(tokens[0], tokens[1]);
            }
            tokens = y.split("[-]", 2);
            if (tokens.length == 2) {
                return sub(tokens[0], tokens[1]);
            }
            tokens = y.split("[*]", 2);
            if (tokens.length == 2) {
                return mul(tokens[0], tokens[1]);
            }
            tokens = y.split("[/]", 2);
            if (tokens.length == 2) {
                return div(tokens[0], tokens[1]);
            }
        }

        Pattern regex = Pattern.compile("\\)(^(\\d+))?$");
        Matcher matcher = regex.matcher(x);
        if (x.startsWith("(") && matcher.find()) {
            String y = x.substring(1, matcher.start());
            tokens = y.split(" x ");
            if (tokens.length > 1) {
                for (int i = 0; i < tokens.length; i++) {
                    tokens[i] = tokens[i].trim();
                    System.out.print(tokens[i]+" ");
                }
                System.out.println();
                String rank = matcher.group(2);
                return cross(rank == null ? tokens.length : Integer.parseInt(rank), tokens);
            }
        }

        if (x.startsWith("abs(") && x.endsWith(")")) {
            return abs(x.substring(4, x.length()-1));
        }

        if (x.startsWith("ceil(") && x.endsWith(")")) {
            return ceil(x.substring(5, x.length()-1));
        }

        if (x.startsWith("floor(") && x.endsWith(")")) {
            return floor(x.substring(6, x.length()-1));
        }

        if (x.startsWith("round(") && x.endsWith(")")) {
            return round(x.substring(6, x.length()-1));
        }

        if (x.startsWith("rint(") && x.endsWith(")")) {
            return rint(x.substring(5, x.length()-1));
        }

        if (x.startsWith("exp(") && x.endsWith(")")) {
            return exp(x.substring(4, x.length()-1));
        }

        if (x.startsWith("expm1(") && x.endsWith(")")) {
            return expm1(x.substring(6, x.length()-1));
        }

        if (x.startsWith("log(") && x.endsWith(")")) {
            return log(x.substring(4, x.length()-1));
        }

        if (x.startsWith("log1p(") && x.endsWith(")")) {
            return log1p(x.substring(6, x.length()-1));
        }

        if (x.startsWith("log2(") && x.endsWith(")")) {
            return log2(x.substring(5, x.length()-1));
        }

        if (x.startsWith("log10(") && x.endsWith(")")) {
            return log10(x.substring(6, x.length()-1));
        }

        if (x.startsWith("signum(") && x.endsWith(")")) {
            return signum(x.substring(7, x.length()-1));
        }

        if (x.startsWith("sign(") && x.endsWith(")")) {
            return sign(x.substring(5, x.length()-1));
        }

        if (x.startsWith("sqrt(") && x.endsWith(")")) {
            return sqrt(x.substring(5, x.length()-1));
        }

        if (x.startsWith("cbrt(") && x.endsWith(")")) {
            return cbrt(x.substring(5, x.length()-1));
        }
        if (x.startsWith("sin(") && x.endsWith(")")) {
            return sin(x.substring(4, x.length()-1));
        }

        if (x.startsWith("cos(") && x.endsWith(")")) {
            return cos(x.substring(4, x.length()-1));
        }

        if (x.startsWith("tan(") && x.endsWith(")")) {
            return tan(x.substring(4, x.length()-1));
        }

        if (x.startsWith("asin(") && x.endsWith(")")) {
            return asin(x.substring(5, x.length()-1));
        }

        if (x.startsWith("acos(") && x.endsWith(")")) {
            return acos(x.substring(5, x.length()-1));
        }

        if (x.startsWith("atan(") && x.endsWith(")")) {
            return atan(x.substring(5, x.length()-1));
        }

        if (x.startsWith("sinh(") && x.endsWith(")")) {
            return sinh(x.substring(5, x.length()-1));
        }

        if (x.startsWith("cosh(") && x.endsWith(")")) {
            return cosh(x.substring(5, x.length()-1));
        }

        if (x.startsWith("tanh(") && x.endsWith(")")) {
            return tanh(x.substring(5, x.length()-1));
        }

        if (x.startsWith("ulp(") && x.endsWith(")")) {
            return ulp(x.substring(4, x.length()-1));
        }

        return new Variable(x);
    }

    /**
     * Returns the special term "." that means all columns not otherwise
     * in the formula in the context of a data frame.
     * @return the special term ".".
     */
    static Dot dot() {
        return new Dot();
    }

    /**
     * Factor interaction of two or more factors.
     * @param factors the factors.
     * @return the interaction term.
     */
    static FactorInteraction interact(String... factors) {
        return new FactorInteraction(factors);
    }

    /**
     * Factor crossing of two or more factors.
     * @param factors the factors.
     * @return the crossing term.
     */
    static FactorCrossing cross(String... factors) {
        return new FactorCrossing(factors);
    }

    /**
     * Factor crossing of two or more factors.
     * @param order the order of factor interactions.
     * @param factors the factors.
     * @return the crossing term.
     */
    static FactorCrossing cross(int order, String... factors) {
        return new FactorCrossing(order, factors);
    }

    /**
     * Deletes a variable or the intercept ("1") from the formula.
     * @param x the variable.
     * @return the deleting term.
     */
    static Term delete(String x) {
        if (x.equals("1"))
            return new Intercept(false);
        else
            return delete($(x));
    }

    /**
     * Deletes a term from the formula.
     * @param x the term.
     * @return the deleting term.
     */
    static Term delete(Term x) {
        if (x instanceof Intercept) {
            return new Intercept(false);
        }

        return new Delete(x);
    }

    /**
     * Extracts date/time features.
     * @param x the variable.
     * @param features the date features.
     * @return the date term.
     */
    static Date date(String x, DateFeature... features) {
        return new Date(x, features);
    }

    /**
     * Adds two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a + b} term.
     */
    static Term add(Term a, Term b) {
        return new Add(a, b);
    }

    /**
     * Adds two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a + b} term.
     */
    static Term add(String a, String b) {
        return new Add($(a), $(b));
    }

    /**
     * Adds two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a + b} term.
     */
    static Term add(Term a, String b) {
        return new Add(a, $(b));
    }

    /**
     * Adds two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a + b} term.
     */
    static Term add(String a, Term b) {
        return new Add($(a), b);
    }

    /**
     * Subtracts two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a - b} term.
     */
    static Term sub(Term a, Term b) {
        return new Sub(a, b);
    }

    /**
     * Subtracts two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a - b} term.
     */
    static Term sub(String a, String b) {
        return new Sub($(a), $(b));
    }

    /**
     * Subtracts two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a - b} term.
     */
    static Term sub(Term a, String b) {
        return new Sub(a, $(b));
    }

    /**
     * Subtracts two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a - b} term.
     */
    static Term sub(String a, Term b) {
        return new Sub($(a), b);
    }

    /**
     * Multiplies two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a * b} term.
     */
    static Term mul(Term a, Term b) {
        return new Mul(a, b);
    }

    /**
     * Multiplies two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a * b} term.
     */
    static Term mul(String a, String b) {
        return new Mul($(a), $(b));
    }

    /**
     * Multiplies two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a * b} term.
     */
    static Term mul(Term a, String b) {
        return new Mul(a, $(b));
    }

    /**
     * Multiplies two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a * b} term.
     */
    static Term mul(String a, Term b) {
        return new Mul($(a), b);
    }

    /**
     * Divides two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a / b} term.
     */
    static Term div(Term a, Term b) {
        return new Div(a, b);
    }

    /**
     * Divides two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a / b} term.
     */
    static Term div(String a, String b) {
        return new Div($(a), $(b));
    }

    /**
     * Divides two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a / b} term.
     */
    static Term div(Term a, String b) {
        return new Div(a, $(b));
    }

    /**
     * Divides two terms.
     * @param a the term.
     * @param b the term.
     * @return the {@code a / b} term.
     */
    static Term div(String a, Term b) {
        return new Div($(a), b);
    }

    /**
     * The {@code abs(x)} term.
     * @param x the term.
     * @return the {@code abs(x)} term.
     */
    static Abs abs(String x) {
        return abs($(x));
    }

    /**
     * The {@code abs(x)} term.
     * @param x the term.
     * @return the {@code abs(x)} term.
     */
    static Abs abs(Term x) {
        return new Abs(x);
    }

    /**
     * The {@code ceil(x)} term.
     * @param x the term.
     * @return the {@code ceil(x)} term.
     */
    static DoubleFunction ceil(String x) {
        return ceil($(x));
    }

    /**
     * The {@code ceil(x)} term.
     * @param x the term.
     * @return the {@code ceil(x)} term.
     */
    static DoubleFunction ceil(Term x) {
        return new DoubleFunction("ceil", x, Math::ceil);
    }

    /**
     * The {@code floor(x)} term.
     * @param x the term.
     * @return the {@code floor(x)} term.
     */
    static DoubleFunction floor(String x) {
        return floor($(x));
    }

    /**
     * The {@code floor(x)} term.
     * @param x the term.
     * @return the {@code floor(x)} term.
     */
    static DoubleFunction floor(Term x) {
        return new DoubleFunction("floor", x, Math::floor);
    }

    /**
     * The {@code round(x)} term.
     * @param x the term.
     * @return the {@code round(x)} term.
     */
    static Round round(String x) {
        return round($(x));
    }

    /**
     * The {@code round(x)} term.
     * @param x the term.
     * @return the {@code round(x)} term.
     */
    static Round round(Term x) {
        return new Round(x);
    }

    /**
     * The {@code rint(x)} term.
     * @param x the term.
     * @return the {@code rint(x)} term.
     */
    static DoubleFunction rint(String x) {
        return rint($(x));
    }

    /**
     * The {@code rint(x)} term.
     * @param x the term.
     * @return the {@code rint(x)} term.
     */
    static DoubleFunction rint(Term x) {
        return new DoubleFunction("rint", x, Math::rint);
    }

    /**
     * The {@code exp(x)} term.
     * @param x the term.
     * @return the {@code exp(x)} term.
     */
    static DoubleFunction exp(String x) {
        return exp($(x));
    }

    /**
     * The {@code exp(x)} term.
     * @param x the term.
     * @return the {@code exp(x)} term.
     */
    static DoubleFunction exp(Term x) {
        return new DoubleFunction("exp", x, Math::exp);
    }

    /**
     * The {@code exp(x) - 1} term.
     * @param x the term.
     * @return the {@code exp(x) - 1} term.
     */
    static DoubleFunction expm1(String x) {
        return expm1($(x));
    }

    /**
     * The {@code exp(x) - 1} term.
     * @param x the term.
     * @return the {@code exp(x) - 1} term.
     */
    static DoubleFunction expm1(Term x) {
        return new DoubleFunction("expm1", x, Math::expm1);
    }

    /**
     * The {@code log(x)} term.
     * @param x the term.
     * @return the {@code log(x)} term.
     */
    static DoubleFunction log(String x) {
        return log($(x));
    }

    /**
     * The {@code log(x)} term.
     * @param x the term.
     * @return the {@code log(x)} term.
     */
    static DoubleFunction log(Term x) {
        return new DoubleFunction("log", x, Math::log);
    }

    /**
     * The {@code log(1 + x)} term.
     * @param x the term.
     * @return the {@code log(1 + x)} term.
     */
    static DoubleFunction log1p(String x) {
        return log1p($(x));
    }

    /**
     * The {@code log(1 + x)} term.
     * @param x the term.
     * @return the {@code log(1 + x)} term.
     */
    static DoubleFunction log1p(Term x) {
        return new DoubleFunction("log1p", x, Math::log1p);
    }

    /**
     * The {@code log10(x)} term.
     * @param x the term.
     * @return the {@code log10(x)} term.
     */
    static DoubleFunction log10(String x) {
        return log10($(x));
    }

    /**
     * The {@code log10(x)} term.
     * @param x the term.
     * @return the {@code log10(x)} term.
     */
    static DoubleFunction log10(Term x) {
        return new DoubleFunction("log10", x, Math::log10);
    }

    /**
     * The {@code log2(x)} term.
     * @param x the term.
     * @return the {@code log2(x)} term.
     */
    static DoubleFunction log2(String x) {
        return log2($(x));
    }

    /**
     * The {@code log2(x)} term.
     * @param x the term.
     * @return the {@code log2(x)} term.
     */
    static DoubleFunction log2(Term x) {
        return new DoubleFunction("log2", x, smile.math.MathEx::log2);
    }

    /**
     * The {@code signum(x)} term.
     * @param x the term.
     * @return the {@code signum(x)} term.
     */
    static DoubleFunction signum(String x) {
        return signum($(x));
    }

    /**
     * The {@code signum(x)} term.
     * @param x the term.
     * @return the {@code signum(x)} term.
     */
    static DoubleFunction signum(Term x) {
        return new DoubleFunction("signum", x, Math::signum);
    }

    /**
     * The {@code sign(x)} term.
     * @param x the term.
     * @return the {@code sign(x)} term.
     */
    static IntFunction sign(String x) {
        return sign($(x));
    }

    /**
     * The {@code sign(x)} term.
     * @param x the term.
     * @return the {@code sign(x)} term.
     */
    static IntFunction sign(Term x) {
        return new IntFunction("sign", x, Integer::signum);
    }

    /**
     * The {@code sqrt(x)} term.
     * @param x the term.
     * @return the {@code sqrt(x)} term.
     */
    static DoubleFunction sqrt(String x) {
        return sqrt($(x));
    }

    /**
     * The {@code sqrt(x)} term.
     * @param x the term.
     * @return the {@code sqrt(x)} term.
     */
    static DoubleFunction sqrt(Term x) {
        return new DoubleFunction("sqrt", x, Math::sqrt);
    }

    /**
     * The {@code cbrt(x)} term.
     * @param x the term.
     * @return the {@code cbrt(x)} term.
     */
    static DoubleFunction cbrt(String x) {
        return cbrt($(x));
    }

    /**
     * The {@code cbrt(x)} term.
     * @param x the term.
     * @return the {@code cbrt(x)} term.
     */
    static DoubleFunction cbrt(Term x) {
        return new DoubleFunction("cbrt", x, Math::cbrt);
    }

    /**
     * The {@code sin(x)} term.
     * @param x the term.
     * @return the {@code sin(x)} term.
     */
    static DoubleFunction sin(String x) {
        return sin($(x));
    }

    /**
     * The {@code sin(x)} term.
     * @param x the term.
     * @return the {@code sin(x)} term.
     */
    static DoubleFunction sin(Term x) {
        return new DoubleFunction("sin", x, Math::sin);
    }

    /**
     * The {@code cos(x)} term.
     * @param x the term.
     * @return the {@code cos(x)} term.
     */
    static DoubleFunction cos(String x) {
        return cos($(x));
    }

    /**
     * The {@code cos(x)} term.
     * @param x the term.
     * @return the {@code cos(x)} term.
     */
    static DoubleFunction cos(Term x) {
        return new DoubleFunction("cos", x, Math::cos);
    }

    /**
     * The {@code tan(x)} term.
     * @param x the term.
     * @return the {@code tan(x)} term.
     */
    static DoubleFunction tan(String x) {
        return tan($(x));
    }

    /**
     * The {@code tan(x)} term.
     * @param x the term.
     * @return the {@code tan(x)} term.
     */
    static DoubleFunction tan(Term x) {
        return new DoubleFunction("tan", x, Math::tan);
    }

    /**
     * The {@code sinh(x)} term.
     * @param x the term.
     * @return the {@code sinh(x)} term.
     */
    static DoubleFunction sinh(String x) {
        return sinh($(x));
    }

    /**
     * The {@code sinh(x)} term.
     * @param x the term.
     * @return the {@code sinh(x)} term.
     */
    static DoubleFunction sinh(Term x) {
        return new DoubleFunction("sinh", x, Math::sinh);
    }

    /**
     * The {@code cosh(x)} term.
     * @param x the term.
     * @return the {@code cosh(x)} term.
     */
    static DoubleFunction cosh(String x) {
        return cosh($(x));
    }

    /**
     * The {@code cosh(x)} term.
     * @param x the term.
     * @return the {@code cosh(x)} term.
     */
    static DoubleFunction cosh(Term x) {
        return new DoubleFunction("cosh", x, Math::cosh);
    }

    /**
     * The {@code tanh(x)} term.
     * @param x the term.
     * @return the {@code tanh(x)} term.
     */
    static DoubleFunction tanh(String x) {
        return tanh($(x));
    }

    /**
     * The {@code tanh(x)} term.
     * @param x the term.
     * @return the {@code tanh(x)} term.
     */
    static DoubleFunction tanh(Term x) {
        return new DoubleFunction("tanh", x, Math::tanh);
    }

    /**
     * The {@code asin(x)} term.
     * @param x the term.
     * @return the {@code asin(x)} term.
     */
    static DoubleFunction asin(String x) {
        return asin($(x));
    }

    /**
     * The {@code asin(x)} term.
     * @param x the term.
     * @return the {@code asin(x)} term.
     */
    static DoubleFunction asin(Term x) {
        return new DoubleFunction("asin", x, Math::asin);
    }

    /**
     * The {@code acos(x)} term.
     * @param x the term.
     * @return the {@code acos(x)} term.
     */
    static DoubleFunction acos(String x) {
        return acos($(x));
    }

    /**
     * The {@code acos(x)} term.
     * @param x the term.
     * @return the {@code acos(x)} term.
     */
    static DoubleFunction acos(Term x) {
        return new DoubleFunction("acos", x, Math::acos);
    }

    /**
     * The {@code atan(x)} term.
     * @param x the term.
     * @return the {@code atan(x)} term.
     */
    static DoubleFunction atan(String x) {
        return atan($(x));
    }

    /**
     * The {@code atan(x)} term.
     * @param x the term.
     * @return the {@code atan(x)} term.
     */
    static DoubleFunction atan(Term x) {
        return new DoubleFunction("atan", x, Math::acos);
    }

    /**
     * The {@code ulp(x)} term.
     * @param x the term.
     * @return the {@code ulp(x)} term.
     */
    static DoubleFunction ulp(String x) {
        return ulp($(x));
    }

    /**
     * The {@code ulp(x)} term.
     * @param x the term.
     * @return the {@code ulp(x)} term.
     */
    static DoubleFunction ulp(Term x) {
        return new DoubleFunction("ulp", x, Math::ulp);
    }

    /**
     * Returns a constant boolean term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final boolean x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.BooleanType, null);

                    @Override
                    public StructField field() {
                        return field;
                    }

                    @Override
                    public boolean applyAsBoolean(Tuple o) {
                        return x;
                    }

                    @Override
                    public Object apply(Tuple o) {
                        return x;
                    }
                };

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant char term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final char x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.CharType, null);

                    @Override
                    public StructField field() {
                        return field;
                    }

                    @Override
                    public char applyAsChar(Tuple o) {
                        return x;
                    }

                    @Override
                    public Object apply(Tuple o) {
                        return x;
                    }
                };

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant byte term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final byte x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.ByteType, null);

                    @Override
                    public StructField field() {
                        return field;
                    }

                    @Override
                    public byte applyAsByte(Tuple o) {
                        return x;
                    }

                    @Override
                    public short applyAsShort(Tuple o) {
                        return x;
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

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant short integer term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final short x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.ShortType, null);

                    @Override
                    public StructField field() {
                        return field;
                    }

                    @Override
                    public short applyAsShort(Tuple o) {
                        return x;
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

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant integer term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final int x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.IntegerType, null);

                    @Override
                    public StructField field() {
                        return field;
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

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant long integer term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final long x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.LongType, null);

                    @Override
                    public StructField field() {
                        return field;
                    }

                    @Override
                    public long applyAsLong(Tuple o) {
                        return x;
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

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant single precision floating number term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final float x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.FloatType, null);

                    @Override
                    public StructField field() {
                        return field;
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

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant double precision floating number term.
     * @param x the value.
     * @return the constant value term.
     */
    static Term val(final double x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataTypes.DoubleType, null);

                    @Override
                    public StructField field() {
                        return field;
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

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a constant object term.
     * @param x the object.
     * @return the constant object term.
     */
    static Term val(final Object x) {
        return new Constant() {
            @Override
            public String toString() {
                return String.valueOf(x);
            }

            @Override
            public List<Feature> bind(StructType schema) {
                Feature feature = new Feature() {
                    private final StructField field = new StructField(String.valueOf(x), DataType.of(x.getClass()), null);

                    @Override
                    public StructField field() {
                        return field;
                    }

                    @Override
                    public Object apply(Tuple o) {
                        return x;
                    }
                };

                return Collections.singletonList(feature);
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param f the lambda to apply on the variable.
     * @param <T> the data type of input term.
     * @return the term.
     */
    static <T> Term of(final String name, final String x, ToIntFunction<T> f) {
        return of(name, $(x), f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param f the lambda to apply on the term.
     * @param <T> the data type of input term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T> Term of(final String name, final Term x, ToIntFunction<T> f) {
        return new AbstractFunction(name, x) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();

                for (Feature feature : x.bind(schema)) {
                    features.add(new Feature() {
                        private final StructField field = new StructField(String.format("%s(%s)", name, feature), DataTypes.IntegerType, null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public int applyAsInt(Tuple o) {
                            return f.applyAsInt((T) feature.apply(o));
                        }

                        @Override
                        public long applyAsLong(Tuple o) {
                            return f.applyAsInt((T) feature.apply(o));
                        }

                        @Override
                        public float applyAsFloat(Tuple o) {
                            return f.applyAsInt((T) feature.apply(o));
                        }

                        @Override
                        public double applyAsDouble(Tuple o) {
                            return f.applyAsInt((T) feature.apply(o));
                        }

                        @Override
                        public Object apply(Tuple o) {
                            return f.applyAsInt((T) feature.apply(o));
                        }
                    });
                }

                return features;
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param f the lambda to apply on the variable.
     * @param <T> the data type of input term.
     * @return the term.
     */
    static <T> Term of(final String name, final String x, ToLongFunction<T> f) {
        return of(name, $(x), f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param f the lambda to apply on the term.
     * @param <T> the data type of input term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T> Term of(final String name, final Term x, ToLongFunction<T> f) {
        return new AbstractFunction(name, x) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();

                for (Feature feature : x.bind(schema)) {
                    features.add(new Feature() {
                        private final StructField field = new StructField(String.format("%s(%s)", name, feature), DataTypes.LongType, null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public long applyAsLong(Tuple o) {
                            return f.applyAsLong((T) feature.apply(o));
                        }

                        @Override
                        public float applyAsFloat(Tuple o) {
                            return f.applyAsLong((T) feature.apply(o));
                        }

                        @Override
                        public double applyAsDouble(Tuple o) {
                            return f.applyAsLong((T) feature.apply(o));
                        }

                        @Override
                        public Object apply(Tuple o) {
                            return f.applyAsLong((T) feature.apply(o));
                        }
                    });
                }

                return features;
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param f the lambda to apply on the variable.
     * @param <T> the data type of input term.
     * @return the term.
     */
    static <T> Term of(final String name, final String x, ToDoubleFunction<T> f) {
        return of(name, $(x), f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param f the lambda to apply on the term.
     * @param <T> the data type of input term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T> Term of(final String name, final Term x, ToDoubleFunction<T> f) {
        return new AbstractFunction(name, x) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();

                for (Feature feature : x.bind(schema)) {
                    features.add(new Feature() {
                        private final StructField field = new StructField(String.format("%s(%s)", name, feature), DataTypes.DoubleType, null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public double applyAsDouble(Tuple o) {
                            return f.applyAsDouble((T) feature.apply(o));
                        }

                        @Override
                        public Object apply(Tuple o) {
                            return f.applyAsDouble((T) feature.apply(o));
                        }
                    });
                }

                return features;
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variable.
     * @param name the function name.
     * @param x the variable name.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the variable.
     * @param <T> the data type of input term.
     * @param <R> the data type of output term.
     * @return the term.
     */
    static <T, R> Term of(final String name, final String x, final Class<R> clazz, java.util.function.Function<T, R> f) {
        return of(name, $(x), clazz, f);
    }

    /**
     * Returns a term that applies a lambda on given term.
     * @param name the function name.
     * @param x the term.
     * @param clazz the class of return object.
     * @param f the lambda to apply on the term.
     * @param <T> the data type of input term.
     * @param <R> the data type of output term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T, R> Term of(final String name, final Term x, final Class<R> clazz, java.util.function.Function<T, R> f) {
        return new AbstractFunction(name, x) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();

                for (Feature feature : x.bind(schema)) {
                    features.add(new Feature() {
                        private final StructField field = new StructField(String.format("%s(%s)", name, feature), DataTypes.object(clazz), null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public Object apply(Tuple o) {
                            return f.apply((T) feature.apply(o));
                        }
                    });
                }

                return features;
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variables.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the variables.
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @return the term.
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
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T, U> Term of(final String name, final Term x, final Term y, ToIntBiFunction<T, U> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();
                List<Feature> xfeatures = x.bind(schema);
                List<Feature> yfeatures = y.bind(schema);
                if (xfeatures.size() != yfeatures.size()) {
                    throw new IllegalStateException(String.format("The features of %s and %s are of different size: %d != %d", x, y, xfeatures.size(), yfeatures.size()));
                }

                for (int i = 0; i < xfeatures.size(); i++) {
                    Feature a = xfeatures.get(i);
                    StructField xfield = a.field();
                    Feature b = yfeatures.get(i);
                    StructField yfield = b.field();

                    features.add(new Feature() {
                        final StructField field = new StructField(String.format("%s(%s, %s)", name, xfield.name, yfield.name),
                                DataTypes.IntegerType,
                                null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public int applyAsInt(Tuple o) {
                            return f.applyAsInt((T) a.apply(o), (U) b.apply(o));
                        }

                        @Override
                        public Object apply(Tuple o) {
                            Object x = a.apply(o);
                            Object y = b.apply(o);
                            if (x == null || y == null) return null;
                            else return f.applyAsInt((T) a.apply(o), (U) b.apply(o));
                        }
                    });
                }

                return features;
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variables.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the variables.
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @return the term.
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
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T, U> Term of(final String name, final Term x, final Term y, ToLongBiFunction<T, U> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();
                List<Feature> xfeatures = x.bind(schema);
                List<Feature> yfeatures = y.bind(schema);
                if (xfeatures.size() != yfeatures.size()) {
                    throw new IllegalStateException(String.format("The features of %s and %s are of different size: %d != %d", x, y, xfeatures.size(), yfeatures.size()));
                }

                for (int i = 0; i < xfeatures.size(); i++) {
                    Feature a = xfeatures.get(i);
                    StructField xfield = a.field();
                    Feature b = yfeatures.get(i);
                    StructField yfield = b.field();

                    features.add(new Feature() {
                        final StructField field = new StructField(String.format("%s(%s, %s)", name, xfield.name, yfield.name),
                                DataTypes.LongType,
                                null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public long applyAsLong(Tuple o) {
                            return f.applyAsLong((T) a.apply(o), (U) b.apply(o));
                        }

                        @Override
                        public Object apply(Tuple o) {
                            Object x = a.apply(o);
                            Object y = b.apply(o);
                            if (x == null || y == null) return null;
                            else return f.applyAsLong((T) a.apply(o), (U) b.apply(o));
                        }
                    });
                }

                return features;
            }
        };
    }

    /**
     * Returns a term that applies a lambda on given variables.
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     * @param f the lambda to apply on the variables.
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @return the term.
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
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T, U> Term of(final String name, final Term x, final Term y, ToDoubleBiFunction<T, U> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();
                List<Feature> xfeatures = x.bind(schema);
                List<Feature> yfeatures = y.bind(schema);
                if (xfeatures.size() != yfeatures.size()) {
                    throw new IllegalStateException(String.format("The features of %s and %s are of different size: %d != %d", x, y, xfeatures.size(), yfeatures.size()));
                }

                for (int i = 0; i < xfeatures.size(); i++) {
                    Feature a = xfeatures.get(i);
                    StructField xfield = a.field();
                    Feature b = yfeatures.get(i);
                    StructField yfield = b.field();

                    features.add(new Feature() {
                        final StructField field = new StructField(String.format("%s(%s, %s)", name, xfield.name, yfield.name),
                                DataTypes.DoubleType,
                                null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public double applyAsDouble(Tuple o) {
                            return f.applyAsDouble((T) a.apply(o), (U) b.apply(o));
                        }

                        @Override
                        public Object apply(Tuple o) {
                            Object x = a.apply(o);
                            Object y = b.apply(o);
                            if (x == null || y == null) return null;
                            else return f.applyAsDouble((T) a.apply(o), (U) b.apply(o));
                        }
                    });
                }

                return features;
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
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @param <R> the data type of output term.
     * @return the term.
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
     * @param <T> the data type of first input term.
     * @param <U> the data type of second input term.
     * @param <R> the data type of output term.
     * @return the term.
     */
    @SuppressWarnings("unchecked")
    static <T, U, R> Term of(final String name, final Term x, final Term y, final Class<R> clazz, BiFunction<T, U, R> f) {
        return new AbstractBiFunction(name, x, y) {
            @Override
            public List<Feature> bind(StructType schema) {
                List<Feature> features = new ArrayList<>();
                List<Feature> xfeatures = x.bind(schema);
                List<Feature> yfeatures = y.bind(schema);
                if (xfeatures.size() != yfeatures.size()) {
                    throw new IllegalStateException(String.format("The features of %s and %s are of different size: %d != %d", x, y, xfeatures.size(), yfeatures.size()));
                }

                for (int i = 0; i < xfeatures.size(); i++) {
                    Feature a = xfeatures.get(i);
                    StructField xfield = a.field();
                    Feature b = yfeatures.get(i);
                    StructField yfield = b.field();

                    features.add(new Feature() {
                        final StructField field = new StructField(String.format("%s(%s, %s)", name, xfield.name, yfield.name),
                                DataTypes.object(clazz),
                                null);

                        @Override
                        public StructField field() {
                            return field;
                        }

                        @Override
                        public Object apply(Tuple o) {
                            Object x = a.apply(o);
                            Object y = b.apply(o);
                            if (x == null || y == null) return null;
                            else return f.apply((T) a.apply(o), (U) b.apply(o));
                        }
                    });
                }

                return features;
            }
        };
    }
}
