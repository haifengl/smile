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

package smile.math.matrix;

import java.nio.DoubleBuffer;
import java.util.Arrays;
import static smile.math.blas.Layout.*;
import static org.bytedeco.arpackng.global.arpack.*;

/**
 * ARPACK is a collection of Fortran77 subroutines designed to
 * solve large scale eigenvalue problems.
 * <p>
 * The package is designed to compute a few eigenvalues and
 * corresponding eigenvectors of a general n by n matrix A.
 * It is most appropriate for large sparse or structured
 * matrices A where structured means that a matrix-vector
 * product requires order n rather than the usual order n^2
 * floating point operations. This software is based upon an
 * algorithmic variant of the Arnoldi process called the
 * Implicitly Restarted Arnoldi Method (IRAM). When the matrix
 * A is symmetric it reduces to a variant of the Lanczos process
 * called the Implicitly Restarted Lanczos Method (IRLM).
 * These variants may be viewed as a synthesis of the Arnoldi/Lanczos
 * process with the Implicitly Shifted QR technique that is suitable
 * for large scale problems. For many standard problems, a matrix
 * factorization is not required. Only the action of the matrix on
 * a vector is needed.
 *
 * @author Haifeng Li
 */
public interface ARPACK {
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ARPACK.class);

    /** Which eigenvalues of symmetric matrix to compute. */
    enum SymmWhich {
        /**
         * The largest algebraic eigenvalues.
         */
        LA,
        /**
         * The smallest algebraic eigenvalues.
         */
        SA,
        /**
         * The eigenvalues largest in magnitude.
         */
        LM,
        /**
         * The eigenvalues smallest in magnitude.
         */
        SM,
        /**
         * Computes nev eigenvalues, half from each end of the spectrum.
         * When nev is odd, computes one more from the high end than
         * from the low end.
         */
        BE
    }

    /** Which eigenvalues of asymmetric matrix to compute. */
    enum AsymmWhich {
        /**
         * The eigenvalues largest in magnitude.
         */
        LM,
        /**
         * The eigenvalues smallest in magnitude.
         */
        SM,
        /**
         * The eigenvalues of largest real part.
         */
        LR,
        /**
         * The eigenvalues of smallest real part.
         */
        SR,
        /**
         * The eigenvalues of largest imaginary part.
         */
        LI,
        /**
         * The eigenvalues of smallest imaginary part.
         */
        SI
    }

    /**
     * Computes k eigenvalues of a symmetric matrix.
     *
     * @param nev the number of eigenvalues of OP to be computed. 0 &lt; k &lt; n.
     * @param which which eigenvalues to compute.
     */
    static Matrix.EVD syev(DMatrix A, int nev, SymmWhich which) {
        return syev(A, nev, which, 0.0);
    }

    /**
     * Computes k eigenvalues of a symmetric matrix.
     *
     * @param nev the number of eigenvalues of OP to be computed. 0 &lt; k &lt; n.
     * @param which which eigenvalues to compute.
     * @param tol the stopping criterion.
     */
    static Matrix.EVD syev(DMatrix A, int nev, SymmWhich which, double tol) {
        if (A.nrows() != A.ncols()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.nrows(), A.ncols()));
        }

        int n = A.nrows();

        if (nev <= 0 || nev >= n) {
            throw new IllegalArgumentException("Invalid NEV parameter k: " + nev);
        }

        int[] ido = {0};
        int[] info = {0};
        byte[] bmat = {'I'}; // standard eigenvalue problem
        byte[] bwhich = which.toString().getBytes();
        int ncv = Math.min(3 * nev, n);

        int[] iparam = new int[11];
        iparam[0] = 1;
        iparam[2] = 300;
        iparam[6] = 1; // mode

        int[] ipntr = new int[11];
        // Arnoldi reverse communication
        double[] workd = new double[3 * n];
        // private work array
        double[] workl = new double[ncv * (ncv + 8)];

        // used for initial residual (if info != 0)
        // and eventually the output residual
        double[] resid = new double[n];
        // Lanczos basis vectors
        double[] V = new double[n * ncv];
        int ldv = n;

        do {
            dsaupd_c(ido, bmat, n, bwhich, nev, tol, resid, ncv, V, ldv, iparam, ipntr,
                     workd, workl, workl.length, info);

            A.mv(workd, ipntr[0] - 1, ipntr[1] - 1);
        } while (ido[0] == -1 || ido[0] == 1);

        if (info[0] < 0) {
            throw new IllegalStateException("ARPACK DSAUPD error code: " + info[0]);
        }

        info[0] = 0;
        byte[] all = {'A'};
        double[] d = new double[nev];
        int[] select = new int[ncv];
        double[] z = new double[(n+1) * (nev+1)];
        int ldz = n + 1;
        double sigma = 0.0;
        boolean rvec = true;

        dseupd_c(rvec, all, select, d, z, ldz, sigma,
                 bmat, n, bwhich, nev, tol, resid, ncv, V, ldv, iparam, ipntr,
                 workd, workl, workl.length, info);

        if (info[0] != 0) {
            String error = "ARPACK DSEUPD error code: " + info[0];
            if (info[0] == 1) {
                error = "ARPACK DSEUPD error: Maximum number of iterations reached.";
            } else if (info[0] == 3) {
                error = "ARPACK DSEUPD error: No shifts could be applied during implicit Arnoldi update, try increasing NCV.";
            }
            throw new IllegalStateException(error);
        }

        nev = iparam[4]; // number of found eigenvalues
        logger.info("ARPACK computed " + nev + " eigenvalues");

        Matrix.EVD eig = new Matrix.EVD(d, Matrix.of(COL_MAJOR, n, nev, ldv, DoubleBuffer.wrap(Arrays.copyOfRange(V, 0, n * nev))));
        return eig.sort();
    }
}
