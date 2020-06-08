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

package smile.math.blas;

import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.EVD;
import static org.bytedeco.arpackng.global.arpack.*;

/**
 * ARPACK based eigen decomposition. Currently support only symmetric matrix.
 *
 * @author Haifeng Li
 */
public interface ARPACK {
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ARPACK.class);

    /**
     * Find k approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k Number of eigenvalues of OP to be computed. 0 &lt; k &lt; N.
     * @param ritz Specify which of the Ritz values to compute.
     */
    static EVD eigen(Matrix A, int k, Ritz ritz) {
        return eigen(A, k, ritz, 1E-6, A.nrows());
    }

    /**
     * Find k approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k Number of eigenvalues of OP to be computed. 0 &lt; k &lt; N.
     * @param which Specify which of the Ritz values to compute.
     */
    static EVD eigen(Matrix A, int k, String which) {
        return eigen(A, k, which, 1E-6, A.nrows());
    }

    /**
     * Find k approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k Number of eigenvalues of OP to be computed. 0 &lt; k &lt; N.
     * @param ritz Specify which of the Ritz values to compute.
     * @param kappa Relative accuracy of ritz values acceptable as eigenvalues.
     * @param maxIter Maximum number of iterations.
     */
    static EVD eigen(Matrix A, int k, Ritz ritz, double kappa, int maxIter) {
        return eigen(A, k, ritz.name(), kappa, maxIter);
    }

    /**
     * Find k approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k Number of eigenvalues of OP to be computed. 0 &lt; NEV &lt; N.
     * @param which Specify which of the Ritz values to compute.
     * @param kappa Relative accuracy of ritz values acceptable as eigenvalues.
     * @param maxIter Maximum number of iterations.
     */
    static EVD eigen(Matrix A, int k, String which, double kappa, int maxIter) {
        if (A.nrows() != A.ncols()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.nrows(), A.ncols()));
        }

        if (!A.isSymmetric()) {
            throw new UnsupportedOperationException("This matrix is not symmetric.");
        }

        int n = A.nrows();

        if (k <= 0 || k >= n) {
            throw new IllegalArgumentException("Invalid NEV parameter k: " + k);
        }

        if (kappa <= MathEx.EPSILON) {
            throw new IllegalArgumentException("Invalid tolerance: kappa = " + kappa);
        }

        if (maxIter <= 0) {
            maxIter = 10 * A.nrows();
        }

        double tol = kappa;
        int[] ido = {0};
        int[] info = {0};
        byte[] bmat = {'I'}; // standard eigenvalue problem
        byte[] bwhich = which.getBytes();
        int nev = k;
        int ncv = 2 * nev + 1;

        int[] iparam = new int[11];
        iparam[0] = 1;
        iparam[2] = 10 * n;
        iparam[3] = 1;
        iparam[4] = 0; // number of ev found by arpack.
        iparam[6] = 1;

        int[] ipntr = new int[14];
        // Arnoldi reverse communication
        double[] workd = new double[3*n];
        // private work array
        double[] workl = new double[3*(ncv*ncv) + 6*ncv];

        // used for initial residual (if info != 0)
        // and eventually the output residual
        double[] resid = new double[n];
        // Lanczos basis vectors
        double[] V = new double[n * ncv];
        int ldv = n;

        int iter = 0;
        for (; iter < maxIter; iter++) {
            dsaupd_c(ido, bmat, n, bwhich, nev, tol, resid, ncv, V, ldv, iparam, ipntr,
                     workd, workl, workl.length, info);

            if (ido[0] == 99) {
                break;
            }

            if (ido[0] != -1 && ido[0] != 1) {
                throw new IllegalStateException("ARPACK DSAUPD ido = " + ido[0]);
            }

            mv(A, workd, ipntr[0] - 1, ipntr[1] - 1);
        }

        logger.info("ARPACK: " + iter + " iterations for Matrix of size " + n);

        if (info[0] != 0) {
            if (info[0] == 1) {
                logger.info("ARPACK DSAUPD found all possible eigenvalues: {}", iparam[4]);
            } else {
                throw new IllegalStateException("ARPACK DSAUPD error code: " + info[0]);
            }
        }

        byte[] howmny = {'A'};
        double[] d = new double[nev];
        int[] select = new int[ncv];
        double[] z = new double[(n+1) * (nev+1)];
        int ldz = n + 1;
        double sigma = 0.0;
        boolean rvec = true;

        dseupd_c(rvec, howmny, select, d, z, ldz, sigma,
                 bmat, n, bwhich, nev, tol, resid, ncv, V, ldv, iparam, ipntr,
                 workd, workl, workl.length, info);

        if (info[0] != 0) {
            throw new IllegalStateException("ARPACK DSEUPD error code: " + info[0]);
        }

        int computed = iparam[4];
        logger.info("ARPACK computed " + computed + " eigenvalues");

        //DenseMatrix ev = Matrix.of(n, nev, V);
        //Matrix.reverse(d, ev);
        //return new EVD(ev, d);
        return null;
    }

    static void mv(Matrix A, double[] work, int inputOffset, int outputOffset) {
        int n = A.ncols();
        double[] x = new double[A.ncols()];
        System.arraycopy(work, inputOffset, x, 0, n);
        double[] y = new double[A.ncols()];
        A.ax(x, y);
        System.arraycopy(y, 0, work, outputOffset, n);
    }
}
