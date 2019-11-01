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

package smile.projection.ica;

import smile.math.DifferentiableFunction;

/**
 * A good general-purpose function for ICA.
 */
public class LogCosh implements DifferentiableFunction {

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