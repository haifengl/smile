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
 *
 * @author Haifeng Li
 */
public interface IsotropicKernel {
    /**
     * Kernel function.
     * @param dist the squared distance.
     */
    double k(double dist);

    /**
     * Kernel function.
     * This is simply for Scala convenience.
     */
    default double apply(double dist) {
        return k(dist);
    }

    /**
     * Returns the kernel matrix.
     *
     * @param pdist the pairwise squared distance matrix.
     * @return the kernel matrix.
     */
    default Matrix K(Matrix pdist) {
        if (pdist.nrows() != pdist.ncols()) {
            throw new IllegalArgumentException("pdist is not square");
        }

        int n = pdist.nrows();
        Matrix K = new Matrix(n, n);

        for (int j = 0; j < n; j++) {
            K.set(j, j, k(pdist.get(j, j)));
            for (int i = j+1; i < n; i++) {
                double k = k(pdist.get(i, j));
                K.set(i, j, k);
                K.set(j, i, k);
            }
        }

        K.uplo(UPLO.LOWER);
        return K;
    }
}
