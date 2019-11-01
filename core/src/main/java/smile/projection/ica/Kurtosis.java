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
 * This function is justified on statistical grounds only for
 * estimating sub-Gaussian independent components when there are no outliers.
 */
public class Kurtosis implements DifferentiableFunction {

    @Override
    public double f(double x) {
        return 0.25 * x * x * x * x;
    }

    @Override
    public double g(double x) {
        return x * x * x;
    }

    @Override
    public double g2(double x) {
        return 3 * x * x;
    }
}
