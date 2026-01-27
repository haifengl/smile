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

import smile.util.function.DifferentiableFunction;

/**
 * A good general-purpose contrast function for ICA.
 *
 * @author Haifeng Li
 */
public class LogCosh implements DifferentiableFunction {
    /** Constructor. */
    public LogCosh() {

    }

    @Override
    public double f(double x) {
        return Math.log(Math.cosh(x));
    }

    @Override
    public double g(double x) {
        return Math.tanh(x);
    }

    @Override
    public double g2(double x) {
        double tanh = Math.tanh(x);
        return 1 - tanh * tanh;
    }
}