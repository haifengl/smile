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

package smile.math.matrix;

import java.util.Arrays;
import static org.bytedeco.arpackng.global.arpack.*;

/**
 * ARPACK is a collection of Fortran77 subroutines designed to
 * solve large scale eigenvalue problems.
 * <p>
 * The package is designed to compute a few eigenvalues and
 * corresponding eigenvectors of a general n by n matrix A.
 * It is most appropriate for large sparse or structured
 * matrices A where structured means that a matrix-vector
 * product requires order n rather than the usual order O(n<sup>2</sup>)
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
public class ARPACK {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ARPACK.class);

    /** Which eigenvalues of symmetric matrix to compute. */
    public enum SymmOption {
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
    public enum AsymmOption {
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

    /** Private constructor to prevent instance creation. */
    private ARPACK() {

    }

    /**
     * Computes NEV eigenvalues of a symmetric double precision matrix.
     *
     * @param A the matrix to decompose.
     * @param which which eigenvalues to compute.
     * @param nev the number of eigenvalues of OP to be computed. {@code 0 < nev < n}.
     * @return the eigen decomposition.
     */
    public static Matrix.EVD syev(IMatrix A, SymmOption which, int nev) {
        return syev(A, which, nev, Math.min(3 * nev, A.nrow()), 1E-6);
    }

    /**
     * Computes NEV eigenvalues of a symmetric double precision matrix.
     *
     * @param A the matrix to decompose.
     * @param which which eigenvalues to compute.
     * @param nev the number of eigenvalues of OP to be computed. {@code 0 < nev < n}.
     * @param ncv the number of Arnoldi vectors.
     * @param tol the stopping criterion.
     * @return the eigen decomposition.
     */
    public static Matrix.EVD syev(IMatrix A, SymmOption which, int nev, int ncv, double tol) {
        if (A.nrow() != A.ncol()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.nrow(), A.ncol()));
        }

        int n = A.nrow();

        if (nev <= 0 || nev >= n) {
            throw new IllegalArgumentException("Invalid NEV parameter k: " + nev);
        }

        int[] ido = {0};
        int[] info = {0};
        byte[] bmat = {'I'}; // standard eigenvalue problem
        String swhich = which.name();
        byte[] bwhich = {(byte) swhich.charAt(0), (byte) swhich.charAt(1)};

        int[] iparam = new int[11];
        iparam[0] = 1;
        iparam[2] = 10 * n;
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

            if (ido[0] == -1 || ido[0] == 1) {
                A.mv(workd, ipntr[0] - 1, ipntr[1] - 1);
            }
        } while (ido[0] == -1 || ido[0] == 1);

        if (info[0] < 0) {
            throw new IllegalStateException("ARPACK DSAUPD error code: " + info[0]);
        }

        info[0] = 0;
        byte[] howmny = {'A'};
        double[] d = new double[ncv * 2];
        int[] select = new int[ncv];
        double sigma = 0.0;
        int rvec = 1;

        dseupd_c(rvec, howmny, select, d, V, ldv, sigma,
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

        d = Arrays.copyOfRange(d, 0, nev);
        V = Arrays.copyOfRange(V, 0, n * nev);
        Matrix.EVD eig = new Matrix.EVD(d, new Matrix(n, nev, ldv, V));
        return eig.sort();
    }

    /**
     * Computes NEV eigenvalues of an asymmetric double precision matrix.
     *
     * @param A the matrix to decompose.
     * @param which which eigenvalues to compute.
     * @param nev the number of eigenvalues of OP to be computed. {@code 0 < nev < n}.
     * @return the eigen decomposition.
     */
    public static Matrix.EVD eigen(IMatrix A, AsymmOption which, int nev) {
        return eigen(A, which, nev, Math.min(3 * nev, A.nrow()), 1E-6);
    }

    /**
     * Computes NEV eigenvalues of an asymmetric double precision matrix.
     *
     * @param A the matrix to decompose.
     * @param which which eigenvalues to compute.
     * @param nev the number of eigenvalues of OP to be computed. {@code 0 < nev < n}.
     * @param ncv the number of Arnoldi vectors.
     * @param tol the stopping criterion.
     * @return the eigen decomposition.
     */
    public static Matrix.EVD eigen(IMatrix A, AsymmOption which, int nev, int ncv, double tol) {
        if (A.nrow() != A.ncol()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.nrow(), A.ncol()));
        }

        int n = A.nrow();

        if (nev <= 0 || nev >= n) {
            throw new IllegalArgumentException("Invalid NEV: " + nev);
        }

        int[] ido = {0};
        int[] info = {0};
        byte[] bmat = {'I'}; // standard eigenvalue problem
        String swhich = which.name();
        byte[] bwhich = {(byte) swhich.charAt(0), (byte) swhich.charAt(1)};

        int[] iparam = new int[11];
        iparam[0] = 1;
        iparam[2] = 10 * n;
        iparam[6] = 1; // mode

        int[] ipntr = new int[14];
        // Arnoldi reverse communication
        double[] workd = new double[3 * n];
        double[] workev = new double[3 * ncv];
        // private work array
        double[] workl = new double[3*ncv*ncv + 6*ncv];

        // used for initial residual (if info != 0)
        // and eventually the output residual
        double[] resid = new double[n];
        // Lanczos basis vectors
        double[] V = new double[n * ncv];
        int ldv = n;

        do {
            dnaupd_c(ido, bmat, n, bwhich, nev, tol, resid, ncv, V, ldv, iparam, ipntr,
                    workd, workl, workl.length, info);

            if (ido[0] == -1 || ido[0] == 1) {
                A.mv(workd, ipntr[0] - 1, ipntr[1] - 1);
            }
        } while (ido[0] == -1 || ido[0] == 1);

        if (info[0] < 0) {
            throw new IllegalStateException("ARPACK DNAUPD error code: " + info[0]);
        }

        info[0] = 0;
        byte[] howmny = {'A'};
        double[] wr = new double[ncv * 2];
        double[] wi = new double[ncv * 2];
        int[] select = new int[ncv];
        double sigmar = 0.0;
        double sigmai = 0.0;
        int rvec = 1;

        dneupd_c(rvec, howmny, select, wr, wi, V, ldv, sigmar, sigmai, workev,
                bmat, n, bwhich, nev, tol, resid, ncv, V, ldv, iparam, ipntr,
                workd, workl, workl.length, info);

        if (info[0] != 0) {
            String error = "ARPACK DNEUPD error code: " + info[0];
            if (info[0] == 1) {
                error = "ARPACK DNEUPD error: Maximum number of iterations reached.";
            } else if (info[0] == 3) {
                error = "ARPACK DNEUPD error: No shifts could be applied during implicit Arnoldi update, try increasing NCV.";
            }
            throw new IllegalStateException(error);
        }

        nev = iparam[4]; // number of found eigenvalues
        logger.info("ARPACK computed " + nev + " eigenvalues");

        wr = Arrays.copyOfRange(wr, 0, nev);
        wi = Arrays.copyOfRange(wi, 0, nev);
        V = Arrays.copyOfRange(V, 0, n * nev);
        Matrix.EVD eig = new Matrix.EVD(wr, wi, null, new Matrix(n, nev, ldv, V));
        return eig.sort();
    }

    /**
     * Computes k largest approximate singular triples of a matrix.
     *
     * @param A the matrix to decompose.
     * @param k the number of singular triples to compute.
     * @return the singular value decomposition.
     */
    public static Matrix.SVD svd(IMatrix A, int k) {
        return svd(A, k, Math.min(3 * k, Math.min(A.nrow(), A.ncol())), 1E-6);
    }

    /**
     * Computes k largest approximate singular triples of a matrix.
     *
     * @param A the matrix to decompose.
     * @param k the number of singular triples to compute.
     * @param ncv the number of Arnoldi vectors.
     * @param tol the stopping criterion.
     * @return the singular value decomposition.
     */
    public static Matrix.SVD svd(IMatrix A, int k, int ncv, double tol) {
        int m = A.nrow();
        int n = A.ncol();

        IMatrix ata = A.square();
        Matrix.EVD eigen = syev(ata, SymmOption.LM, k, ncv, tol);

        double[] s = eigen.wr;
        for (int i = 0; i < s.length; i++) {
            s[i] = Math.sqrt(s[i]);
        }

        if (m >= n) {
            Matrix V = eigen.Vr;

            double[] Av = new double[m];
            double[] v = new double[n];
            Matrix U = new Matrix(m, s.length);
            for (int j = 0; j < s.length; j++) {
                for (int i = 0; i < n; i++) {
                    v[i] = V.get(i, j);
                }

                A.mv(v, Av);

                for (int i = 0; i < m; i++) {
                    U.set(i, j, Av[i] / s[j]);
                }
            }

            return new Matrix.SVD(s, U, V);
        } else {
            Matrix U = eigen.Vr;

            double[] Atu = new double[n];
            double[] u = new double[m];
            Matrix V = new Matrix(n, s.length);
            for (int j = 0; j < s.length; j++) {
                for (int i = 0; i < m; i++) {
                    u[i] = U.get(i, j);
                }

                A.tv(u, Atu);

                for (int i = 0; i < n; i++) {
                    V.set(i, j, Atu[i] / s[j]);
                }
            }

            return new Matrix.SVD(s, U, V);
        }
    }
}
