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
 * Polyharmonic spline RBF. This is a generalization of the thin plate spline.
 * <p>
 * For odd order k:
 * <p>
 *     &phi;(r) = r<sup>k</sup>
 * <p>
 * For even order k:
 * <p>
 *     &phi;(r) = r<sup>k</sup> log(r)
 * <p>
 * with &phi;(0) = 0 assumed in all cases. The standard thin plate spline
 * corresponds to k = 2 (with scale r<sub>0</sub> = 1).
 * <p>
 * Polyharmonic splines minimize a natural bending energy functional
 * and are widely used in scattered data interpolation and image warping.
 * Higher-order splines produce smoother interpolants.
 *
 * <h2>References</h2>
 * <ol>
 * <li> R.L. Hardy. Multiquadric equations of topography and other irregular
 *      surfaces. J. Geophys. Res., 1971.</li>
 * <li> J. Duchon. Splines minimizing rotation-invariant semi-norms in
 *      Sobolev spaces. Constructive Theory of Functions of Several Variables, 1977.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class PolyharmonicSpline implements RadialBasisFunction {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The order of the polyharmonic spline. Must be a positive integer.
     * Order 1 = linear spline, order 2 = thin-plate spline (with log),
     * order 3 = cubic spline, etc.
     */
    private final int k;

    /**
     * Constructor for order 2 (thin-plate spline equivalent).
     */
    public PolyharmonicSpline() {
        this(2);
    }

    /**
     * Constructor.
     * @param k the order of the polyharmonic spline. Must be a positive integer.
     */
    public PolyharmonicSpline(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("order k must be positive: " + k);
        }
        this.k = k;
    }

    /**
     * Returns the order of the polyharmonic spline.
     * @return the order.
     */
    public int order() {
        return k;
    }

    @Override
    public double f(double r) {
        if (r <= 0.0) return 0.0;
        if (k % 2 == 0) {
            // Even order: φ(r) = r^k * log(r)
            return Math.pow(r, k) * Math.log(r);
        } else {
            // Odd order: φ(r) = r^k
            return Math.pow(r, k);
        }
    }

    @Override
    public String toString() {
        return String.format("PolyharmonicSpline(k = %d)", k);
    }
}

