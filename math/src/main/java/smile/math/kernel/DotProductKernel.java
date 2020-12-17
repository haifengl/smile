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

package smile.math.kernel;

import smile.math.Function;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

/**
 * Dot product kernel depends only on the dot product of x and y.
 *
 * @author Haifeng Li
 */
public interface DotProductKernel extends Function {

    @Override
    default double f(double dot) {
        return k(dot);
    }

    /**
     * Computes the dot product kernel function.
     * @param dot the dot product.
     * @return the kernel value.
     */
    double k(double dot);

    /**
     * Computes the dot product kernel function and its gradient over hyperparameters..
     * @param dot The dot product.
     * @return the kernel value and gradient.
     */
    double[] kg(double dot);

    /**
     * Computes the kernel function.
     * This is simply for Scala convenience.
     * @param dot the dot product.
     * @return the kernel value.
     */
    default double apply(double dot) {
        return k(dot);
    }

    /**
     * Computes the kernel matrix.
     *
     * @param pdot the pairwise dot product matrix.
     * @return the kernel matrix.
     */
    default Matrix K(Matrix pdot) {
        if (pdot.nrow() != pdot.ncol()) {
            throw new IllegalArgumentException("pdot is not square");
        }

        int n = pdot.nrow();
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
