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
 * Isotropic kernel. If the kernel is a function only of <code>|x − y|</code>
 * then it is called isotropic. It is thus invariant to all rigid motions.
 * If a covariance function depends only on the dot product of x and y,
 * we call it a dot product covariance function.
 *
 * @author Haifeng Li
 */
public interface IsotropicKernel {
    /**
     * Kernel function.
     * @param d the distance <code>|x − y|</code>.
     */
    double k(double d);

    /**
     * Kernel function.
     * This is simply for Scala convenience.
     */
    default double apply(double d) {
        return k(d);
    }

    /**
     * Returns the kernel matrix.
     *
     * @param pdist the pairwise distance matrix.
     * @return the kernel matrix.
     */
    default Matrix K(double[][] pdist) {
        int n = pdist.length;
        Matrix K = new Matrix(n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double k = k(pdist[i][j]);
                K.set(i, j, k);
                K.set(j, i, k);
            }
            K.set(i, i, k(pdist[i][i]));
        }

        K.uplo(UPLO.LOWER);
        return K;
    }

    /**
     * Returns the kernel matrix.
     *
     * @param pdist the pairwise distance matrix.
     * @return the kernel matrix.
     */
    default Matrix K(Matrix pdist) {
        if (pdist.nrows() != pdist.ncols()) {
            throw new IllegalArgumentException("pdist is not square");
        }

        int n = pdist.nrows();
        Matrix K = new Matrix(n, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < i; i++) {
                double k = k(pdist.get(i, j));
                K.set(i, j, k);
                K.set(j, i, k);
            }
            K.set(j, j, k(pdist.get(j, j)));
        }

        K.uplo(UPLO.LOWER);
        return K;
    }
}
