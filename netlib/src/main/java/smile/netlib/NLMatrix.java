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

import smile.math.matrix.DenseMatrix;
import smile.math.matrix.JMatrix;
import smile.math.matrix.SVD;
import smile.math.matrix.EVD;
import smile.sort.QuickSort;
import com.github.fommil.netlib.BLAS;
import com.github.fommil.netlib.LAPACK;
import org.netlib.util.intW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Column-major matrix that employs netlib for matrix-vector and matrix-matrix
 * computation.
 *
 * @author Haifeng Li
 */
public class NLMatrix extends JMatrix {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(NLMatrix.class);

    static String NoTranspose = "N";
    static String Transpose   = "T";
    static String ConjugateTranspose = "C";

    static String Upper = "U";
    static String Lower = "L";

    static String Left = "L";
    static String Right = "R";

    /** The diagonal elements are assumed to be 1. */
    static String UnitTriangular = "U";
    static String NonUnitTriangular = "N";

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public NLMatrix(double[][] A) {
        super(A);
    }

    /**
     * Constructor of a column vector/matrix with given array as the internal storage.
     * @param A the array of column vector.
     */
    public NLMatrix(double[] A) {
        super(A);
    }

    /**
     * Constructor of all-zero matrix.
     */
    public NLMatrix(int rows, int cols) {
        super(rows, cols);
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public NLMatrix(int rows, int cols, double value) {
        super(rows, cols, value);
    }

    /**
     * Constructor.
     * @param value the array of matrix values arranged in column major format
     */
    public NLMatrix(int rows, int cols, double[] value) {
        super(rows, cols, value);
    }

    @Override
    public NLMatrix copy() {
        return new NLMatrix(nrows(), ncols(), data().clone());
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        BLAS.getInstance().dgemv(NoTranspose, nrows(), ncols(), 1.0, data(), ld(), x, 1, 0.0, y, 1);
        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        BLAS.getInstance().dgemv(NoTranspose, nrows(), ncols(), 1.0, data(), ld(), x, 1, 1.0, y, 1);
        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        BLAS.getInstance().dgemv(NoTranspose, nrows(), ncols(), 1.0, data(), ld(), x, 1, b, y, 1);
        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        BLAS.getInstance().dgemv(Transpose, nrows(), ncols(), 1.0, data(), ld(), x, 1, 0.0, y, 1);
        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        BLAS.getInstance().dgemv(Transpose, nrows(), ncols(), 1.0, data(), ld(), x, 1, 1.0, y, 1);
        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        BLAS.getInstance().dgemv(Transpose, nrows(), ncols(), 1.0, data(), ld(), x, 1, b, y, 1);
        return y;
    }

    @Override
    public NLMatrix ata() {
        return atbmm(this);
    }

    @Override
    public NLMatrix aat() {
        return abtmm(this);
    }

    @Override
    public NLMatrix abmm(DenseMatrix B) {
        if (B instanceof JMatrix) {
            int m = nrows();
            int n = B.ncols();
            int k = ncols();
            NLMatrix C = new NLMatrix(m, n);
            BLAS.getInstance().dgemm(NoTranspose, NoTranspose,
                    m, n, k, 1.0, data(), m, B.data(),
                    k, 0.0, C.data(), m);
            return C;
        }

        throw new IllegalArgumentException("NLMatrix.abmm() parameter must be JMatrix");
    }

    @Override
    public NLMatrix abtmm(DenseMatrix B) {
        if (B instanceof JMatrix) {
            int m = nrows();
            int n = B.nrows();
            int k = ncols();
            NLMatrix C = new NLMatrix(m, n);
            BLAS.getInstance().dgemm(NoTranspose, Transpose,
                    m, n, k, 1.0, data(), m, B.data(),
                    n, 0.0, C.data(), m);
            return C;
        }

        throw new IllegalArgumentException("NLMatrix.abtmm() parameter must be JMatrix");
    }

    @Override
    public NLMatrix atbmm(DenseMatrix B) {
        if (B instanceof JMatrix) {
            int m = ncols();
            int n = B.ncols();
            int k = nrows();
            NLMatrix C = new NLMatrix(m, n);
            BLAS.getInstance().dgemm(Transpose, NoTranspose,
                    m, n, k, 1.0, data(), k, B.data(),
                    k, 0.0, C.data(), m);
            return C;
        }

        throw new IllegalArgumentException("NLMatrix.atbmm() parameter must be JMatrix");
    }

    @Override
    public NLMatrix transpose() {
        NLMatrix B = new NLMatrix(ncols(), nrows());
        for (int i = 0; i < nrows(); i++) {
            for (int j = 0; j < ncols(); j++) {
                B.set(j, i, get(i, j));
            }
        }

        return B;
    }

    @Override
    public LU lu() {
        boolean singular = false;

        int[] piv = new int[Math.min(nrows(), ncols())];
        intW info = new intW(0);
        LAPACK.getInstance().dgetrf(nrows(), ncols(), data(), ld(), piv, info);

        if (info.val > 0) {
            singular = true;
        }

        if (info.val < 0) {
            logger.error("LAPACK DGETRF error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DGETRF error code: " + info.val);
        }

        return new LU(this, piv, singular);
    }

    @Override
    public Cholesky cholesky() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Cholesky decomposition on non-square matrix");
        }

