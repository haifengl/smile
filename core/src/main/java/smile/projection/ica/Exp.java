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

package smile.projection.ica;

import smile.math.DifferentiableFunction;

/**
 * The contrast function when the independent components are highly
 * super-Gaussian, or when robustness is very important.
 *
 * @author Haifeng Li
 */
public class Exp implements DifferentiableFunction {

    @Override
    public double f(double x) {
        return -Math.exp(-0.5 * x * x);
    }

    @Override
    public double g(double x) {
        return x * Math.exp(-0.5 * x * x);
    }

    @Override
    public double g2(double x) {
        double x2 = x * x;
        return (1 - x2) * Math.exp(-0.5 * x2);
    }
}
