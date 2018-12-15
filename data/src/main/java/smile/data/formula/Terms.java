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

import smile.math.MathEx;

/**
 * Predefined terms.
 *
 * @author Haifeng Li
 */
public interface Terms {
    /** Returns a column. */
    static Column $(String column) {
        return new Column(column);
    }

    /** Applies Math.abs. */
    static Abs abs(String column) {
        return abs($(column));
    }

    /** Applies Math.abs. */
    static Abs abs(Factor factor) {
        return new Abs(factor);
    }

    /** Applies Math.ceil. */
    static DoubleFunction ceil(String column) {
        return ceil($(column));
    }

    /** Applies Math.ceil. */
    static DoubleFunction ceil(Factor factor) {
        return new DoubleFunction("ceil", factor, Math::ceil);
    }

    /** Applies Math.floor. */
    static DoubleFunction floor(String column) {
        return floor($(column));
    }

    /** Applies Math.floor. */
    static DoubleFunction floor(Factor factor) {
        return new DoubleFunction("floor", factor, Math::floor);
    }

    /** Applies Math.round. */
    static Round round(String column) {
        return round($(column));
    }

    /** Applies Math.round. */
    static Round round(Factor factor) {
        return new Round(factor);
    }

    /** Applies Math.rint. */
    static DoubleFunction rint(String column) {
        return rint($(column));
    }

    /** Applies Math.rint. */
    static DoubleFunction rint(Factor factor) {
        return new DoubleFunction("rint", factor, Math::rint);
    }

    /** Applies Math.exp. */
    static DoubleFunction exp(String column) {
        return exp($(column));
    }

    /** Applies Math.exp. */
    static DoubleFunction exp(Factor factor) {
        return new DoubleFunction("exp", factor, Math::exp);
    }

    /** Applies Math.expm1. */
    static DoubleFunction expm1(String column) {
        return expm1($(column));
    }

    /** Applies Math.expm1. */
    static DoubleFunction expm1(Factor factor) {
        return new DoubleFunction("expm1", factor, Math::expm1);
    }

    /** Applies Math.log. */
    static DoubleFunction log(String column) {
        return log($(column));
    }

    /** Applies Math.log. */
    static DoubleFunction log(Factor factor) {
        return new DoubleFunction("log", factor, Math::log);
    }

    /** Applies Math.log1p. */
    static DoubleFunction log1p(String column) {
        return log1p($(column));
    }

    /** Applies Math.log1p. */
    static DoubleFunction log1p(Factor factor) {
        return new DoubleFunction("log1p", factor, Math::log1p);
    }

    /** Applies Math.log10. */
    static DoubleFunction log10(String column) {
        return log10($(column));
    }

    /** Applies Math.log10. */
    static DoubleFunction log10(Factor factor) {
        return new DoubleFunction("log10", factor, Math::log10);
    }

    /** Applies MathEx.log2. */
    static DoubleFunction log2(String column) {
        return log2($(column));
    }

    /** Applies MathEx.log2. */
    static DoubleFunction log2(Factor factor) {
        return new DoubleFunction("log2", factor, MathEx::log2);
    }

    /** Applies Math.signum. */
    static DoubleFunction signum(String column) {
        return signum($(column));
    }

    /** Applies Math.signum. */
    static DoubleFunction signum(Factor factor) {
        return new DoubleFunction("signum", factor, Math::signum);
    }

    /** Applies Integer.signum. */
    static IntFunction sign(String column) {
        return sign($(column));
    }

    /** Applies Integer.signum. */
    static IntFunction sign(Factor factor) {
        return new IntFunction("sign", factor, Integer::signum);
    }

    /** Applies Math.sqrt. */
    static DoubleFunction sqrt(String column) {
        return sqrt($(column));
    }

    /** Applies Math.sqrt. */
    static DoubleFunction sqrt(Factor factor) {
        return new DoubleFunction("sqrt", factor, Math::sqrt);
    }

    /** Applies Math.cbrt. */
    static DoubleFunction cbrt(String column) {
        return cbrt($(column));
    }

    /** Applies Math.cbrt. */
    static DoubleFunction cbrt(Factor factor) {
        return new DoubleFunction("cbrt", factor, Math::cbrt);
    }

    /** Applies Math.sin. */
    static DoubleFunction sin(String column) {
        return sin($(column));
    }

    /** Applies Math.sin. */
    static DoubleFunction sin(Factor factor) {
        return new DoubleFunction("sin", factor, Math::sin);
    }

    /** Applies Math.cos. */
    static DoubleFunction cos(String column) {
        return cos($(column));
    }

    /** Applies Math.cos. */
    static DoubleFunction cos(Factor factor) {
        return new DoubleFunction("cos", factor, Math::cos);
    }

    /** Applies Math.tan. */
    static DoubleFunction tan(String column) {
        return tan($(column));
    }

    /** Applies Math.tan. */
    static DoubleFunction tan(Factor factor) {
        return new DoubleFunction("tan", factor, Math::tan);
    }

    /** Applies Math.sinh. */
    static DoubleFunction sinh(String column) {
        return sinh($(column));
    }

    /** Applies Math.sinh. */
    static DoubleFunction sinh(Factor factor) {
        return new DoubleFunction("sinh", factor, Math::sinh);
    }

    /** Applies Math.cosh. */
    static DoubleFunction cosh(String column) {
        return cosh($(column));
    }

    /** Applies Math.cosh. */
    static DoubleFunction cosh(Factor factor) {
        return new DoubleFunction("cosh", factor, Math::cosh);
    }

    /** Applies Math.tanh. */
    static DoubleFunction tanh(String column) {
        return tanh($(column));
    }

    /** Applies Math.tanh. */
    static DoubleFunction tanh(Factor factor) {
        return new DoubleFunction("tanh", factor, Math::tanh);
    }

    /** Applies Math.asin. */
    static DoubleFunction asin(String column) {
        return asin($(column));
    }

    /** Applies Math.asin. */
    static DoubleFunction asin(Factor factor) {
        return new DoubleFunction("asin", factor, Math::asin);
    }

    /** Applies Math.acos. */
    static DoubleFunction acos(String column) {
        return acos($(column));
    }

    /** Applies Math.acos. */
    static DoubleFunction acos(Factor factor) {
        return new DoubleFunction("acos", factor, Math::acos);
    }

    /** Applies Math.atan. */
    static DoubleFunction atan(String column) {
        return atan($(column));
    }

    /** Applies Math.atan. */
    static DoubleFunction atan(Factor factor) {
        return new DoubleFunction("atan", factor, Math::acos);
    }

    /** Applies Math.ulp. */
    static DoubleFunction ulp(String column) {
        return ulp($(column));
    }

    /** Applies Math.ulp. */
    static DoubleFunction ulp(Factor factor) {
        return new DoubleFunction("ulp", factor, Math::ulp);
    }
}
