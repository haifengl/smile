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

import smile.math.MathEx;

/**
 * The Thin Plate Spline kernel.
 * <p>
 *     k(u, v) = (||u-v|| / &sigma;)<sup>2</sup> log (||u-v|| / &sigma;)
 * <p>
 * where &sigma; {@code > 0} is the scale parameter of the kernel.
 * 
 * @author Haifeng Li
 */
public class ThinPlateSplineKernel extends ThinPlateSpline implements MercerKernel<double[]> {
    /**
     * Constructor.
     * @param sigma The length scale of kernel.
     */
    public ThinPlateSplineKernel(double sigma) {
        this(sigma, 1E-05, 1E5);
    }

    /**
     * Constructor.
     * @param sigma The length scale of kernel.
     * @param lo The lower bound of length scale for hyperparameter tuning.
     * @param hi The upper bound of length scale for hyperparameter tuning.
     */
    public ThinPlateSplineKernel(double sigma, double lo, double hi) {
        super(sigma, lo, hi);
    }


    @Override
    public double k(double[] x, double[] y) {
        return k(MathEx.distance(x, y));
    }

    @Override
    public double[] kg(double[] x, double[] y) {
        return kg(MathEx.distance(x, y));
    }

    @Override
    public ThinPlateSplineKernel of(double[] params) {
        return new ThinPlateSplineKernel(params[0], lo, hi);
    }

    @Override
    public double[] hyperparameters() {
        return new double[] { sigma };
    }

    @Override
    public double[] lo() {
        return new double[] { lo };
    }

    @Override
    public double[] hi() {
        return new double[] { hi };
    }
}
