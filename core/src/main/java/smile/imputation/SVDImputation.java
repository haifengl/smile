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

package smile.imputation;

import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.QR;
import smile.math.matrix.SVD;

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
public class SVDImputation implements MissingValueImputation {

    /**
     * The number of eigenvectors used for imputation.
     */
    private int k;

    /**
     * Constructor.
     * @param k the number of eigenvectors used for imputation.
     */
    public SVDImputation(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Invalid number of eigenvectors for imputation: " + k);
        }

        this.k = k;
    }

    @Override
    public void impute(double[][] data) throws MissingValueImputationException {
        impute(data, 10);
    }
    
    /**
     * Impute missing values in the dataset.
     * @param data a data set with missing values (represented as Double.NaN).
     * On output, missing values are filled with estimated values.
     * @param maxIter the maximum number of iterations.
     */
    public void impute(double[][] data, int maxIter) throws MissingValueImputationException {
        if (maxIter < 1) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int[] count = new int[data[0].length];
        for (int i = 0; i < data.length; i++) {
            int n = 0;
            for (int j = 0; j < data[i].length; j++) {
                if (Double.isNaN(data[i][j])) {
                    n++;
                    count[j]++;
                }
            }

            if (n == data[i].length) {
                throw new MissingValueImputationException("The whole row " + i + " is missing");
            }
        }

        for (int i = 0; i < data[0].length; i++) {
            if (count[i] == data.length) {
                throw new MissingValueImputationException("The whole column " + i + " is missing");
            }
        }

        double[][] full = new double[data.length][];
        for (int i = 0; i < full.length; i++) {
            full[i] = data[i].clone();
        }

        MissingValueImputation.imputeWithColumnAverage(full);

        for (int iter = 0; iter < maxIter; iter++) {
            svdImpute(data, full);
        }

        for (int i = 0; i < data.length; i++) {
            System.arraycopy(full[i], 0, data[i], 0, data[i].length);
        }
    }

    /**
     * Impute the missing values by SVD.
     * @param raw the raw data with missing values.
     * @param data the data with current imputations.
     */
    private void svdImpute(double[][] raw, double[][] data) {
        SVD svd = Matrix.of(data).svd();

        int d = data[0].length;

        for (int i = 0; i < raw.length; i++) {
            int missing = 0;
            for (int j = 0; j < d; j++) {
                if (Double.isNaN(raw[i][j])) {
                    missing++;
                } else {
                    data[i][j] = raw[i][j];
                }
            }

            if (missing == 0) {
                continue;
            }

            DenseMatrix A = Matrix.zeros(d - missing, k);
            double[] b = new double[d - missing];

            for (int j = 0, m = 0; j < d; j++) {
                if (!Double.isNaN(raw[i][j])) {
                    for (int l = 0; l < k; l++) {
                        A.set(m, l, svd.getV().get(j, l));
                    }
                    b[m++] = raw[i][j];
                }
            }

            double[] s = new double[k];
            QR qr = A.qr();
            qr.solve(b, s);

            for (int j = 0; j < d; j++) {
                if (Double.isNaN(raw[i][j])) {
                    data[i][j] = 0;
                    for (int l = 0; l < k; l++) {
                        data[i][j] += s[l] * svd.getV().get(j, l);
                    }
                }
            }
        }
    }
}
