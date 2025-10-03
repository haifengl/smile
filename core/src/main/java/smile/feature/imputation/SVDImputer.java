/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.imputation;

import smile.tensor.DenseMatrix;
import smile.tensor.QR;
import smile.tensor.SVD;
import smile.tensor.Vector;

/**
 * Missing value imputation with singular value decomposition. Given SVD
 * A = U &Sigma; V<sup>T</sup>, we use the most significant eigenvectors of
 * V<sup>T</sup> to linearly estimate missing values. Although it has been
 * shown that several significant eigenvectors are sufficient to describe
 * the data with small errors, the exact fraction of eigenvectors best for
 * estimation needs to be determined empirically. Once k most significant
 * eigenvectors from V<sup>T</sup> are selected, we estimate a missing value j
 * in row i by first regressing this row against the k eigenvectors and then use
 * the coefficients of the regression to reconstruct j from a linear combination
 * of the k eigenvectors. The j th value of row i and the j th values of the k
 * eigenvectors are not used in determining these regression coefficients.
 * It should be noted that SVD can only be performed on complete matrices;
 * therefore we originally fill all missing values by other methods in
 * matrix A, obtaining A'. We then utilize an expectation maximization method to
 * arrive at the final estimate, as follows. Each missing value in A is estimated
 * using the above algorithm, and then the procedure is repeated on the newly
 * obtained matrix, until the total change in the matrix falls below the
 * empirically determined threshold (say 0.01).
 *
 * @author Haifeng Li
 */
public interface SVDImputer {
    /**
     * Impute missing values in the dataset.
     * @param data a data set with missing values (represented as Double.NaN).
     * @param k the number of eigenvectors used for imputation.
     * @param maxIter the maximum number of iterations.
     * @return the imputed data.
     * @throws IllegalArgumentException when the whole row or column is missing.
     */
    static double[][] impute(double[][] data, int k, int maxIter) {
        if (k < 1 || k > Math.min(data.length, data[0].length)) {
            throw new IllegalArgumentException("Invalid number of eigenvectors for imputation: " + k);
        }

        if (maxIter < 1) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int d = data[0].length;
        double[][] full = SimpleImputer.impute(data);

        for (int iter = 0; iter < maxIter; iter++) {
            SVD svd = DenseMatrix.of(full).svd(true);

            for (int i = 0; i < data.length; i++) {
                int missing = 0;
                for (int j = 0; j < d; j++) {
                    if (Double.isNaN(data[i][j])) {
                        missing++;
                    } else {
                        full[i][j] = data[i][j];
                    }
                }

                if (missing == 0) {
                    continue;
                }

                DenseMatrix A = svd.Vt().zeros(d - missing, k);
                double[] b = new double[d - missing];

                for (int j = 0, m = 0; j < d; j++) {
                    if (!Double.isNaN(data[i][j])) {
                        for (int l = 0; l < k; l++) {
                            A.set(m, l, svd.Vt().get(l, j));
                        }
                        b[m++] = data[i][j];
                    }
                }

                QR qr = A.qr();
                Vector s = qr.solve(b);

                for (int j = 0; j < d; j++) {
                    if (Double.isNaN(data[i][j])) {
                        full[i][j] = 0;
                        for (int l = 0; l < k; l++) {
                            full[i][j] += s.get(l) * svd.Vt().get(l, j);
                        }
                    }
                }
            }
        }

        return full;
    }
}
