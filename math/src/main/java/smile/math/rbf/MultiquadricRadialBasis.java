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

package smile.math.rbf;

/**
 * Multiquadric RBF. &phi;(r) = (r<sup>2</sup> + r<sup>2</sup><sub>0</sub>)<sup>1/2</sup>
 * where r<sub>0</sub> is a scale factor. Multiquadrics are said to be less
 * sensitive to the choice of r<sub>0</sub> than som other functional forms.
 * <p>
 * In general, r<sub>0</sub> should be larger than the typical separation of
 * points but smaller than the "outer scale" or feature size of the function
 * to interplate. There can be several orders of magnitude difference between
 * the interpolation accuracy with a good choice for r<sub>0</sub>, versus a
 * poor choice, so it is definitely worth some experimentation. One way to
 * experiment is to construct an RBF interpolator omitting one data point
 * at a time and measuring the interpolation error at the omitted point.
 *
 * @author Haifeng Li
 */
public class MultiquadricRadialBasis implements RadialBasisFunction {
    private static final long serialVersionUID = 1L;

    private double r02;

    /**
     * Constructor.
     */
    public MultiquadricRadialBasis() {
        this(1.0);
    }

    /**
     * Constructor.
     */
    public MultiquadricRadialBasis(double scale) {
        r02 = scale * scale;
    }

    @Override
    public double f(double r) {
        return Math.sqrt(r * r + r02);
    }

    @Override
    public String toString() {
        return String.format("Multiquadric Radial Basis (r0 = %.4f)", r02);
    }
}
