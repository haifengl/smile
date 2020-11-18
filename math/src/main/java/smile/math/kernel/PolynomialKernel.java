/*******************************************************************************
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
 ******************************************************************************/

package smile.math.kernel;

import smile.math.MathEx;

/**
 * The polynomial kernel.
 * <p>
 * <pre>
 *     k(u, v) = (&gamma; u<sup>T</sup>v - &lambda;)<sup>d</sup>
 * </pre>
 * where &gamma; is the scale of the used inner product, &lambda; the offset of
 * the used inner product, and <i>d</i> the order of the polynomial kernel.
 * 
 * @author Haifeng Li
 */
public class PolynomialKernel extends Polynomial implements MercerKernel<double[]> {
    /**
     * Constructor with scale 1 and bias 0.
     */
    public PolynomialKernel(int degree) {
        super(degree);
    }

    /**
     * Constructor.
     */
    public PolynomialKernel(int degree, double scale, double offset) {
        super(degree, scale, offset);
    }

    @Override
    public double k(double[] x, double[] y) {
        return k(MathEx.dot(x, y));
    }
}
