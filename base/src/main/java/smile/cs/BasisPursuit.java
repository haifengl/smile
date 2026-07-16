/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.cs;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import smile.math.MathEx;
import smile.tensor.Matrix;
import smile.tensor.Vector;

/**
 * Basis Pursuit Denoising (BPDN) via a log-barrier interior-point method.
 *
 * <p>Given an underdetermined linear system {@code y = A*x + noise}, where
 * {@code A} is an {@code m × n} measurement matrix ({@code m ≪ n}), this
 * class recovers the sparsest signal {@code x} by solving the convex
 * L1-norm minimization known as <em>Basis Pursuit Denoising</em>:
 *
 * <pre>
 *   minimise  ‖x‖₁
 *   subject to  ‖Ax − y‖₂ ≤ ε
 * </pre>
 *
 * <p>When {@code ε = 0} the problem reduces to exact <em>Basis Pursuit</em>:
 * {@code minimise ‖x‖₁  s.t.  Ax = y}.
 *
 * <p>The algorithm is a primal–dual log-barrier (interior-point) method that
 * solves a sequence of unconstrained Newton sub-problems, each of which
 * requires a linear solve. The inner linear system is solved with a
 * preconditioned conjugate-gradient (PCG) method, making the overall
 * approach suitable for large, sparse or implicit measurement matrices.
 *
 * <h2>References</h2>
 * <ol>
 * <li>E. J. Candès and T. Tao, "Near-optimal signal recovery from random
 *     projections: Universal encoding strategies?", IEEE Trans. Inf. Theory,
 *     52(12):5406–5425, 2006.</li>
 * <li>E. J. Candès, J. Romberg and T. Tao, "Robust uncertainty principles:
 *     Exact signal reconstruction from highly incomplete frequency
 *     information", IEEE Trans. Inf. Theory, 52(2):489–509, 2006.</li>
 * <li>S. S. Chen, D. L. Donoho and M. A. Saunders, "Atomic decomposition
 *     by basis pursuit", SIAM Rev., 43(1):129–159, 2001.</li>
 * </ol>
 *
 * @param x    the recovered sparse signal vector (length {@code n}).
 * @param iter the total number of Newton (outer) iterations performed.
 * @author Haifeng Li
 */
