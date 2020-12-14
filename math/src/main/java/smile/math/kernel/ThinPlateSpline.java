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

package smile.math.kernel;

/**
 * The Thin Plate Spline kernel.
 * <p>
 *     k(u, v) = (||u-v|| / &sigma;)<sup>2</sup> log (||u-v|| / &sigma;)
 * <p>
 * where &sigma; {@code > 0} is the scale parameter of the kernel.
 *
 * @author Haifeng Li
 */
public class ThinPlateSpline implements IsotropicKernel {
    private static final long serialVersionUID = 2L;

    /**
     * The length scale of the kernel.
     */
    final double sigma;
    /** The lower bound of length scale for hyperparameter tuning. */
    final double lo;
    /** The upper bound of length scale for hyperparameter tuning. */
    final double hi;

    /**
     * Constructor.
     * @param sigma The length scale of kernel.
     * @param lo The lower bound of length scale for hyperparameter tuning.
     * @param hi The upper bound of length scale for hyperparameter tuning.
     */
    public ThinPlateSpline(double sigma, double lo, double hi) {
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma is not positive: " + sigma);
        }

        this.sigma = sigma;
        this.lo = lo;
        this.hi = hi;
    }

    /**
     * Returns the length scale of kernel.
     * @return the length scale of kernel.
     */
    public double scale() {
        return sigma;
    }

    @Override
    public String toString() {
        return String.format("ThinPlateSplineKernel(%.4f)", sigma);
    }

    @Override
    public double k(double dist) {
        double d = dist / sigma;
        return d * d * Math.log(d);
    }

    @Override
    public double[] kg(double dist) {
        double[] g = new double[2];
        double d = dist / sigma;
        double k = d * d * Math.log(d);
        g[0] = k;
        g[1] = -d * d * (2.0 * Math.log(d) + 1.0) / sigma;
        return g;
    }
}
