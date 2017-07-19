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

import org.netlib.util.doubleW;
import org.netlib.util.intW;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.EVD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ARPACK based eigen decomposition. Currently support only symmetric matrix.
 *
 * @author Haifeng Li
 */
public class ARPACK {
    private static final Logger logger = LoggerFactory.getLogger(ARPACK.class);

    /** Specify which of the Ritz values of OP to compute. */
    public enum Ritz {
        /**
         * compute the NEV largest (algebraic) eigenvalues.
         */
        LA,
        /**
         * compute the NEV smallest (algebraic) eigenvalues.
         */
        SA,
        /**
         * compute the NEV largest (in magnitude) eigenvalues.
         */
        LM,
        /**
         * compute the NEV smallest (in magnitude) eigenvalues.
         */
        SM,
        /**
         * compute NEV eigenvalues, half from each end of the spectrum
         */
        BE
    }

    private static final com.github.fommil.netlib.ARPACK arpack = com.github.fommil.netlib.ARPACK.getInstance();

    /**
     * Solve the eigensystem for the number of eigenvalues requested.
     * <p>
     * NOTE: The references to the eigenvectors will keep alive a reference to a
     * {@code nev * n} double array, so use the {@code copy()} method to free it
     * up if only a subset is required.
     *
     * @param k the number of eigen values/vectors to compute.
     * @param ritz preference for solutions
     */
    public static EVD eigen(Matrix A, int k, Ritz ritz) {
        return eigen(A, k, ritz, 1E-6);
    }

    /**
     * Solve the eigensystem for the number of eigenvalues requested.
     * <p>
     * NOTE: The references to the eigenvectors will keep alive a reference to a
     * {@code nev * n} double array, so use the {@code copy()} method to free it
     * up if only a subset is required.
     *
     * @param k Number of eigenvalues of OP to be computed. 0 < NEV < N.
     * @param ritz Specify which of the Ritz values of OP to compute.
     * @param kappa Relative accuracy of ritz values acceptable as eigenvalues.
     */
    public static EVD eigen(Matrix A, int k, Ritz ritz, double kappa) {
        if (A.nrows() != A.ncols()) {
            throw new IllegalArgumentException("Matrix is not symmetric");
        }

        int n = A.nrows();

        if (k <= 0 || k >= n) {
            throw new IllegalArgumentException("Invalid NEV parameter k: " + k);
        }

        intW nev = new intW(k);

        int ncv = Math.min(2 * k, n);

        String bmat = "I";
        String which = ritz.name();
        doubleW tol = new doubleW(kappa);
        intW info = new intW(0);
        int[] iparam = new int[11];
        iparam[0] = 1;
        iparam[2] = 300;
        iparam[6] = 1;
        intW ido = new intW(0);

        // used for initial residual (if info != 0)
        // and eventually the output residual
        double[] resid = new double[n];
        // Lanczos basis vectors
        double[] v = new double[n * ncv];
        // Arnoldi reverse communication
        double[] workd = new double[3 * n];
        // private work array
        double[] workl = new double[ncv * (ncv + 8)];
        int[] ipntr = new int[11];

        int iter = 0;
        while (true) {
            iter++;
            arpack.dsaupd(ido, bmat, n, which, nev.val, tol, resid, ncv, v, n, iparam, ipntr, workd, workl, workl.length, info);

            if (ido.val == 99) {
                break;
            }

            if (ido.val != -1 && ido.val != 1) {
                throw new IllegalStateException("ARPACK DSAUPD ido = " + ido.val);
            }

            av(A, workd, ipntr[0] - 1, ipntr[1] - 1);
        }

        logger.info("ARPACK: " + iter + " iterations for Matrix of size " + n);

        if (info.val != 0) {
            throw new IllegalStateException("ARPACK DSAUPD error code: " + info.val);
        }

        double[] d = new double[nev.val];
        boolean[] select = new boolean[ncv];
        double[] z = java.util.Arrays.copyOfRange(v, 0, nev.val * n);

        arpack.dseupd(true, "A", select, d, z, n, 0, bmat, n, which, nev, tol.val, resid, ncv, v, n, iparam, ipntr, workd, workl, workl.length, info);

        if (info.val != 0) {
            throw new IllegalStateException("ARPACK DSEUPD error code: " + info.val);
        }

        int computed = iparam[4];
        logger.info("ARPACK computed " + computed + " eigenvalues");

        DenseMatrix V = new NLMatrix(n, nev.val, z);
        NLMatrix.reverse(d, V);
        return new EVD(V, d);
    }

    private static void av(Matrix A, double[] work, int inputOffset, int outputOffset) {
        int n = A.ncols();
        double[] x = new double[A.ncols()];
        System.arraycopy(work, inputOffset, x, 0, n);
        double[] y = new double[A.ncols()];
        A.ax(x, y);
        System.arraycopy(y, 0, work, outputOffset, n);
    }
}