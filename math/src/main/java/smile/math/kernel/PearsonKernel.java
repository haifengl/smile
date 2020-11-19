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

    /**
     * Constructor.
     * @param sigma Pearson width.
     * @param omega The tailing factor of the peak.
     */
    public PearsonKernel(double sigma, double omega) {
        this.omega = omega;
        this.sigma = sigma;
        this.C = 4.0 * (Math.pow(2.0, 1.0 / omega) - 1.0) / (sigma * sigma);
    }

    /**
     * Returns Pearson width.
     */
    public double sigma() {
        return sigma;
    }

    /**
     * Returns the tailing factor of the peak.
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
        return 1.0 / Math.pow(1.0 + C * d, omega);
    }
}