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

/**
 * Laplacian Kernel, also referred as exponential kernel.
 * <p>
 * <pre>
 *     k(u, v) = e<sup>-||u-v|| / &sigma;</sup>
 * </pre>
 * where <code>&sigma; &gt; 0</code> is the scale parameter of the kernel.
 *
 * @author Haifeng Li
 */
public class Laplacian implements IsotropicKernel {
    private static final long serialVersionUID = 2L;

    /**
     * The length scale of the kernel.
     */
    private final double sigma;

    /**
     * Constructor.
     * @param sigma The length scale of kernel.
     */
    public Laplacian(double sigma) {
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma is not positive: " + sigma);
        }

        this.sigma = sigma;
    }

    /** Returns the length scale of kernel. */
    public double scale() {
        return sigma;
    }

    @Override
    public String toString() {
        return String.format("Laplacian(%.4f)", sigma);
    }

    @Override
    public double k(double dist) {
        return Math.exp(-dist / sigma);
    }
}
