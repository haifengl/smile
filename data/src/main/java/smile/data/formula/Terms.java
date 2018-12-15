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
    /** Returns a variable. */
    static Variable $(String x) {
        return new Variable(x);
    }

    /** Applies Math.abs. */
    static Abs abs(String x) {
        return abs($(x));
    }

    /** Applies Math.abs. */
    static Abs abs(Function x) {
        return new Abs(x);
    }

    /** Applies Math.ceil. */
    static DoubleFunction ceil(String x) {
        return ceil($(x));
    }

    /** Applies Math.ceil. */
    static DoubleFunction ceil(Function x) {
        return new DoubleFunction("ceil", x, Math::ceil);
    }

    /** Applies Math.floor. */
    static DoubleFunction floor(String x) {
        return floor($(x));
    }

    /** Applies Math.floor. */
    static DoubleFunction floor(Function x) {
        return new DoubleFunction("floor", x, Math::floor);
    }

    /** Applies Math.round. */
    static Round round(String x) {
        return round($(x));
    }

    /** Applies Math.round. */
    static Round round(Function x) {
        return new Round(x);
    }

    /** Applies Math.rint. */
    static DoubleFunction rint(String x) {
        return rint($(x));
    }

    /** Applies Math.rint. */
    static DoubleFunction rint(Function x) {
        return new DoubleFunction("rint", x, Math::rint);
    }

    /** Applies Math.exp. */
    static DoubleFunction exp(String x) {
        return exp($(x));
    }

    /** Applies Math.exp. */
    static DoubleFunction exp(Function x) {
        return new DoubleFunction("exp", x, Math::exp);
    }

    /** Applies Math.expm1. */
    static DoubleFunction expm1(String x) {
        return expm1($(x));
    }

    /** Applies Math.expm1. */
    static DoubleFunction expm1(Function x) {
        return new DoubleFunction("expm1", x, Math::expm1);
    }

    /** Applies Math.log. */
    static DoubleFunction log(String x) {
        return log($(x));
    }

    /** Applies Math.log. */
    static DoubleFunction log(Function x) {
        return new DoubleFunction("log", x, Math::log);
    }

    /** Applies Math.log1p. */
    static DoubleFunction log1p(String x) {
        return log1p($(x));
    }

    /** Applies Math.log1p. */
    static DoubleFunction log1p(Function x) {
        return new DoubleFunction("log1p", x, Math::log1p);
    }

    /** Applies Math.log10. */
    static DoubleFunction log10(String x) {
        return log10($(x));
    }

    /** Applies Math.log10. */
    static DoubleFunction log10(Function x) {
        return new DoubleFunction("log10", x, Math::log10);
    }

    /** Applies MathEx.log2. */
    static DoubleFunction log2(String x) {
        return log2($(x));
    }

    /** Applies MathEx.log2. */
    static DoubleFunction log2(Function x) {
        return new DoubleFunction("log2", x, MathEx::log2);
    }

    /** Applies Math.signum. */
    static DoubleFunction signum(String x) {
        return signum($(x));
    }

    /** Applies Math.signum. */
    static DoubleFunction signum(Function x) {
        return new DoubleFunction("signum", x, Math::signum);
    }

    /** Applies Integer.signum. */
    static IntFunction sign(String x) {
        return sign($(x));
    }

    /** Applies Integer.signum. */
    static IntFunction sign(Function x) {
        return new IntFunction("sign", x, Integer::signum);
    }

    /** Applies Math.sqrt. */
    static DoubleFunction sqrt(String x) {
        return sqrt($(x));
    }

    /** Applies Math.sqrt. */
    static DoubleFunction sqrt(Function x) {
        return new DoubleFunction("sqrt", x, Math::sqrt);
    }

    /** Applies Math.cbrt. */
    static DoubleFunction cbrt(String x) {
        return cbrt($(x));
    }

    /** Applies Math.cbrt. */
    static DoubleFunction cbrt(Function x) {
        return new DoubleFunction("cbrt", x, Math::cbrt);
    }

    /** Applies Math.sin. */
    static DoubleFunction sin(String x) {
        return sin($(x));
    }

    /** Applies Math.sin. */
    static DoubleFunction sin(Function x) {
        return new DoubleFunction("sin", x, Math::sin);
    }

    /** Applies Math.cos. */
    static DoubleFunction cos(String x) {
        return cos($(x));
    }

    /** Applies Math.cos. */
    static DoubleFunction cos(Function x) {
        return new DoubleFunction("cos", x, Math::cos);
    }

    /** Applies Math.tan. */
    static DoubleFunction tan(String x) {
        return tan($(x));
    }

    /** Applies Math.tan. */
    static DoubleFunction tan(Function x) {
        return new DoubleFunction("tan", x, Math::tan);
    }

    /** Applies Math.sinh. */
    static DoubleFunction sinh(String x) {
        return sinh($(x));
    }

    /** Applies Math.sinh. */
    static DoubleFunction sinh(Function x) {
        return new DoubleFunction("sinh", x, Math::sinh);
    }

    /** Applies Math.cosh. */
    static DoubleFunction cosh(String x) {
        return cosh($(x));
    }

    /** Applies Math.cosh. */
    static DoubleFunction cosh(Function x) {
        return new DoubleFunction("cosh", x, Math::cosh);
    }

    /** Applies Math.tanh. */
    static DoubleFunction tanh(String x) {
        return tanh($(x));
    }

    /** Applies Math.tanh. */
    static DoubleFunction tanh(Function x) {
        return new DoubleFunction("tanh", x, Math::tanh);
    }

    /** Applies Math.asin. */
    static DoubleFunction asin(String x) {
        return asin($(x));
    }

    /** Applies Math.asin. */
    static DoubleFunction asin(Function x) {
        return new DoubleFunction("asin", x, Math::asin);
    }

    /** Applies Math.acos. */
    static DoubleFunction acos(String x) {
        return acos($(x));
    }

    /** Applies Math.acos. */
    static DoubleFunction acos(Function x) {
        return new DoubleFunction("acos", x, Math::acos);
    }

    /** Applies Math.atan. */
    static DoubleFunction atan(String x) {
        return atan($(x));
    }

    /** Applies Math.atan. */
    static DoubleFunction atan(Function x) {
        return new DoubleFunction("atan", x, Math::acos);
    }

    /** Applies Math.ulp. */
    static DoubleFunction ulp(String x) {
        return ulp($(x));
    }

    /** Applies Math.ulp. */
    static DoubleFunction ulp(Function x) {
        return new DoubleFunction("ulp", x, Math::ulp);
    }
}
