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
 * Thin plate RBF. &phi;(r) = r<sup>2</sup> log(r / r<sub>0</sub>)
 * with the limiting value &phi;(0)=0 assumed, where r<sub>0</sub> is a scale factor.
 * This function has some physical justification in the energy minimization
 * problem associated with warping a thin elastic plate. However, there is no
 * indication that it is generally better than multiquadric or inverse
 * multiquadric function.
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
public class ThinPlateRadialBasis implements RadialBasisFunction {
    private static final long serialVersionUID = 1L;

    private double r0;

    /**
     * Constructor.
     */
    public ThinPlateRadialBasis() {
        this(1.0);
    }

    /**
     * Constructor.
     */
    public ThinPlateRadialBasis(double scale) {
        r0 = scale;
    }

    @Override
    public double f(double r) {
        return r <= 0.0 ? 0.0 : r * r * Math.log(r / r0);
    }

    @Override
    public String toString() {
        return String.format("Thin Plate Radial Basis (r0 = %.4f)", r0);
    }
}
