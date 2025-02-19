/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util.function;

import java.io.Serializable;
import java.util.function.ToDoubleFunction;

/**
 * An interface representing a multivariate real function.
 *
 * @author Haifeng Li
 */
public interface MultivariateFunction extends ToDoubleFunction<double[]>, Serializable {
    /**
     * Computes the value of the function at x.
     * @param x a real vector.
     * @return the function value.
     */
    double f(double[] x);

    /**
     * Computes the value of the function at x.
     * It delegates the computation to f().
     * This is simply for Scala convenience.
     *
     * @param x a real vector.
     * @return the function value.
     */
    default double apply(double... x) {
        return f(x);
    }

    @Override
    default double applyAsDouble(double[] x) {
        return f(x);
    }
}
