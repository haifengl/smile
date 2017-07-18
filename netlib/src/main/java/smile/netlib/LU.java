/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.netlib;

import smile.math.Math;
import smile.math.matrix.DenseMatrix;
import com.github.fommil.netlib.LAPACK;
import org.netlib.util.intW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.matrix.Matrix;

/**
 * For an m-by-n matrix A with m &ge; n, the LU decomposition is an m-by-n
 * unit lower triangular matrix L, an n-by-n upper triangular matrix U,
 * and a permutation vector piv of length m so that A(piv,:) = L*U.
 * If m &lt; n, then L is m-by-m and U is m-by-n.
 * <p>
 * The LU decomposition with pivoting always exists, even if the matrix is
 * singular. The primary use of the LU decomposition is in the solution of
 * square systems of simultaneous linear equations if it is not singular.
 * <p>
 * This decomposition can also be used to calculate the determinant.
 *
 * @author Haifeng Li
 */
public class LU extends smile.math.matrix.LU {
    private static final Logger logger = LoggerFactory.getLogger(LU.class);

    /**
     * Constructor.
     * @param lu       LU decomposition matrix
     * @param piv      pivot vector
     * @param singular True if the matrix is singular
     */
    public LU(DenseMatrix lu, int[] piv, boolean singular) {
        super(lu, piv, pivsign(piv, Math.min(lu.nrows(), lu.ncols())), singular);
    }

    /** Returns the pivot sign. */
    private static int pivsign(int[] piv, int n) {
        int pivsign = 1;
        for (int i = 0; i < n; i++) {
            if (piv[i] != (i+1))
                pivsign = -pivsign;
        }

        return pivsign;
    }

    /**
     * Returns the matrix inverse. The LU matrix will overwritten with
     * the inverse of the original matrix.
     */
    @Override
    public DenseMatrix inverse() {
        int m = lu.nrows();
        int n = lu.ncols();

        if (m != n) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", m, n));
        }

        int nb = LAPACK.getInstance().ilaenv(1, "DGETRI", "", n, -1, -1, -1);
        if (nb < 0) {
            logger.warn("LAPACK ILAENV error code: {}", nb);
        }

        if (nb < 1) nb = 1;

        int lwork = lu.ncols() * nb;
        double[] work = new double[lwork];
        intW info = new intW(0);
        LAPACK.getInstance().dgetri(lu.ncols(), lu.data(), lu.ld(), piv, work, lwork, info);

        if (info.val != 0) {
            logger.error("LAPACK DGETRI error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DGETRI error code: " + info.val);
        }

        return lu;
    }

    @Override
    public void solve(double[] b) {
        // B use b as the internal storage. Therefore b will contains the results.
        DenseMatrix B = Matrix.newInstance(b);
        solve(B);
    }

    @Override
    public void solve(DenseMatrix B) {
        int m = lu.nrows();
        int n = lu.ncols();

        if (B.nrows() != m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.nrows(), lu.ncols(), B.nrows(), B.ncols()));
        }

        if (isSingular()) {
            throw new RuntimeException("Matrix is singular.");
        }

        intW info = new intW(0);
        LAPACK.getInstance().dgetrs(NLMatrix.Transpose, lu.nrows(), B.ncols(), lu.data(), lu.ld(), piv, B.data(), B.ld(), info);

        if (info.val < 0) {
            logger.error("LAPACK DGETRS error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DGETRS error code: " + info.val);
        }
    }
}
