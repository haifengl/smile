/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math.matrix;

import smile.math.MathEx;

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
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Lanczos.class);

    /**
     * Find k-largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     * @return eigen value decomposition.
     */
    public static Matrix.EVD eigen(IMatrix A, int k) {
        return eigen(A, k, 1.0E-8, 10 * A.nrow());
    }

    /**
     * Find k-largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     * @param kappa relative accuracy of ritz values acceptable as eigenvalues.
     * @param maxIter Maximum number of iterations.
     * @return eigen value decomposition.
     */
    public static Matrix.EVD eigen(IMatrix A, int k, double kappa, int maxIter) {
        if (A.nrow() != A.ncol()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.nrow(), A.ncol()));
        }

        if (k < 1 || k > A.nrow()) {
            throw new IllegalArgumentException("k is larger than the size of A: " + k + " > " + A.nrow());
        }

        if (kappa <= MathEx.EPSILON) {
            throw new IllegalArgumentException("Invalid tolerance: kappa = " + kappa);
        }

        if (maxIter <= 0) {
            maxIter = 10 * A.nrow();
        }

        int n = A.nrow();
        int intro = 0;

        // roundoff estimate for dot product of two unit vectors
        double eps = MathEx.EPSILON * Math.sqrt(n);
        double reps = Math.sqrt(MathEx.EPSILON);
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
        Matrix z = null;

        // First step of the Lanczos algorithm. It also does a step of extended
        // local re-orthogonalization.
        // get initial vector; default is random
        double rnm = startv(A, q, wptr, 0);

        // normalize starting vector
        double t = 1.0 / rnm;
        MathEx.scale(t, wptr[0], wptr[1]);
        MathEx.scale(t, wptr[3]);

        // take the first step
        A.mv(wptr[3], wptr[0]);
        alf[0] = MathEx.dot(wptr[0], wptr[3]);
        MathEx.axpy(-alf[0], wptr[1], wptr[0]);
        t = MathEx.dot(wptr[0], wptr[3]);
        MathEx.axpy(-t, wptr[1], wptr[0]);
        alf[0] += t;
        System.arraycopy(wptr[0], 0, wptr[4], 0, n);
        rnm = MathEx.norm(wptr[0]);
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
                MathEx.swap(wptr, 1, 2);
                MathEx.swap(wptr, 3, 4);

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
                    MathEx.swap(wptr, 1, 2);
                    break;
                }

                // take a lanczos step
                t = 1.0 / rnm;
                MathEx.scale(t, wptr[0], wptr[1]);
                MathEx.scale(t, wptr[3]);
                A.mv(wptr[3], wptr[0]);
                MathEx.axpy(-rnm, wptr[2], wptr[0]);
                alf[j] = MathEx.dot(wptr[0], wptr[3]);
                MathEx.axpy(-alf[j], wptr[1], wptr[0]);

                // orthogonalize against initial lanczos vectors
                if (j <= 2 && (Math.abs(alf[j - 1]) > 4.0 * Math.abs(alf[j]))) {
                    ll = j;
                }

                for (int i = 0; i < Math.min(ll, j - 1); i++) {
                    t = MathEx.dot(p[i], wptr[0]);
                    MathEx.axpy(-t, q[i], wptr[0]);
                    eta[i] = eps;
                    oldeta[i] = eps;
                }

                // extended local reorthogonalization
                t = MathEx.dot(wptr[0], wptr[4]);
                MathEx.axpy(-t, wptr[2], wptr[0]);
                if (bet[j] > 0.0) {
                    bet[j] = bet[j] + t;
                }
                t = MathEx.dot(wptr[0], wptr[3]);
                MathEx.axpy(-t, wptr[1], wptr[0]);
                alf[j] = alf[j] + t;
                System.arraycopy(wptr[0], 0, wptr[4], 0, n);
                rnm = MathEx.norm(wptr[0]);
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

            z = new Matrix(j + 1, j + 1);
            for (int i = 0; i <= j; i++) {
                z.set(i, i, 1.0);
            }

            // compute the eigenvalues and eigenvectors of the
            // tridiagonal matrix
            tql2(z, ritz, wptr[5]);

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

        logger.info("Lanczos: {} iterations for Matrix of size {}", iter, n);

        store(q, j, wptr[1]);

        k = Math.min(k, neig);

        double[] eigenvalues = new double[k];
        Matrix eigenvectors = new Matrix(n, k);
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

        return new Matrix.EVD(eigenvalues, eigenvectors);
    }

    /**
     * Generate a starting vector in r and returns |r|. It returns zero if the
     * range is spanned, and throws exception if no starting vector within range
     * of operator can be found.
     * @param step starting index for a Lanczos run
     */
    private static double startv(IMatrix A, double[][] q, double[][] wptr, int step) {
        // get initial vector; default is random
        double rnm = MathEx.dot(wptr[0], wptr[0]);
        double[] r = wptr[0];
        int n = r.length;
        for (int id = 0; id < 3; id++) {
            if (id > 0 || step > 0 || rnm == 0) {
                for (int i = 0; i < r.length; i++) {
                    r[i] = Math.random() - 0.5;
                }
            }
            System.arraycopy(wptr[0], 0, wptr[3], 0, n);

            // apply operator to put r in range (essential if m singular)
            A.mv(wptr[3], wptr[0]);
            System.arraycopy(wptr[0], 0, wptr[3], 0, n);
            rnm = MathEx.dot(wptr[0], wptr[3]);
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
                double t = MathEx.dot(wptr[3], q[i]);
                MathEx.axpy(-t, q[i], wptr[0]);
            }

            // make sure q[step] is orthogonal to q[step-1]
            double t = MathEx.dot(wptr[4], wptr[0]);
            MathEx.axpy(-t, wptr[2], wptr[0]);
            System.arraycopy(wptr[0], 0, wptr[3], 0, n);
            t = MathEx.dot(wptr[3], wptr[0]);
            if (t <= MathEx.EPSILON * rnm) {
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
                        t = -MathEx.dot(qa, Q[i]);
                        tq += Math.abs(t);
                        MathEx.axpy(t, Q[i], q);
                        t = -MathEx.dot(ra, Q[i]);
                        tr += Math.abs(t);
                        MathEx.axpy(t, Q[i], r);
                    }
                    System.arraycopy(q, 0, qa, 0, q.length);
                    t = -MathEx.dot(r, qa);
                    tr += Math.abs(t);
                    MathEx.axpy(t, q, r);
                    System.arraycopy(r, 0, ra, 0, r.length);
                    rnm = Math.sqrt(MathEx.dot(ra, r));
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

            if (bnd[i] <= 16.0 * MathEx.EPSILON * Math.abs(ritz[i])) {
                neig++;
                if (!enough[0]) {
                    enough[0] = -MathEx.EPSILON < ritz[i] && ritz[i] < MathEx.EPSILON;
                }
            }
        }

        logger.info("Lancozs method found {} converged eigenvalues of the {}-by-{} matrix", neig, step + 1, step + 1);
        if (neig != 0) {
            for (int i = 0; i <= step; i++) {
                if (bnd[i] <= 16.0 * MathEx.EPSILON * Math.abs(ritz[i])) {
                    logger.info("ritz[{}] = {}", i, ritz[i]);
                }
            }
        }

        return neig;
    }

    /**
     * Based on the input operation flag, stores to or retrieves from memory a vector.
     * @param s contains the vector to be stored
     */
    private static void store(double[][] q, int j, double[] s) {
        if (null == q[j]) {
            q[j] = s.clone();
        } else {
            System.arraycopy(s, 0, q[j], 0, s.length);
        }
    }

    /**
     * Tridiagonal QL Implicit routine for computing eigenvalues and eigenvectors of a symmetric,
     * real, tridiagonal matrix.
     * <p>
     * The routine works extremely well in practice. The number of iterations for the first few
     * eigenvalues might be 4 or 5, say, but meanwhile the off-diagonal elements in the lower right-hand
     * corner have been reduced too. The later eigenvalues are liberated with very little work. The
     * average number of iterations per eigenvalue is typically 1.3 - 1.6. The operation count per
     * iteration is O(n), with a fairly large effective coefficient, say, ~20n. The total operation count
     * for the diagonalization is then ~20n * (1.3 - 1.6)n = ~30n<sup>2</sup>. If the eigenvectors are required,
     * there is an additional, much larger, workload of about O(3n<sup>3</sup>) operations.
     *
     * @param V on input, it contains the identity matrix. On output, the kth column
     * of V returns the normalized eigenvector corresponding to d[k].
     * @param d on input, it contains the diagonal elements of the tridiagonal matrix.
     * On output, it contains the eigenvalues.
     * @param e on input, it contains the subdiagonal elements of the tridiagonal
     * matrix, with e[0] arbitrary. On output, its contents are destroyed.
     */
    private static void tql2(Matrix V, double[] d, double[] e) {
        int n = V.nrow();
        for (int i = 1; i < n; i++) {
            e[i - 1] = e[i];
        }
        e[n - 1] = 0.0;

        double f = 0.0;
        double tst1 = 0.0;
        for (int l = 0; l < n; l++) {
            // Find small subdiagonal element
            tst1 = Math.max(tst1, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            for (; m < n; m++) {
                if (Math.abs(e[m]) <= MathEx.EPSILON * tst1) {
                    break;
                }
            }

            // If m == l, d[l] is an eigenvalue,
            // otherwise, iterate.
            if (m > l) {
                int iter = 0;
                do {
                    if (++iter >= 30) {
                        throw new RuntimeException("Too many iterations");
                    }

                    // Compute implicit shift
                    double g = d[l];
                    double p = (d[l + 1] - g) / (2.0 * e[l]);
                    double r = Math.hypot(p, 1.0);
                    if (p < 0) {
                        r = -r;
                    }
                    d[l] = e[l] / (p + r);
                    d[l + 1] = e[l] * (p + r);
                    double dl1 = d[l + 1];
                    double h = g - d[l];
                    for (int i = l + 2; i < n; i++) {
                        d[i] -= h;
                    }
                    f = f + h;

                    // Implicit QL transformation.
                    p = d[m];
                    double c = 1.0;
                    double c2 = c;
                    double c3 = c;
                    double el1 = e[l + 1];
                    double s = 0.0;
                    double s2 = 0.0;
                    for (int i = m - 1; i >= l; i--) {
                        c3 = c2;
                        c2 = c;
                        s2 = s;
                        g = c * e[i];
                        h = c * p;
                        r = Math.hypot(p, e[i]);
                        e[i + 1] = s * r;
                        s = e[i] / r;
                        c = p / r;
                        p = c * d[i] - s * g;
                        d[i + 1] = h + s * (c * g + s * d[i]);

                        // Accumulate transformation.
                        for (int k = 0; k < n; k++) {
                            h = V.get(k, i + 1);
                            V.set(k, i + 1, s * V.get(k, i) + c * h);
                            V.set(k, i,     c * V.get(k, i) - s * h);
                        }
                    }
                    p = -s * s2 * c3 * el1 * e[l] / dl1;
                    e[l] = s * p;
                    d[l] = c * p;

                    // Check for convergence.
                } while (Math.abs(e[l]) > MathEx.EPSILON * tst1);
            }
            d[l] = d[l] + f;
            e[l] = 0.0;
        }

        // Sort eigenvalues and corresponding vectors.
        for (int i = 0; i < n - 1; i++) {
            int k = i;
            double p = d[i];
            for (int j = i + 1; j < n; j++) {
                if (d[j] > p) {
                    k = j;
                    p = d[j];
                }
            }
            if (k != i) {
                d[k] = d[i];
                d[i] = p;
                for (int j = 0; j < n; j++) {
                    p = V.get(j, i);
                    V.set(j, i, V.get(j, k));
                    V.set(j, k, p);
                }
            }
        }
    }
}
