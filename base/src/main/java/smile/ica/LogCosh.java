/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.ica;

import java.io.Serial;
import java.io.Serializable;
import smile.util.function.DifferentiableFunction;

/**
 * A good general-purpose contrast function for ICA.
 * <p>
 * The function is {@code G(u) = log(cosh(u))}, computed in a numerically
 * stable manner to avoid overflow for large |u|.
 *
 * @author Haifeng Li
 */
public class LogCosh implements DifferentiableFunction, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Constructor. */
    public LogCosh() {
    }

    @Override
    public double f(double x) {
        // Numerically stable: log(cosh(x)) = |x| + log(1 + exp(-2|x|)) - log(2)
        double ax = Math.abs(x);
        return ax + Math.log1p(Math.exp(-2.0 * ax)) - Math.log(2.0);
    }

    @Override
    public double g(double x) {
        return Math.tanh(x);
    }

    @Override
    public double g2(double x) {
        double tanh = Math.tanh(x);
        return 1.0 - tanh * tanh;
    }

    @Override
    public String toString() {
        return "LogCosh";
    }
}