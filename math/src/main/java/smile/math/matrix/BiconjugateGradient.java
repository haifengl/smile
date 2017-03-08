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
 * The biconjugate gradient method is an algorithm to
 * solve systems of linear equations.
 *
 * @author Haifeng Li
 */
public class BiconjugateGradient {
    private static final Logger logger = LoggerFactory.getLogger(BiconjugateGradient.class);

    /** Returns a simple preconditioner matrix that is the
     * trivial diagonal part of A in some cases.
     */
    private static Preconditioner diagonalPreconditioner(Matrix A) {
        return new Preconditioner() {
            public void asolve(double[] b, double[] x) {
                double[] diag = A.diag();
                int n = diag.length;

                for (int i = 0; i < n; i++) {
                    x[i] = diag[i] != 0.0 ? b[i] / diag[i] : b[i];
                }
            }
        };
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @return the estimated error.
     */
    public static double solve(Matrix A, double[] b, double[] x) {
        return solve(A, diagonalPreconditioner(A), b, x);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @return the estimated error.
     */
    public static double solve(Matrix A, Preconditioner Ap, double[] b, double[] x) {
        return solve(A, Ap, b, x, 1E-10);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(Matrix A, double[] b, double[] x, double tol) {
        return solve(A, diagonalPreconditioner(A), b, x, tol);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(Matrix A, Preconditioner Ap, double[] b, double[] x, double tol) {
        return solve(A, Ap, b, x, tol, 1);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(Matrix A, double[] b, double[] x, double tol, int itol) {
        return solve(A, diagonalPreconditioner(A), b, x, tol, itol);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(Matrix A, Preconditioner Ap, double[] b, double[] x, double tol, int itol) {
        return solve(A, Ap, b, x, tol, itol, 2 * Math.max(A.nrows(), A.ncols()));
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * This method can be called repeatedly, with maxIter &lt; n, to monitor how
     * error decreases.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the estimated error.
     */
    public static double solve(Matrix A, double[] b, double[] x, double tol, int itol, int maxIter) {
        return solve(A, diagonalPreconditioner(A), b, x, tol, itol, maxIter);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * This method can be called repeatedly, with maxIter &lt; n, to monitor how
     * error decreases.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the estimated error.
     */
    public static double solve(Matrix A, Preconditioner Ap, double[] b, double[] x, double tol, int itol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        if (itol < 1 || itol > 4) {
            throw new IllegalArgumentException(String.format("Illegal itol: %d", itol));
        }

        double err = 0.0;
        double ak, akden, bk, bkden = 1.0, bknum, bnrm, dxnrm, xnrm, zm1nrm, znrm = 0.0;
        int j, n = b.length;

        double[] p = new double[n];
        double[] pp = new double[n];
        double[] r = new double[n];
        double[] rr = new double[n];
        double[] z = new double[n];
        double[] zz = new double[n];

        A.ax(x, r);
        for (j = 0; j < n; j++) {
            r[j] = b[j] - r[j];
            rr[j] = r[j];
        }

        if (itol == 1) {
            bnrm = snorm(b, itol);
            Ap.asolve(r, z);
        } else if (itol == 2) {
            Ap.asolve(b, z);
            bnrm = snorm(z, itol);
            Ap.asolve(r, z);
        } else if (itol == 3 || itol == 4) {
            Ap.asolve(b, z);
            bnrm = snorm(z, itol);
            Ap.asolve(r, z);
            znrm = snorm(z, itol);
        } else {
            throw new IllegalArgumentException(String.format("Illegal itol: %d", itol));
        }

        for (int iter = 1; iter <= maxIter; iter++) {
            Ap.asolve(rr, zz);
            for (bknum = 0.0, j = 0; j < n; j++) {
                bknum += z[j] * rr[j];
            }
            if (iter == 1) {
                for (j = 0; j < n; j++) {
                    p[j] = z[j];
                    pp[j] = zz[j];
                }
            } else {
                bk = bknum / bkden;
                for (j = 0; j < n; j++) {
                    p[j] = bk * p[j] + z[j];
                    pp[j] = bk * pp[j] + zz[j];
                }
            }
            bkden = bknum;
            A.ax(p, z);
            for (akden = 0.0, j = 0; j < n; j++) {
                akden += z[j] * pp[j];
            }
            ak = bknum / akden;
            A.atx(pp, zz);
            for (j = 0; j < n; j++) {
                x[j] += ak * p[j];
                r[j] -= ak * z[j];
                rr[j] -= ak * zz[j];
            }
            Ap.asolve(r, z);
            if (itol == 1) {
                err = snorm(r, itol) / bnrm;
            } else if (itol == 2) {
                err = snorm(z, itol) / bnrm;
            } else if (itol == 3 || itol == 4) {
                zm1nrm = znrm;
                znrm = snorm(z, itol);
                if (Math.abs(zm1nrm - znrm) > Math.EPSILON * znrm) {
                    dxnrm = Math.abs(ak) * snorm(p, itol);
                    err = znrm / Math.abs(zm1nrm - znrm) * dxnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
                xnrm = snorm(x, itol);
                if (err <= 0.5 * xnrm) {
                    err /= xnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
            }

            if (iter % 10 == 0) {
                logger.info(String.format("BCG: the error after %3d iterations: %.5g", iter, err));
            }

            if (err <= tol) {
                logger.info(String.format("BCG: the error after %3d iterations: %.5g", iter, err));
                break;
            }
        }

        return err;
    }

    /**
     * Compute L2 or L-infinity norms for a vector x, as signaled by itol.
     */
    private static double snorm(double[] x, int itol) {
        int n = x.length;

        if (itol <= 3) {
            double ans = 0.0;
            for (int i = 0; i < n; i++) {
                ans += x[i] * x[i];
            }
            return Math.sqrt(ans);
        } else {
            int isamax = 0;
            for (int i = 0; i < n; i++) {
                if (Math.abs(x[i]) > Math.abs(x[isamax])) {
                    isamax = i;
                }
            }

            return Math.abs(x[isamax]);
        }
    }
}
