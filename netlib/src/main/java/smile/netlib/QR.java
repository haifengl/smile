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
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Cholesky;
import com.github.fommil.netlib.LAPACK;
import org.netlib.util.intW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For an m-by-n matrix A with m &ge; n, the QR decomposition is an m-by-n
 * orthogonal matrix Q and an n-by-n upper triangular matrix R such that
 * A = Q*R.
 * <p>
 * The QR decomposition always exists, even if the matrix does not have
 * full rank. The primary use of the QR decomposition is in the least squares
 * solution of non-square systems of simultaneous linear equations, where
 * {@link #isSingular()} has to be false.
 * <p>
 * QR decomposition is also the basis for a particular eigenvalue algorithm,
 * the QR algorithm.
 *
 * @author Haifeng Li
 */
public class QR extends smile.math.matrix.QR {
    private static final Logger logger = LoggerFactory.getLogger(QR.class);

    /**
     * Constructor.
     */
    public QR(DenseMatrix qr, double[] tau, boolean singular) {
        super(qr, tau, singular);
    }

    /**
     * Returns the Cholesky decomposition of A'A.
     */
    @Override
    public Cholesky CholeskyOfAtA() {
        int n = qr.ncols();

        DenseMatrix L = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                L.set(i, j, qr.get(j, i));
            }
        }

        return new Cholesky(L);
    }

    @Override
    public DenseMatrix getR() {
        int m = qr.nrows();
        int n = qr.ncols();
        DenseMatrix R = Matrix.zeros(m, n);
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                R.set(i, j, qr.get(i, j));
            }
        }
        return R;
    }

    @Override
    public DenseMatrix getQ() {
        int m = qr.nrows();
        int n = qr.ncols();
        int k = Math.min(m, n);

        intW info = new intW(0);
        double[] work = new double[1];
        LAPACK.getInstance().dorgqr(m, n, k, qr.data(), qr.ld(), tau, work, -1, info);

        int lwork = n;
        if (info.val == 0) {
            lwork = (int) work[0];
            logger.debug("LAPACK DORGQR returns work space size: {}", lwork);
        } else {
            logger.warn("LAPACK DORGQR error code: {}", info.val);
        }

        lwork = Math.max(1, lwork);
        work = new double[lwork];

        info.val = 0;
        LAPACK.getInstance().dorgqr(m, n, k, qr.data(), qr.ld(), tau, work, lwork, info);

        if (info.val < 0) {
            logger.error("LAPACK DORGQR error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DORGQR error code: " + info.val);
        }

        return qr;
    }

    @Override
    public void solve(double[] b, double[] x) {
        if (b.length != qr.nrows()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", qr.nrows(), qr.nrows(), b.length));
        }

        if (x.length != qr.ncols()) {
            throw new IllegalArgumentException("A and x dimensions don't match.");
        }

        if (singular) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        double[] B = b.clone();
        solve(Matrix.newInstance(B));
        System.arraycopy(B, 0, x, 0, x.length);
    }

    @Override
    public void solve(DenseMatrix B) {
        if (B.nrows() != qr.nrows()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrows(), qr.nrows(), B.nrows(), B.ncols()));
        }

        if (singular) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        int m = qr.nrows();
        int n = qr.ncols();
        int k = Math.min(m, n);

        intW info = new intW(0);
        double[] work = new double[1];
        LAPACK.getInstance().dormqr(NLMatrix.Left, NLMatrix.Transpose, B.nrows(), B.ncols(), k, qr.data(), qr.ld(), tau, B.data(), B.ld(), work, -1, info);

        int lwork = n;
        if (info.val == 0) {
            lwork = (int) work[0];
            logger.debug("LAPACK DORMQR returns work space size: {}", lwork);
        } else {
            logger.warn("LAPACK DORMQR error code: {}", info.val);
        }

        lwork = Math.max(1, lwork);
        work = new double[lwork];

        info.val = 0;
        LAPACK.getInstance().dormqr(NLMatrix.Left, NLMatrix.Transpose, B.nrows(), B.ncols(), k, qr.data(), qr.ld(), tau, B.data(), B.ld(), work, lwork, info);

        if (info.val < 0) {
            logger.error("LAPACK DORMQR error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DORMQR error code: " + info.val);
        }

        info.val = 0;
        LAPACK.getInstance().dtrtrs(NLMatrix.Upper, NLMatrix.NoTranspose, NLMatrix.NonUnitTriangular, qr.ncols(), B.ncols(), qr.data(), qr.ld(), B.data(), B.ld(), info);

        if (info.val != 0) {
            logger.error("LAPACK DTRTRS error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DTRTRS error code: " + info.val);
        }
    }
}
