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

package smile.projection;

import smile.math.matrix.Matrix;

/**
 * Linear projection.
 *
 * @author Haifeng Li
 */
public interface LinearProjection extends Projection<double[]> {

    /**
     * Returns the projection matrix. The dimension reduced data can be obtained
     * by y = W * x.
     * @return the projection matrix.
     */
    Matrix projection();

    @Override
    default double[] project(double[] x) {
        Matrix A = projection();
        int p = A.nrow();
        int n = A.ncol();

        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, n));
        }

        double[] y = new double[p];
        A.mv(x, y);
        return y;
    }

    @Override
    default double[][] project(double[][] x) {
        Matrix A = projection();
        int p = A.nrow();
        int n = A.ncol();

        if (x[0].length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, n));
        }

        double[][] y = new double[x.length][p];
        for (int i = 0; i < x.length; i++) {
            A.mv(x[i], y[i]);
        }
        return y;
    }
}