        intW info = new intW(0);
        LAPACK.getInstance().dpotrf(NLMatrix.Lower, nrows(), data(), ld(), info);

        if (info.val > 0) {
            logger.error("LAPACK DPOTRF error code: {}", info.val);
            throw new IllegalArgumentException("The matrix is not positive definite.");
        }

        if (info.val < 0) {
            logger.error("LAPACK DPOTRF error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DPOTRF error code: " + info.val);
        }

        return new Cholesky(this);
    }

    @Override
    public QR qr() {
        boolean singular = false;

        int m = nrows();
        int n = ncols();

        // Query optimal workspace.
        double[] work = new double[1];
        intW info = new intW(0);
        LAPACK.getInstance().dgeqrf(m, n, new double[0], m, new double[0], work, -1, info);

        int lwork = n;
        if (info.val == 0) {
            lwork = (int) work[0];
            logger.debug("LAPACK DEGQRF returns work space size: {}", lwork);
        } else {
            logger.warn("LAPACK DEGQRF error code: {}", info.val);
        }

        lwork = Math.max(1, lwork);
        work = new double[lwork];

        info.val = 0;
        double[] tau = new double[Math.min(nrows(), ncols())];
        LAPACK.getInstance().dgeqrf(nrows(), ncols(), data(), ld(), tau, work, lwork, info);

        if (info.val > 0) {
            singular = true;
        }

        if (info.val < 0) {
            logger.error("LAPACK DGETRF error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DGETRF error code: " + info.val);
        }

        return new QR(this, tau, singular);
    }

    @Override
    public SVD svd() {
        int m = nrows();
        int n = ncols();
        int mx = Math.max(m, n);
        int mn = Math.min(m, n);

        NLMatrix U  = m >= n ? this : new NLMatrix(m, n);
        NLMatrix Vt = m >= n ? new NLMatrix(n, n) : this;
        double[] s = new double[mn];
        int[] iwork = new int[8*mn];

        // Query optimal workspace.
        double[] work = new double[1];
        intW info = new intW(0);
        LAPACK.getInstance().dgesdd("O", m, n, data(), ld(), s, U.data(), U.ld(), Vt.data(), Vt.ld(), work, -1, iwork, info);

        int lwork = 3 * mn + Math.max(mx, 5*mn*mn + 4 *mn);
        if (info.val == 0) {
            lwork = (int) work[0];
            logger.debug("LAPACK DGESDD returns work space size: {}", lwork);
        } else {
            logger.warn("LAPACK DGESDD error code: {}", info.val);
        }

        lwork = Math.max(1, lwork);
        work = new double[lwork];

        info.val = 0;
        LAPACK.getInstance().dgesdd("O", m, n, data(), ld(), s, U.data(), U.ld(), Vt.data(), Vt.ld(), work, lwork, iwork, info);

        if (info.val != 0) {
            logger.error("LAPACK DGESDD error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DGESDD error code: " + info.val);
        }

        return new SVD(U, Vt.transpose(), s);
    }

    @Override
    public double[] eig() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Eigen decomposition on non-square matrix");
        }

        int n = nrows();

        if (isSymmetric()) {
            double[] V  = new double[0];
            double[] d = new double[n];

            double abstol = LAPACK.getInstance().dlamch("Safe minimum");

            // Query optimal workspace.
            int[] isuppz = new int[2 * Math.max(1, n)];
            double[] work = new double[1];
            int[] iwork = new int[1];
            intW m = new intW(0);
            intW info = new intW(0);
            LAPACK.getInstance().dsyevr("N", "A",
                    NLMatrix.Lower, n, data(), ld(), 0, 0, 0, 0, abstol,
                    m, d, V, 1, isuppz, work, -1, iwork, -1, info);

            int lwork = 26 * n;
            int liwork = 10 * n;
            if (info.val == 0) {
                lwork = (int) work[0];
                liwork = (int) iwork[0];
                logger.debug("LAPACK DSYEVR returns work space size: {}", lwork);
            } else {
                logger.warn("LAPACK DSYEVR error code: {}", info.val);
            }

            lwork = Math.max(1, lwork);
            liwork = Math.max(1, liwork);
            work = new double[lwork];
            iwork = new int[liwork];

            m.val = 0;
            info.val = 0;
            LAPACK.getInstance().dsyevr("N", "A",
                    NLMatrix.Lower, n, data(), ld(), 0, 0, 0, 0, abstol,
                    m, d, V, 1, isuppz, work, lwork, iwork, liwork, info);

            if (info.val != 0) {
                logger.error("LAPACK DSYEVR error code: {}", info.val);
                throw new IllegalArgumentException("LAPACK DSYEVR error code: " + info.val);
            }

            // LAPACK returns eigen values in ascending order.
            // In contrast, JMatrix returns eigen values in descending order.
            // Reverse the array to match JMatrix.
            double[] w = new double[2*n];
            for (int i = 0; i < n; i++) {
                w[i] = d[n - i - 1];
            }
            return w;

        } else {
            double[] V  = new double[0];
            double[] d = new double[n];
            double[] e = new double[n];

            // Query optimal workspace.
            double[] work = new double[1];
            intW info = new intW(0);
            LAPACK.getInstance().dgeev("N", "N", n, data(), ld(), d, e, V, 1, V, 1, work, -1, info);

            int lwork = 4 * n;
            if (info.val == 0) {
                lwork = (int) work[0];
                logger.debug("LAPACK DGEEV returns work space size: {}", lwork);
            } else {
                logger.warn("LAPACK DGEEV error code: {}", info.val);
            }

            lwork = Math.max(1, lwork);
            work = new double[lwork];

            info.val = 0;
            LAPACK.getInstance().dgeev("N", "N", n, data(), ld(), d, e, V, 1, V, 1, work, lwork, info);

            if (info.val != 0) {
                logger.error("LAPACK DGEEV error code: {}", info.val);
                throw new IllegalArgumentException("LAPACK DGEEV error code: " + info.val);
            }

            // LAPACK returns eigen values in undefined order.
            // In contrast, JMatrix returns eigen values in descending order.
            // Sort the array to match JMatrix.
            sort(d, e);
            double[] w = new double[2*n];
            System.arraycopy(d, 0, w, 0, n);
            System.arraycopy(e, 0, w, n, n);
            return w;
        }
    }

    @Override
    public EVD eigen() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Eigen decomposition on non-square matrix");
        }

        int n = nrows();

        if (isSymmetric()) {
            NLMatrix V  = new NLMatrix(n, n);
            double[] d = new double[n];

            double abstol = LAPACK.getInstance().dlamch("Safe minimum");

            // Query optimal workspace.
            int[] isuppz = new int[2 * Math.max(1, n)];
            double[] work = new double[1];
            int[] iwork = new int[1];
            intW m = new intW(0);
            intW info = new intW(0);
            LAPACK.getInstance().dsyevr("V", "A",
                    NLMatrix.Lower, n, data(), ld(), 0, 0, 0, 0, abstol,
                    m, d, V.data(), V.ld(), isuppz, work, -1, iwork, -1, info);

            int lwork = 26 * n;
            int liwork = 10 * n;
            if (info.val == 0) {
                lwork = (int) work[0];
                liwork = (int) iwork[0];
                logger.debug("LAPACK DSYEVR returns work space size: {}", lwork);
            } else {
                logger.warn("LAPACK DSYEVR error code: {}", info.val);
            }

            lwork = Math.max(1, lwork);
            liwork = Math.max(1, liwork);
            work = new double[lwork];
            iwork = new int[liwork];

            m.val = 0;
            info.val = 0;
            LAPACK.getInstance().dsyevr("V", "A",
                    NLMatrix.Lower, n, data(), ld(), 0, 0, 0, 0, abstol,
                    m, d, V.data(), V.ld(), isuppz, work, lwork, iwork, liwork, info);

            if (info.val != 0) {
                logger.error("LAPACK DSYEVR error code: {}", info.val);
                throw new IllegalArgumentException("LAPACK DSYEVR error code: " + info.val);
            }

            reverse(d, V);
            return new EVD(V, d);

        } else {
            NLMatrix V  = new NLMatrix(n, n);
            double[] d = new double[n];
            double[] e = new double[n];

            // Query optimal workspace.
            double[] work = new double[1];
            intW info = new intW(0);
            LAPACK.getInstance().dgeev("N", "V", n, data(), ld(), d, e, V.data(), 1, V.data(), V.ld(), work, -1, info);

            int lwork = 4 * n;
            if (info.val == 0) {
                lwork = (int) work[0];
                logger.debug("LAPACK DGEEV returns work space size: {}", lwork);
            } else {
                logger.warn("LAPACK DGEEV error code: {}", info.val);
            }

            lwork = Math.max(1, lwork);
            work = new double[lwork];

            info.val = 0;
            LAPACK.getInstance().dgeev("N", "V", n, data(), ld(), d, e, new double[0], 1, V.data(), V.ld(), work, lwork, info);

            if (info.val != 0) {
                logger.error("LAPACK DGEEV error code: {}", info.val);
                throw new IllegalArgumentException("LAPACK DGEEV error code: " + info.val);
            }

            // LAPACK returns eigen values in undefined order.
            // In contrast, JMatrix returns eigen values in descending order.
            // Sort the array to match JMatrix.
            sort(d, e, V);
            return new EVD(V, d, e);
        }
    }

    // LAPACK/ARPACK returns eigen values in ascending order.
    // In contrast, JMatrix returns eigen values in descending order.
    // Reverse the array to match JMatrix.
    static void reverse(double[] d, DenseMatrix V) {
        int m = V.nrows();
        int n = d.length;
        int half = n / 2;
        for (int i = 0; i < half; i++) {
            double tmp = d[i];
            d[i] = d[n - i - 1];
            d[n - i - 1] = tmp;
        }
        for (int j = 0; j < half; j++) {
            for (int i = 0; i < m; i++) {
                double tmp = V.get(i, j);
                V.set(i, j, V.get(i, n - j - 1));
                V.set(i, n - j - 1, tmp);
            }
        }
    }
}
