/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math.rbf;

import java.io.Serial;

/**
 * Inverse quadratic RBF.
 * <p>
 *     &phi;(r) = 1 / (1 + (r / r<sub>0</sub>)<sup>2</sup>)
 * <p>
 * where r<sub>0</sub> is a scale factor. The inverse quadratic is a
 * completely monotone function, making it a valid positive-definite kernel.
 * Unlike the Gaussian, it has heavier tails, which can be advantageous
 * for capturing long-range correlations. It also has the property that
 * &phi;(0) = 1 and &phi;(r) → 0 as r → ∞.
 * <p>
 * In general, r<sub>0</sub> should be larger than the typical separation of
 * points but smaller than the "outer scale" or feature size of the function
 * to interpolate.
 *
 * @author Haifeng Li
 */
public class InverseQuadraticRadialBasis implements RadialBasisFunction {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The scale factor.
     */
    private final double r0;

    /**
     * Constructor. The default scale is 1.0.
     */
    public InverseQuadraticRadialBasis() {
        this(1.0);
    }

    /**
     * Constructor.
     * @param scale the scale parameter. Must be positive.
     */
    public InverseQuadraticRadialBasis(double scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("scale is not positive: " + scale);
        }
        r0 = scale;
    }

    /**
     * Returns the scale factor r0.
     * @return the scale factor.
     */
    public double scale() {
        return r0;
    }

    @Override
    public double f(double r) {
        double x = r / r0;
        return 1.0 / (1.0 + x * x);
    }

    @Override
    public String toString() {
        return String.format("InverseQuadraticRadialBasis(r0 = %.4f)", r0);
    }
}

