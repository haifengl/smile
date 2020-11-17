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

import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

/**
 * Dot product kernel that depends only on the dot product of x and y.
 *
 * @author Haifeng Li
 */
public interface DotProductKernel {
    /**
     * Kernel function.
     * @param dot the dot product.
     */
    double k(double dot);

    /**
     * Kernel function.
     * This is simply for Scala convenience.
     */
    default double apply(double dot) {
        return k(dot);
    }

    /**
     * Returns the kernel matrix.
     *
     * @param pdot the pairwise dot product matrix.
     * @return the kernel matrix.
     */
    default Matrix K(Matrix pdot) {
        if (pdot.nrows() != pdot.ncols()) {
            throw new IllegalArgumentException("pdot is not square");
        }

        int n = pdot.nrows();
        Matrix K = new Matrix(n, n);

        for (int j = 0; j < n; j++) {
            K.set(j, j, k(pdot.get(j, j)));
            for (int i = j+1; i < n; i++) {
                double k = k(pdot.get(i, j));
                K.set(i, j, k);
                K.set(j, i, k);
            }
        }

        K.uplo(UPLO.LOWER);
        return K;
    }
}
