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

package smile.math;

/**
 * A differentiable function is a function whose derivative exists at each point
 * in its domain.
 *
 * @author Haifeng Li
 */
public interface DifferentiableMultivariateFunction extends MultivariateFunction {
    /** A number close to zero, between machine epsilon and its square root. */
    double EPSILON = Double.parseDouble(System.getProperty("smile.gradient.epsilon", "1E-8"));

    /**
     * Computes the value and gradient at x. The default implementation
     * uses finite differences to calculate the gradient. When possible,
     * the subclass should compute the gradient analytically.
     *
     * @param x a real vector.
     * @param gradient the output variable of gradient.
     * @return the function value.
     */
    default double g(double[] x, double[] gradient) {
        double fx = f(x);

        int n = x.length;
        double[] xh = new double[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(x, 0, xh, 0, n);
            double xi = x[i];
            double h = EPSILON * Math.abs(xi);
            if (h == 0.0) {
                h = EPSILON;
            }
            xh[i] = xi + h; // trick to reduce finite-precision error.
            h = xh[i] - xi;

            double fh = f(xh);
            xh[i] = xi;
            gradient[i] = (fh - fx) / h;
        }

        return fx;
    }
}
