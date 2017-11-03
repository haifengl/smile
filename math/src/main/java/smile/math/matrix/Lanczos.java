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
package smile.math.matrix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;

/**
 * The Lanczos algorithm is a direct algorithm devised by Cornelius Lanczos
 * that is an adaptation of power methods to find the most useful eigenvalues
 * and eigenvectors of an n<sup>th</sup> order linear system with a limited
 * number of operations, m, where m is much smaller than n.
 * <p>
 * Although computationally efficient in principle, the method as initially
 * formulated was not useful, due to its numerical instability. In this
 * implementation, we use partial reorthogonalization to make the method
 * numerically stable.
 *
 * @author Haifeng Li
 */
public class Lanczos {
    private static final Logger logger = LoggerFactory.getLogger(Lanczos.class);

    /**
     * Find k largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     */
    public static EVD eigen(Matrix A, int k) {
        return eigen(A, k, 1.0E-8, 10 * A.nrows());
    }

    /**
     * Find k largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     * @param kappa relative accuracy of ritz values acceptable as eigenvalues.
     * @param maxIter Maximum number of iterations.
     */
    public static EVD eigen(Matrix A, int k, double kappa, int maxIter) {
        if (A.nrows() != A.ncols()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.nrows(), A.ncols()));
        }

        if (!A.isSymmetric()) {
            throw new IllegalArgumentException("Matrix is not symmetric.");
        }

        if (k < 1 || k > A.nrows()) {
            throw new IllegalArgumentException("k is larger than the size of A: " + k + " > " + A.nrows());
        }

        if (kappa <= Math.EPSILON) {
            throw new IllegalArgumentException("Invalid tolerance: kappa = " + kappa);
        }

        if (maxIter <= 0) {
            maxIter = 10 * A.nrows();
        }

        int n = A.nrows();
        int intro = 0;

        // roundoff estimate for dot product of two unit vectors
        double eps = Math.EPSILON * Math.sqrt(n);
        double reps = Math.sqrt(Math.EPSILON);
        double eps34 = reps * Math.sqrt(reps);
        kappa = Math.max(kappa, eps34);

        // Workspace
        // wptr[0]             r[j]
        // wptr[1]             q[j]
        // wptr[2]             q[j-1]
        // wptr[3]             p
        // wptr[4]             p[j-1]
        // wptr[5]             temporary worksapce
        double[][] wptr = new double[6][n];

        // orthogonality estimate of Lanczos vectors at step j
        double[] eta = new double[n];
        // orthogonality estimate of Lanczos vectors at step j-1
        double[] oldeta = new double[n];
        // the error bounds
        double[] bnd = new double[n];

        // diagonal elements of T
        double[] alf = new double[n];
        // off-diagonal elements of T
        double[] bet = new double[n + 1];

        // basis vectors for the Krylov subspace
        double[][] q = new double[n][];
        // initial Lanczos vectors
        double[][] p = new double[2][];

        // arrays used in the QL decomposition
        double[] ritz = new double[n + 1];
        // eigenvectors calculated in the QL decomposition
        DenseMatrix z = null;

        // First step of the Lanczos algorithm. It also does a step of extended
        // local re-orthogonalization.
        // get initial vector; default is random
        double rnm = startv(A, q, wptr, 0);

        // normalize starting vector
        double t = 1.0 / rnm;
        Math.scale(t, wptr[0], wptr[1]);
        Math.scale(t, wptr[3]);

        // take the first step
        A.ax(wptr[3], wptr[0]);
        alf[0] = Math.dot(wptr[0], wptr[3]);
        Math.axpy(-alf[0], wptr[1], wptr[0]);
        t = Math.dot(wptr[0], wptr[3]);
        Math.axpy(-t, wptr[1], wptr[0]);
        alf[0] += t;
        Math.copy(wptr[0], wptr[4]);
        rnm = Math.norm(wptr[0]);
        double anorm = rnm + Math.abs(alf[0]);
        double tol = reps * anorm;

        if (0 == rnm) {
            throw new IllegalStateException("Lanczos method was unable to find a starting vector within range.");
        }

        eta[0] = eps;
        oldeta[0] = eps;

        // number of ritz values stabilized
        int neig = 0;
        // number of intitial Lanczos vectors in local orthog. (has value of 0, 1 or 2)
        int ll = 0;

        // start of index through loop
        int first = 1;
        // end of index through loop
        int last = Math.min(k + Math.max(8, k), n);

        // number of Lanczos steps actually taken
        int j = 0;

        // stop flag
        boolean enough = false;

        // algorithm iterations
        int iter = 0;
        for (; !enough && iter < maxIter; iter++) {
            if (rnm <= tol) {
                rnm = 0.0;
            }

            // a single Lanczos step
            for (j = first; j < last; j++) {
                Math.swap(wptr, 1, 2);
                Math.swap(wptr, 3, 4);

                store(q, j - 1, wptr[2]);
                if (j - 1 < 2) {
                    p[j - 1] = wptr[4].clone();
                }
                bet[j] = rnm;

                // restart if invariant subspace is found
                if (0 == bet[j]) {
                    rnm = startv(A, q, wptr, j);
                    if (rnm < 0.0) {
                        rnm = 0.0;
                        break;
                    }

                    if (rnm == 0) {
                        enough = true;
                    }
                }

                if (enough) {
                    // These lines fix a bug that occurs with low-rank matrices
                    Math.swap(wptr, 1, 2);
                    break;
                }

                // take a lanczos step
                t = 1.0 / rnm;
                Math.scale(t, wptr[0], wptr[1]);
                Math.scale(t, wptr[3]);
                A.ax(wptr[3], wptr[0]);
                Math.axpy(-rnm, wptr[2], wptr[0]);
                alf[j] = Math.dot(wptr[0], wptr[3]);
                Math.axpy(-alf[j], wptr[1], wptr[0]);

                // orthogonalize against initial lanczos vectors
                if (j <= 2 && (Math.abs(alf[j - 1]) > 4.0 * Math.abs(alf[j]))) {
                    ll = j;
                }

                for (int i = 0; i < Math.min(ll, j - 1); i++) {
                    t = Math.dot(p[i], wptr[0]);
                    Math.axpy(-t, q[i], wptr[0]);
                    eta[i] = eps;
                    oldeta[i] = eps;
                }

                // extended local reorthogonalization
                t = Math.dot(wptr[0], wptr[4]);
                Math.axpy(-t, wptr[2], wptr[0]);
                if (bet[j] > 0.0) {
                    bet[j] = bet[j] + t;
                }
                t = Math.dot(wptr[0], wptr[3]);
                Math.axpy(-t, wptr[1], wptr[0]);
                alf[j] = alf[j] + t;
                Math.copy(wptr[0], wptr[4]);
                rnm = Math.norm(wptr[0]);
                anorm = bet[j] + Math.abs(alf[j]) + rnm;
                tol = reps * anorm;

                // update the orthogonality bounds
                ortbnd(alf, bet, eta, oldeta, j, rnm, eps);

                // restore the orthogonality state when needed
                rnm = purge(ll, q, wptr[0], wptr[1], wptr[4], wptr[3], eta, oldeta, j, rnm, tol, eps, reps);
                if (rnm <= tol) {
                    rnm = 0.0;
                }
            }

            if (enough) {
                j = j - 1;
            } else {
                j = last - 1;
            }

            first = j + 1;
            bet[j + 1] = rnm;

            // analyze T
            System.arraycopy(alf, 0, ritz, 0, j + 1);
            System.arraycopy(bet, 0, wptr[5], 0, j + 1);

            z = Matrix.zeros(j + 1, j + 1);
            for (int i = 0; i <= j; i++) {
                z.set(i, i, 1.0);
            }

            // compute the eigenvalues and eigenvectors of the
            // tridiagonal matrix
            JMatrix.tql2(z, ritz, wptr[5]);

            for (int i = 0; i <= j; i++) {
                bnd[i] = rnm * Math.abs(z.get(j, i));
            }

            // massage error bounds for very close ritz values
            boolean[] ref_enough = {enough};
            neig = error_bound(ref_enough, ritz, bnd, j, tol, eps34);
            enough = ref_enough[0];

            // should we stop?
            if (neig < k) {
                if (0 == neig) {
                    last = first + 9;
                    intro = first;
                } else {
                    last = first + Math.max(3, 1 + ((j - intro) * (k - neig)) / Math.max(3,neig));
                }
                last = Math.min(last, n);
            } else {
                enough = true;
            }
            enough = enough || first >= n;
        }

        logger.info("Lanczos: " + iter + " iterations for Matrix of size " + n);

        store(q, j, wptr[1]);

        k = Math.min(k, neig);

        double[] eigenvalues = new double[k];
        DenseMatrix eigenvectors = Matrix.zeros(n, k);
        for (int i = 0, index = 0; i <= j && index < k; i++) {
            if (bnd[i] <= kappa * Math.abs(ritz[i])) {
                for (int row = 0; row < n; row++) {
                    for (int l = 0; l <= j; l++) {
                        eigenvectors.add(row, index, q[l][row] * z.get(l, i));
                    }
                }
                eigenvalues[index++] = ritz[i];
            }
        }

        return new EVD(eigenvectors, eigenvalues);
    }

    /**
     * Generate a starting vector in r and returns |r|. It returns zero if the
     * range is spanned, and throws exception if no starting vector within range
     * of operator can be found.
     * @param step   starting index for a Lanczos run
     */
    private static double startv(Matrix A, double[][] q, double[][] wptr, int step) {
        // get initial vector; default is random
        double rnm = Math.dot(wptr[0], wptr[0]);
        double[] r = wptr[0];
        for (int id = 0; id < 3; id++) {
            if (id > 0 || step > 0 || rnm == 0) {
                for (int i = 0; i < r.length; i++) {
                    r[i] = Math.random() - 0.5;
                }
            }
            Math.copy(wptr[0], wptr[3]);

            // apply operator to put r in range (essential if m singular)
            A.ax(wptr[3], wptr[0]);
            Math.copy(wptr[0], wptr[3]);
            rnm = Math.dot(wptr[0], wptr[3]);
            if (rnm > 0.0) {
                break;
            }
        }

        // fatal error
        if (rnm <= 0.0) {
            logger.error("Lanczos method was unable to find a starting vector within range.");
            return -1;
        }

        if (step > 0) {
            for (int i = 0; i < step; i++) {
                double t = Math.dot(wptr[3], q[i]);
                Math.axpy(-t, q[i], wptr[0]);
            }

            // make sure q[step] is orthogonal to q[step-1]
            double t = Math.dot(wptr[4], wptr[0]);
            Math.axpy(-t, wptr[2], wptr[0]);
            Math.copy(wptr[0], wptr[3]);
            t = Math.dot(wptr[3], wptr[0]);
            if (t <= Math.EPSILON * rnm) {
                t = 0.0;
            }
            rnm = t;
        }

        return Math.sqrt(rnm);
    }

    /**
     * Update the eta recurrence.
     * @param alf      array to store diagonal of the tridiagonal matrix T
     * @param bet      array to store off-diagonal of T
     * @param eta      on input, orthogonality estimate of Lanczos vectors at step j.
     * On output, orthogonality estimate of Lanczos vectors at step j+1 .
     * @param oldeta   on input, orthogonality estimate of Lanczos vectors at step j-1
     * On output orthogonality estimate of Lanczos vectors at step j
     * @param step     dimension of T
     * @param rnm      norm of the next residual vector
     * @param eps      roundoff estimate for dot product of two unit vectors
     */
    private static void ortbnd(double[] alf, double[] bet, double[] eta, double[] oldeta, int step, double rnm, double eps) {
        if (step < 1) {
            return;
        }

        if (0 != rnm) {
            if (step > 1) {
                oldeta[0] = (bet[1] * eta[1] + (alf[0] - alf[step]) * eta[0] - bet[step] * oldeta[0]) / rnm + eps;
            }

            for (int i = 1; i <= step - 2; i++) {
                oldeta[i] = (bet[i + 1] * eta[i + 1] + (alf[i] - alf[step]) * eta[i] + bet[i] * eta[i - 1] - bet[step] * oldeta[i]) / rnm + eps;
            }
        }

        oldeta[step - 1] = eps;
        for (int i = 0; i < step; i++) {
            double swap = eta[i];
            eta[i] = oldeta[i];
            oldeta[i] = swap;
        }

        eta[step] = eps;
    }

    /**
     * Examine the state of orthogonality between the new Lanczos
     * vector and the previous ones to decide whether re-orthogonalization
     * should be performed.
     * @param ll       number of intitial Lanczos vectors in local orthog.
     * @param r        on input, residual vector to become next Lanczos vector.
     * On output, residual vector orthogonalized against previous Lanczos.
     * @param q        on input, current Lanczos vector. On Output, current
     * Lanczos vector orthogonalized against previous ones.
     * @param ra       previous Lanczos vector
     * @param qa       previous Lanczos vector
     * @param eta      state of orthogonality between r and prev. Lanczos vectors
     * @param oldeta   state of orthogonality between q and prev. Lanczos vectors
     */
    private static double purge(int ll, double[][] Q, double[] r, double[] q, double[] ra, double[] qa, double[] eta, double[] oldeta, int step, double rnm, double tol, double eps, double reps) {
        if (step < ll + 2) {
            return rnm;
        }

        double t, tq, tr;
        int k = idamax(step - (ll + 1), eta, ll, 1) + ll;
        if (Math.abs(eta[k]) > reps) {
            double reps1 = eps / reps;
            int iteration = 0;
            boolean flag = true;
            while (iteration < 2 && flag) {
                if (rnm > tol) {
                    // bring in a lanczos vector t and orthogonalize both
                    // r and q against it
                    tq = 0.0;
                    tr = 0.0;
                    for (int i = ll; i < step; i++) {
                        t = -Math.dot(qa, Q[i]);
                        tq += Math.abs(t);
                        Math.axpy(t, Q[i], q);
                        t = -Math.dot(ra, Q[i]);
                        tr += Math.abs(t);
                        Math.axpy(t, Q[i], r);
                    }
                    Math.copy(q, qa);
                    t = -Math.dot(r, qa);
                    tr += Math.abs(t);
                    Math.axpy(t, q, r);
                    Math.copy(r, ra);
                    rnm = Math.sqrt(Math.dot(ra, r));
                    if (tq <= reps1 && tr <= reps1 * rnm) {
                        flag = false;
                    }
                }
                iteration++;
            }

            for (int i = ll; i <= step; i++) {
                eta[i] = eps;
                oldeta[i] = eps;
            }
        }

        return rnm;
    }

    /**
     * Find the index of element having maximum absolute value.
     */
    private static int idamax(int n, double[] dx, int ix0, int incx) {
        int ix, imax;
        double dmax;
        if (n < 1) {
            return -1;
        }
        if (n == 1) {
            return 0;
        }
        if (incx == 0) {
            return -1;
        }
        ix = (incx < 0) ? ix0 + ((-n + 1) * incx) : ix0;
        imax = ix;
        dmax = Math.abs(dx[ix]);
        for (int i = 1; i < n; i++) {
            ix += incx;
            double dtemp = Math.abs(dx[ix]);
            if (dtemp > dmax) {
                dmax = dtemp;
                imax = ix;
            }
        }
        return imax;
    }

    /**
     * Massage error bounds for very close ritz values by placing a gap between
     * them.  The error bounds are then refined to reflect this.
     * @param ritz     array to store the ritz values
     * @param bnd      array to store the error bounds
     * @param enough   stop flag
     */
    private static int error_bound(boolean[] enough, double[] ritz, double[] bnd, int step, double tol, double eps34) {
        double gapl, gap;

        // massage error bounds for very close ritz values
        int mid = idamax(step + 1, bnd, 0, 1);

        for (int i = ((step + 1) + (step - 1)) / 2; i >= mid + 1; i -= 1) {
            if (Math.abs(ritz[i - 1] - ritz[i]) < eps34 * Math.abs(ritz[i])) {
                if (bnd[i] > tol && bnd[i - 1] > tol) {
                    bnd[i - 1] = Math.sqrt(bnd[i] * bnd[i] + bnd[i - 1] * bnd[i - 1]);
                    bnd[i] = 0.0;
                }
            }
        }

        for (int i = ((step + 1) - (step - 1)) / 2; i <= mid - 1; i += 1) {
            if (Math.abs(ritz[i + 1] - ritz[i]) < eps34 * Math.abs(ritz[i])) {
                if (bnd[i] > tol && bnd[i + 1] > tol) {
                    bnd[i + 1] = Math.sqrt(bnd[i] * bnd[i] + bnd[i + 1] * bnd[i + 1]);
                    bnd[i] = 0.0;
                }
            }
        }

        // refine the error bounds
        int neig = 0;
        gapl = ritz[step] - ritz[0];
        for (int i = 0; i <= step; i++) {
            gap = gapl;
            if (i < step) {
                gapl = ritz[i + 1] - ritz[i];
            }

            gap = Math.min(gap, gapl);
            if (gap > bnd[i]) {
                bnd[i] = bnd[i] * (bnd[i] / gap);
            }

            if (bnd[i] <= 16.0 * Math.EPSILON * Math.abs(ritz[i])) {
                neig++;
                if (!enough[0]) {
                    enough[0] = -Math.EPSILON < ritz[i] && ritz[i] < Math.EPSILON;
                }
            }
        }

        logger.info("Lancozs method found {} converged eigenvalues of the {}-by-{} matrix", neig, step + 1, step + 1);
        if (neig != 0) {
            for (int i = 0; i <= step; i++) {
                if (bnd[i] <= 16.0 * Math.EPSILON * Math.abs(ritz[i])) {
                    logger.info("ritz[{}] = {}", i, ritz[i]);
                }
            }
        }

        return neig;
    }

    /**
     * Based on the input operation flag, stores to or retrieves from memory a vector.
     * @param s	   contains the vector to be stored
     */
    private static void store(double[][] q, int j, double[] s) {
        if (null == q[j]) {
            q[j] = s.clone();
        } else {
            Math.copy(s, q[j]);
        }
    }
}
