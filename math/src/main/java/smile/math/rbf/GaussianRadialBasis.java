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
 * Gaussian RBF. &phi;(r) = e<sup>-0.5 * r<sup>2</sup> / r<sup>2</sup><sub>0</sub></sup>
 * where r<sub>0</sub> is a scale factor. The interpolation accuracy using
 * Gaussian basis functions can be very sensitive to r<sub>0</sub>, and they
 * are often avoided for this reason. However, for smooth functions and with
 * an optimal r<sub>0</sub>, very high accuracy can be achieved. The Gaussian
 * also will extrapolate any function to zero far from the data, and it gets
 * to zero quickly.
 * <p>
 * In general, r<sub>0</sub> should be larger than the typical separation of
 * points but smaller than the "outer scale" or feature size of the function
 * to interplate. There can be several orders of magnitude difference between
 * the interpolation accuracy with a good choice for r<sub>0</sub>, versus a
 * poor choice, so it is definitely worth some experimentation. One way to
 * experiment is to construct an RBF interpolator omitting one data point
 * at a time and measuring the interpolation error at the omitted point.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Nabil Benoudjit and Michel Verleysen. On the kernel widths in radial-basis function networks. Neural Process, 2003.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class GaussianRadialBasis implements RadialBasisFunction {
    private static final long serialVersionUID = 1L;

    /**
     * The scale factor.
     */
    private double r0;

    /**
     * Constructor. The default bandwidth is 1.0.
     */
    public GaussianRadialBasis() {
        this(1.0);
    }

    /**
     * Constructor.
     *
     * @param scale the scale (bandwidth/sigma) parameter.
     */
    public GaussianRadialBasis(double scale) {
        r0 = scale;
    }

    @Override
    public double f(double r) {
        r /= r0;
        return Math.exp(-0.5 * r * r);
    }

    @Override
    public String toString() {
        return String.format("Gaussian Radial Basis (r0 = %.4f)", r0);
    }
}
