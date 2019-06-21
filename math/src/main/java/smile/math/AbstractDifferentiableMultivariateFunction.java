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

package smile.math;

/**
 * An abstract implementation that uses finite differences to calculate the
 * partial derivatives instead of providing them analytically.
 *
 * @author Haifeng Li
 */
public abstract class AbstractDifferentiableMultivariateFunction implements DifferentiableMultivariateFunction {

    private static final double EPS = 1.0E-8;

    @Override
    public double g(double[] x, double[] gradient) {
        double f = applyAsDouble(x);

        double[] xh = x.clone();
        for (int j = 0; j < x.length; j++) {
            double temp = x[j];
            double h = EPS * Math.abs(temp);
            if (h == 0.0) {
                h = EPS;
            }
            xh[j] = temp + h; // trick to reduce finite-precision error.
            h = xh[j] - temp;
            double fh = applyAsDouble(xh);
            xh[j] = temp;
            gradient[j] = (fh - f) / h;
        }

        return f;
    }
}
