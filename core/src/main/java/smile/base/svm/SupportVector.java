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

package smile.base.svm;

import java.io.Serial;
import java.io.Serializable;

/**
 * Support vector.
 *
 * @param <T> the data type of support vectors.
 *
 * @author Haifeng Li
 */
public class SupportVector<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The index of support vector in training samples.
     */
    final int i;
    /**
     * Support vector.
     */
    final T x;
    /**
     * Lagrangian multiplier of support vector.
     */
    double alpha;
    /**
     * Gradient y - K&alpha;.
     */
    double g;
    /**
     * Lower bound of alpha.
     */
    final double cmin;
    /**
     * Upper bound of alpha.
     */
    final double cmax;
    /**
     * Kernel value k(x, x)
     */
    final double k;

    /**
     * Constructor.
     * @param i the index of support vector.
     * @param x the support vector.
     * @param y the class label.
     * @param alpha Lagrangian multiplier of support vector.
     * @param g the gradient.
     * @param Cp the bound on positive samples.
     * @param Cn the bound on negative samples.
     * @param k the kernel value k(x, x).
     */
    public SupportVector(int i, T x, int y, double alpha, double g, double Cp, double Cn, double k) {
        this.i = i;
        this.x = x;
        this.alpha = alpha;
        this.g = g;
        this.k = k;

        if (y > 0) {
            cmin = 0;
            cmax = Cp;
        } else {
            cmin = -Cn;
            cmax = 0;
        }
    }
}

