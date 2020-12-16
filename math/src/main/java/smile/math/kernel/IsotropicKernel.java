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
 * Isotropic kernel. If the kernel is a function only of the distance
 * <code>|x − y|</code>, it is called isotropic. It is thus invariant
 * to all rigid motions.
 *
 * @author Haifeng Li
 */
public interface IsotropicKernel extends Function {

    @Override
    default double f(double dist) {
        return k(dist);
    }

    /**
     * Computes the isotropic kernel function.
     * @param dist The distance.
     * @return the kernel value.
     */
    double k(double dist);

    /**
     * Computes the isotropic kernel function and its gradient over hyperparameters..
     * @param dist The distance.
     * @return the kernel value and gradient.
     */
    double[] kg(double dist);

    /**
     * Computes the kernel function.
     * This is simply for Scala convenience.
     * @param dist The distance.
     * @return the kernel value.
     */
    default double apply(double dist) {
        return k(dist);
    }

    /**
     * Computes the kernel matrix.
     *
     * @param pdist The pairwise distance matrix.
     * @return The kernel matrix.
     */
    default Matrix K(Matrix pdist) {
        if (pdist.nrow() != pdist.ncol()) {
            throw new IllegalArgumentException(String.format("pdist is not square: %d x %d", pdist.nrow(), pdist.ncol()));
        }

        int n = pdist.nrow();
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

    /**
     * Computes the kernel and gradient matrices.
     *
     * @param pdist The pairwise distance matrix.
     * @return the kernel and gradient matrices.
     */
    default Matrix[] KG(Matrix pdist) {
        if (pdist.nrow() != pdist.ncol()) {
            throw new IllegalArgumentException(String.format("pdist is not square: %d x %d", pdist.nrow(), pdist.ncol()));
        }

        int n = pdist.nrow();
        int m = kg(pdist.get(0, 0)).length;
        Matrix[] K = new Matrix[m];
        for (int i = 0; i < m; i++) {
            K[i] = new Matrix(n, n);
            K[i].uplo(UPLO.LOWER);
        }

        for (int j = 0; j < n; j++) {
            double[] kg = kg(pdist.get(j, j));
            for (int l = 0; l < m; l++) {
                K[l].set(j, j, kg[l]);
            }

            for (int i = j+1; i < n; i++) {
                kg = kg(pdist.get(i, j));
                for (int l = 0; l < m; l++) {
                    K[l].set(i, j, kg[l]);
                    K[l].set(j, i, kg[l]);
                }
            }
        }

        return K;
    }
}
