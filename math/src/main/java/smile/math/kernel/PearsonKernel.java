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
 * Pearson VII universal kernel. The Pearson VII function
 * is often used for curve fitting of X-ray diffraction
 * scans and single bands in infrared spectra.
 *
 * <h2>References</h2>
 * <ol>
 * <li> B. Üstün, W.J. Melssen, and L. Buydens. Facilitating the Application of Support Vector Regression by Using a Universal Pearson VII Function Based Kernel, 2006.</li>
 * </ol>
 *
 * @author Diego Catalano
 */
public class PearsonKernel implements MercerKernel<double[]> {
    private static final long serialVersionUID = 2L;

    /** The tailing factor of the peak. */
    private final double omega;
    /** Pearson width. */
    private final double sigma;
    /** The coefficient 4 * (2 ^ (1/omega) - 1) / (sigma^2). */
    private final double C;
    /** The lower bound of sigma. */
    private final double lo;
    /** The upper bound of sigma. */
    private final double hi;

    /**
     * Constructor.
     * @param sigma Pearson width.
     * @param omega The tailing factor of the peak.
     */
    public PearsonKernel(double sigma, double omega) {
        this(sigma, omega, 1E-5, 1E5);
    }

    /**
     * Constructor.
     * @param sigma Pearson width.
     * @param omega The tailing factor of the peak. The tailing factor is
     *              fixed during hyperparameter tuning.
     * @param lo The lower bound of length scale for hyperparameter tuning.
     * @param hi The upper bound of length scale for hyperparameter tuning.
     */
    public PearsonKernel(double sigma, double omega, double lo, double hi) {
        this.omega = omega;
        this.sigma = sigma;
        this.C = 4.0 * (Math.pow(2.0, 1.0 / omega) - 1.0) / (sigma * sigma);
        this.lo = lo;
        this.hi = hi;
    }

    /**
     * Returns Pearson width.
     * @return Pearson width.
     */
    public double sigma() {
        return sigma;
    }

    /**
     * Returns the tailing factor of the peak.
     * @return the tailing factor of the peak.
     */
    public double omega() {
        return omega;
    }

    @Override
    public String toString() {
        return String.format("PearsonKernel(%.4f, %.4f)", sigma, omega);
    }

    @Override
    public double k(double[] x, double[] y) {
        double d = MathEx.squaredDistance(x, y);
        return Math.pow(1.0 + C * d, -omega);
    }

    @Override
    public double[] kg(double[] x, double[] y) {
        double d = MathEx.squaredDistance(x, y);
        double[] g = new double[2];
        g[0] = Math.pow(1.0 + C * d, -omega);
        g[1] = -2 * C * d * Math.pow(1.0 + C * d, -omega-1) / sigma;
        return g;
    }

    @Override
    public PearsonKernel of(double[] params) {
        return new PearsonKernel(params[0], omega, lo, hi);
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