public record BasisPursuit(double[] x, int iter) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BasisPursuit.class);

    // =========================================================================
    //  Hyperparameters
    // =========================================================================

    /**
     * Hyperparameters for the log-barrier interior-point solver.
     *
     * @param epsilon      the L2 constraint tolerance ({@code ‖Ax−y‖₂ ≤ ε}).
     *                     Use {@code 0} for exact basis pursuit.
     * @param mu           barrier update factor (μ &gt; 1, e.g. 10).
     *                     The barrier parameter {@code t} is multiplied by
     *                     {@code mu} at each outer iteration.
     * @param cgtol        convergence tolerance for the inner CG solver.
     * @param cgMaxIter    maximum CG iterations per Newton step.
     * @param maxIter      maximum number of outer (Newton) iterations.
     * @param tol          duality-gap tolerance; outer loop terminates when
     *                     the surrogate duality gap satisfies
     *                     {@code gap/|f| < tol}.
     */
    public record Options(double epsilon, double mu, double cgtol,
                          int cgMaxIter, int maxIter, double tol) {
        /** Constructs options with defaults. */
        public Options {
            if (epsilon < 0) throw new IllegalArgumentException("epsilon must be ≥ 0: " + epsilon);
            if (mu <= 1)     throw new IllegalArgumentException("mu must be > 1: " + mu);
            if (cgtol <= 0)  throw new IllegalArgumentException("cgtol must be > 0: " + cgtol);
            if (cgMaxIter <= 0) throw new IllegalArgumentException("cgMaxIter must be > 0: " + cgMaxIter);
            if (maxIter <= 0)   throw new IllegalArgumentException("maxIter must be > 0: " + maxIter);
            if (tol <= 0)       throw new IllegalArgumentException("tol must be > 0: " + tol);
        }

        /** Default options. */
        public Options() {
            this(0.0, 10.0, 1e-8, 200, 50, 1e-3);
        }

        /**
         * Returns properties for persistence.
         * @return the properties.
         */
        public Properties toProperties() {
            var p = new Properties();
            p.setProperty("smile.cs.bp.epsilon",    Double.toString(epsilon));
            p.setProperty("smile.cs.bp.mu",         Double.toString(mu));
            p.setProperty("smile.cs.bp.cgtol",      Double.toString(cgtol));
            p.setProperty("smile.cs.bp.cgMaxIter",  Integer.toString(cgMaxIter));
            p.setProperty("smile.cs.bp.maxIter",    Integer.toString(maxIter));
            p.setProperty("smile.cs.bp.tol",        Double.toString(tol));
            return p;
        }

        /**
         * Creates options from properties.
         * @param props the properties.
         * @return the options.
         */
        public static Options of(Properties props) {
            double epsilon   = Double.parseDouble(props.getProperty("smile.cs.bp.epsilon",   "0.0"));
            double mu        = Double.parseDouble(props.getProperty("smile.cs.bp.mu",        "10.0"));
            double cgtol     = Double.parseDouble(props.getProperty("smile.cs.bp.cgtol",     "1e-8"));
            int    cgMaxIter = Integer.parseInt(  props.getProperty("smile.cs.bp.cgMaxIter", "200"));
            int    maxIter   = Integer.parseInt(  props.getProperty("smile.cs.bp.maxIter",   "50"));
            double tol       = Double.parseDouble(props.getProperty("smile.cs.bp.tol",       "1e-3"));
            return new Options(epsilon, mu, cgtol, cgMaxIter, maxIter, tol);
        }
    }

    // =========================================================================
    //  Public factory methods
    // =========================================================================

    /**
     * Recovers a sparse signal using Basis Pursuit ({@code ε = 0}).
     *
     * @param A the {@code m × n} measurement matrix.
     * @param y the {@code m}-dimensional measurement vector.
     * @return the recovered signal.
     */
    public static BasisPursuit fit(Matrix A, double[] y) {
        return fit(A, y, new Options());
    }

    /**
     * Recovers a sparse signal using Basis Pursuit Denoising.
     *
     * @param A       the {@code m × n} measurement matrix.
     * @param y       the {@code m}-dimensional measurement vector.
     * @param options solver hyperparameters.
     * @return the recovered signal.
     */
    public static BasisPursuit fit(Matrix A, double[] y, Options options) {
        int m = A.nrow();
        int n = A.ncol();
        if (y.length != m) {
            throw new IllegalArgumentException(
                    "y length %d does not match matrix rows %d".formatted(y.length, m));
        }

        double epsilon = options.epsilon;
        if (epsilon > 0) {
            return solveQC(A, y, m, n, options);
        } else {
            return solveEQ(A, y, m, n, options);
        }
    }

    // =========================================================================
    //  Exact Basis Pursuit  ( min ‖x‖₁  s.t.  Ax = y )
    //  Lifted to  min ‖u‖₁  s.t.  Ax = y,  −u ≤ x ≤ u
    //  Primal variables: (x, u)  ∈ ℝ²ⁿ
    // =========================================================================
    private static BasisPursuit solveEQ(Matrix A, double[] y, int m, int n, Options opts) {
        double mu       = opts.mu;
        double cgtol    = opts.cgtol;
        int    cgMax    = opts.cgMaxIter;
        int    maxIter  = opts.maxIter;
        double tol      = opts.tol;

        // --- initialize primal variables ---
        // x0 = A^T (A A^T)^{-1} y  (minimum-norm least-squares via CG)
        double[] x = leastNorm(A, y, m, n, cgtol, cgMax);
        double[] u = new double[n];
        for (int i = 0; i < n; i++) u[i] = Math.abs(x[i]) + 0.1;

        double t = Math.max(1.0, 1.0 / (2.0 * n));  // barrier parameter

        double[] dx = new double[n];
        double[] du = new double[n];
        double[] Adx = new double[m];

        // Newton work arrays
        double[] grad_x = new double[n];
        double[] grad_u = new double[n];
        double[] rhs    = new double[2 * n];
        double[] sol    = new double[2 * n];

        int totalIter = 0;

        for (int outerIt = 0; outerIt < maxIter; outerIt++) {
            // surrogate duality gap
            double gap = 2.0 * n / t;
            double f   = MathEx.norm1(u);
            logger.debug("BP outer iter {}: ‖u‖₁ = {}, gap = {}", outerIt, f, gap);

            if (gap / Math.max(1.0, Math.abs(f)) < tol) {
                logger.info("BP converged at outer iter {} (gap/|f| = {})", outerIt, gap / Math.abs(f));
                break;
            }

            // --- Newton step ---
            // Barrier terms: log(u + x) + log(u - x)
            // Gradient:
            //   ∂/∂x:  t·1 − 1/(u+x) + 1/(u−x)   → wait, we minimise t·‖u‖₁ − Σlog(ui+xi) − Σlog(ui−xi) + barrier(Ax=y)
            // Using augmented Lagrangian approach / Newton interior-point:
            double[] upx = new double[n];  // u + x
            double[] umx = new double[n];  // u - x
            for (int i = 0; i < n; i++) {
                upx[i] = u[i] + x[i];
                umx[i] = u[i] - x[i];
            }

            // Check interior feasibility
            boolean feasible = true;
            for (int i = 0; i < n; i++) {
                if (upx[i] <= 0 || umx[i] <= 0) { feasible = false; break; }
            }
            if (!feasible) {
                logger.warn("BP: interior feasibility lost at iter {}; stopping.", outerIt);
                break;
            }

            // q1 = 1/(u+x),  q2 = 1/(u-x)
            double[] q1 = new double[n];
            double[] q2 = new double[n];
            for (int i = 0; i < n; i++) {
                q1[i] = 1.0 / upx[i];
                q2[i] = 1.0 / umx[i];
            }

            // Gradient of barrier w.r.t. x and u
            for (int i = 0; i < n; i++) {
                grad_x[i] = q2[i] - q1[i];      // ∂/∂xi = −q1 + q2
                grad_u[i] = t - q1[i] - q2[i];  // ∂/∂ui = t − q1 − q2
            }

            // Diagonal blocks of Hessian
            // d1 = (q1^2 + q2^2),  d2 = (q1^2 - q2^2)  — but for the Schur step we use:
            // D1 = diag(q1² + q2²),  D2 = diag(q1² − q2²),  W = D1 − D2²/D1
            double[] d1 = new double[n];
            double[] d2 = new double[n];
            double[] w  = new double[n];
            for (int i = 0; i < n; i++) {
                d1[i] = q1[i] * q1[i] + q2[i] * q2[i];
                d2[i] = q1[i] * q1[i] - q2[i] * q2[i];
                w[i]  = d1[i] - (d2[i] * d2[i]) / d1[i];  // Schur complement diagonal
            }

            // Build RHS for Schur complement system (KKT) for dx:
            //   (A diag(W)^{-1} A^T) ν = b   then dx = …
            // Reduced gradient rhs_x = grad_x - d2/d1 * grad_u
            double[] rhs_x = new double[n];
            for (int i = 0; i < n; i++) {
                rhs_x[i] = -(grad_x[i] - (d2[i] / d1[i]) * grad_u[i]);
            }

            // CG solve  (A * diag(1/w) * A^T) ν = A * rhs_x / w
            // Build r_vec = A * (rhs_x / w), use CG on H = A diag(1/w) A^T
            double[] rhs_x_over_w = new double[n];
            for (int i = 0; i < n; i++) rhs_x_over_w[i] = rhs_x[i] / w[i];

            double[] bvec = matvec(A, rhs_x_over_w, m, n, false);
            double[] nu = cgSolve(A, w, bvec, m, n, cgtol, cgMax);

            // Recover dx, du
            double[] Atnu = matvec(A, nu, m, n, true);
            for (int i = 0; i < n; i++) {
                dx[i] = (rhs_x[i] - Atnu[i]) / w[i];
                du[i] = -(grad_u[i] + d2[i] * dx[i]) / d1[i];
            }

            // Line search
            // Maximum step respecting u+x>0, u-x>0
            double smax = 1.0;
            for (int i = 0; i < n; i++) {
                double s = (dx[i] - du[i]) < 0 ? -upx[i] / (dx[i] - du[i]) : Double.POSITIVE_INFINITY;
                smax = Math.min(smax, s);
                s    = (-dx[i] - du[i]) < 0 ? -umx[i] / (-dx[i] - du[i]) : Double.POSITIVE_INFINITY;
                smax = Math.min(smax, s);
            }
            double step = Math.min(1.0, 0.99 * smax);

            // Armijo back-tracking
            for (int ls = 0; ls < 50; ls++) {
                double[] xnew = new double[n];
                double[] unew = new double[n];
                for (int i = 0; i < n; i++) {
                    xnew[i] = x[i] + step * dx[i];
                    unew[i] = u[i] + step * du[i];
                }
                // Check feasibility
                boolean ok = true;
                for (int i = 0; i < n; i++) {
                    if (unew[i] + xnew[i] <= 0 || unew[i] - xnew[i] <= 0) { ok = false; break; }
                }
                double fnew = MathEx.norm1(unew);
                if (ok && fnew < f) {
                    System.arraycopy(xnew, 0, x, 0, n);
                    System.arraycopy(unew, 0, u, 0, n);
                    break;
                }
                step *= 0.5;
            }

            // Check residual  Ax − y
            double[] res = matvec(A, x, m, n, false);
            for (int i = 0; i < m; i++) res[i] -= y[i];
            logger.debug("BP residual ‖Ax−y‖₂ = {}", MathEx.norm(res));

            // Update barrier
            t *= mu;
            totalIter++;
        }

        return new BasisPursuit(x, totalIter);
    }

    // =========================================================================
    //  Quadratically-Constrained Basis Pursuit
    //  min ‖x‖₁  s.t.  ‖Ax − y‖₂ ≤ ε
    //  Reformulated as:
    //  min ‖u‖₁  s.t.  ‖Ax − y‖₂ ≤ ε,  −u ≤ x ≤ u
    //  Interior-point (log-barrier) method, Newton's method for each t.
    // =========================================================================
    private static BasisPursuit solveQC(Matrix A, double[] y, int m, int n, Options opts) {
        double epsilon  = opts.epsilon;
        double mu       = opts.mu;
        double cgtol    = opts.cgtol;
        int    cgMax    = opts.cgMaxIter;
        int    maxIter  = opts.maxIter;
        double tol      = opts.tol;

        // Initialize x via minimum-norm LS, scale so residual ≤ epsilon
        double[] x = leastNorm(A, y, m, n, cgtol, cgMax);
        double[] res = matvec(A, x, m, n, false);
        for (int i = 0; i < m; i++) res[i] -= y[i];
        double resNorm = MathEx.norm(res);
        if (resNorm > epsilon) {
            double scale = epsilon / (2.0 * resNorm);
            for (int i = 0; i < n; i++) x[i] *= scale;
        }

        double[] u = new double[n];
        for (int i = 0; i < n; i++) u[i] = Math.abs(x[i]) + 0.1;

        double tau = Math.max(2.0 * n + 1, 1.0 / (2.0 * n));

        double[] dx  = new double[n];
        double[] du  = new double[n];

        int totalIter = 0;

        for (int outerIt = 0; outerIt < maxIter; outerIt++) {
            // Recompute residual r = Ax − y
            double[] Ax = matvec(A, x, m, n, false);
            double[] r  = new double[m];
            for (int i = 0; i < m; i++) r[i] = Ax[i] - y[i];
            double rNorm2 = MathEx.dot(r, r);
            double epsSq  = epsilon * epsilon;
            double slack  = epsSq - rNorm2;

            if (slack <= 0) {
                logger.warn("BPDN: QC constraint violated at iter {}; resNorm={}", outerIt, Math.sqrt(rNorm2));
            }

            double[] upx = new double[n];
            double[] umx = new double[n];
            for (int i = 0; i < n; i++) {
                upx[i] = u[i] + x[i];
                umx[i] = u[i] - x[i];
            }
            boolean feasible = slack > 0;
            for (int i = 0; i < n; i++) {
                if (upx[i] <= 0 || umx[i] <= 0) { feasible = false; break; }
            }
            if (!feasible) {
                logger.warn("BPDN: interior feasibility lost at iter {}; stopping.", outerIt);
                break;
            }

            double[] q1 = new double[n];
            double[] q2 = new double[n];
            for (int i = 0; i < n; i++) {
                q1[i] = 1.0 / upx[i];
                q2[i] = 1.0 / umx[i];
            }

            // Objective gradient
            double[] grad_x = new double[n];
            double[] grad_u = new double[n];
            // grad_x:  tau·A^T r / slack − (q1 − q2)
            double[] Atr = matvec(A, r, m, n, true);
            double alpha = tau / slack;
            for (int i = 0; i < n; i++) {
                grad_x[i] = alpha * Atr[i] - q1[i] + q2[i];
                grad_u[i] = tau - q1[i] - q2[i];
            }

            // Hessian diagonal blocks
            double[] d1 = new double[n];
            double[] d2 = new double[n];
            double[] w  = new double[n];
            double sig2 = tau / (slack * slack);
            for (int i = 0; i < n; i++) {
                d1[i] = q1[i] * q1[i] + q2[i] * q2[i] + 2.0 * sig2 * Atr[i] * Atr[i];
                d2[i] = q1[i] * q1[i] - q2[i] * q2[i];
                w[i]  = d1[i] - (d2[i] * d2[i]) / d1[i];
            }

            // Solve for Newton step via Schur complement + CG
            double[] rhs_x = new double[n];
            for (int i = 0; i < n; i++) {
                rhs_x[i] = -(grad_x[i] - (d2[i] / d1[i]) * grad_u[i]);
            }

            // CG on  (A diag(1/w) A^T + (2*sig2) I) dν = A (rhs_x/w)
            double[] rhs_over_w = new double[n];
            for (int i = 0; i < n; i++) rhs_over_w[i] = rhs_x[i] / w[i];
            double[] bvec = matvec(A, rhs_over_w, m, n, false);
            // Additional quadratic term from QC Hessian: + 2*sig2 * A A^T   → added via operator
            double[] nu = cgSolveQC(A, w, sig2, r, bvec, m, n, cgtol, cgMax);

            double[] Atnu = matvec(A, nu, m, n, true);
            for (int i = 0; i < n; i++) {
                dx[i] = (rhs_x[i] - Atnu[i]) / w[i];
                du[i] = -(grad_u[i] + d2[i] * dx[i]) / d1[i];
            }

            // Step-length: maximum respecting box constraints
            double smax = 1.0;
            for (int i = 0; i < n; i++) {
                if (dx[i] - du[i] < 0) smax = Math.min(smax, -upx[i] / (dx[i] - du[i]));
                if (-dx[i] - du[i] < 0) smax = Math.min(smax, -umx[i] / (-dx[i] - du[i]));
            }
            double step = Math.min(1.0, 0.99 * smax);

            // Also enforce QC constraint via golden-section / bisection
            // (simplified: just scale down until residual ≤ eps)
            for (int ls = 0; ls < 50; ls++) {
                double[] xnew = new double[n];
                double[] unew = new double[n];
                for (int i = 0; i < n; i++) {
                    xnew[i] = x[i] + step * dx[i];
                    unew[i] = u[i] + step * du[i];
                }
                double[] rnew = matvec(A, xnew, m, n, false);
                for (int i = 0; i < m; i++) rnew[i] -= y[i];
                boolean box = true;
                for (int i = 0; i < n; i++) {
                    if (unew[i] + xnew[i] <= 0 || unew[i] - xnew[i] <= 0) { box = false; break; }
                }
                if (box && MathEx.dot(rnew, rnew) < epsSq) {
                    System.arraycopy(xnew, 0, x, 0, n);
                    System.arraycopy(unew, 0, u, 0, n);
                    break;
                }
                step *= 0.5;
            }

            double gap = 2.0 * n / tau;
            double f   = MathEx.norm1(u);
            logger.debug("BPDN outer iter {}: ‖u‖₁ = {}, gap = {}", outerIt, f, gap);

            if (gap / Math.max(1.0, Math.abs(f)) < tol) {
                logger.info("BPDN converged at outer iter {}", outerIt);
                break;
            }

            tau *= mu;
            totalIter++;
        }

        return new BasisPursuit(x, totalIter);
    }

    // =========================================================================
    //  Utilities
    // =========================================================================

    /**
     * Computes the minimum-norm solution x = A^T (A A^T)^{-1} y via CG.
     */
    private static double[] leastNorm(Matrix A, double[] y, int m, int n,
                                      double cgtol, int cgMax) {
        // CG on (A A^T) ν = y, then x = A^T ν
        double[] nu = cgSolveAAT(A, y, m, n, cgtol, cgMax);
        return matvec(A, nu, m, n, true);
    }

    /**
     * Matrix–vector product: {@code y = A x} (trans=false) or
     * {@code y = A^T x} (trans=true).
     */
    static double[] matvec(Matrix A, double[] x, int m, int n, boolean trans) {
        if (trans) {
            // y = A^T x, y ∈ ℝⁿ
            double[] y = new double[n];
            var xv = Vector.column(x);
            var yv = Vector.column(y);
            A.tv(xv, yv);
            return yv.toArray(y);
        } else {
            // y = A x, y ∈ ℝᵐ
            double[] y = new double[m];
            var xv = Vector.column(x);
            var yv = Vector.column(y);
            A.mv(xv, yv);
            return yv.toArray(y);
        }
    }

    /**
     * Preconditioned CG for (A A^T) ν = b.
     * Used for computing the minimum-norm solution.
     */
    private static double[] cgSolveAAT(Matrix A, double[] b, int m, int n,
                                       double tol, int maxIter) {
        double[] x = new double[m];
        double[] r = Arrays.copyOf(b, m);
        double[] p = Arrays.copyOf(r, m);
        double rsold = MathEx.dot(r, r);
        double bnorm2 = rsold;
        if (bnorm2 < 1e-30) return x;

        for (int k = 0; k < maxIter; k++) {
            // q = A A^T p
            double[] Atp = matvec(A, p, m, n, true);
            double[] q   = matvec(A, Atp, m, n, false);
            double alpha = rsold / MathEx.dot(p, q);
            for (int i = 0; i < m; i++) {
                x[i] += alpha * p[i];
                r[i] -= alpha * q[i];
            }
            double rsnew = MathEx.dot(r, r);
            if (rsnew / bnorm2 < tol * tol) break;
            double beta = rsnew / rsold;
            for (int i = 0; i < m; i++) p[i] = r[i] + beta * p[i];
            rsold = rsnew;
        }
        return x;
    }

    /**
     * CG for the Schur-complement system  (A diag(1/w) A^T) ν = b.
     */
    static double[] cgSolve(Matrix A, double[] w, double[] b, int m, int n,
                            double tol, int maxIter) {
        // Operator: v → A diag(1/w) A^T v
        double[] x = new double[m];
        double[] r = Arrays.copyOf(b, m);
        double[] p = Arrays.copyOf(r, m);
        double rsold  = MathEx.dot(r, r);
        double bnorm2 = rsold;
        if (bnorm2 < 1e-30) return x;

        for (int k = 0; k < maxIter; k++) {
            // q = A (A^T p / w)
            double[] Atp = matvec(A, p, m, n, true);
            double[] tmp = new double[n];
            for (int i = 0; i < n; i++) tmp[i] = Atp[i] / w[i];
            double[] q = matvec(A, tmp, m, n, false);

            double pq = MathEx.dot(p, q);
            if (Math.abs(pq) < 1e-30) break;
            double alpha = rsold / pq;
            for (int i = 0; i < m; i++) {
                x[i] += alpha * p[i];
                r[i] -= alpha * q[i];
            }
            double rsnew = MathEx.dot(r, r);
            if (rsnew / bnorm2 < tol * tol) break;
            double beta = rsnew / rsold;
            for (int i = 0; i < m; i++) p[i] = r[i] + beta * p[i];
            rsold = rsnew;
        }
        return x;
    }

    /**
     * CG for the QC Schur-complement system
     * (A diag(1/w) A^T + (2*sig2/slack) * A A^T) ν = b,
     * approximated as (A diag(1/w + 2*sig2) A^T) ν = b.
     */
    private static double[] cgSolveQC(Matrix A, double[] w, double sig2, double[] r,
                                       double[] b, int m, int n, double tol, int maxIter) {
        // Effective diagonal: weff[i] = w[i] / (1 + 2*sig2 * w[i])
        double[] weff = new double[n];
        for (int i = 0; i < n; i++) weff[i] = w[i] / (1.0 + 2.0 * sig2 * w[i]);
        return cgSolve(A, weff, b, m, n, tol, maxIter);
    }
}

