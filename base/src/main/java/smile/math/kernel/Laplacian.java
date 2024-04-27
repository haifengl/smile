/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.kernel;

import java.io.Serial;

/**
 * Laplacian kernel, also referred as exponential kernel.
 * <p>
 *     k(u, v) = exp(-||u-v|| / &sigma;)
 * <p>
 * where &sigma; {@code > 0} is the scale parameter of the kernel.
 *
 * @author Haifeng Li
 */
public class Laplacian implements IsotropicKernel {
    @Serial
    private static final long serialVersionUID = 2L;

    /** The length scale of the kernel. */
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
    public Laplacian(double sigma, double lo, double hi) {
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
        return String.format("LaplacianKernel(%.4f)", sigma);
    }

    @Override
    public double k(double dist) {
        return Math.exp(-dist / sigma);
    }

    @Override
    public double[] kg(double dist) {
        double[] g = new double[2];
        double d = dist / sigma;
        double k = Math.exp(-d);
        g[0] = k;
        g[1] = k * d / sigma;
        return g;
    }
}
