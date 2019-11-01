/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.projection;

import smile.math.matrix.DenseMatrix;

/** Linear projection. */
public interface LinearProjection extends Projection<double[]> {

    /**
     * Returns the projection matrix. The dimension reduced data can be obtained
     * by y = W * x.
     */
    DenseMatrix getProjection();

    @Override
    default double[] project(double[] x) {
        DenseMatrix A = getProjection();
        int p = A.nrows();
        int n = A.ncols();

        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, n));
        }

        double[] y = new double[p];
        A.ax(x, y);
        return y;
    }

    @Override
    default double[][] project(double[][] x) {
        DenseMatrix A = getProjection();
        int p = A.nrows();
        int n = A.ncols();

        if (x[0].length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, n));
        }

        double[][] y = new double[x.length][p];
        for (int i = 0; i < x.length; i++) {
            A.ax(x[i], y[i]);
        }
        return y;
    }
}
