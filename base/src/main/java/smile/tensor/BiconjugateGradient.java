/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import smile.math.MathEx;
import static smile.tensor.ScalarType.*;

/**
 * The biconjugate gradient method to solve systems of linear equations.
 *
 * @author Haifeng Li
 */
public interface BiconjugateGradient {
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BiconjugateGradient.class);

    /**
     * Solves A * x = b by iterative biconjugate gradient method with Jacobi
     * preconditioner matrix.
     *
     * @param A the linear system.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is set to the improved solution.
     * @return the estimated error.
     */
    static double solve(Matrix A, Vector b, Vector x) {
        return solve(A, b, x, Preconditioner.Jacobi(A), 1E-6, 1, 2 * A.nrow());
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     *
     * @param A the linear system.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is set to the improved solution.
     * @param P The preconditioner matrix.
     * @param tol The desired convergence tolerance.
     * @param itol Which convergence test is applied.
     *             If itol = 1, iteration stops when |Ax - b| / |b| is less
     *             than the parameter tolerance.
     *             If itol = 2, the stop criterion is that |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b|
     *             is less than tolerance.
     *             If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     *             tolerance.
     *             The setting of tol = 4 is same as tol = 3 except that the
     *             L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param maxIter The maximum number of iterations.
     * @return the estimated error.
     */
    static double solve(Matrix A, Vector b, Vector x, Preconditioner P, double tol, int itol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (itol < 1 || itol > 4) {
            throw new IllegalArgumentException("Invalid itol: " + itol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum iterations: " + maxIter);
        }

        double eps = A.scalarType() == Float64 ? MathEx.EPSILON : MathEx.FLOAT_EPSILON;
        double err = 0.0;
        double ak, akden, bk, bkden = 1.0, bknum, bnrm, dxnrm, xnrm, zm1nrm, znrm = 0.0;
        int j, n = b.size();

        Vector p = A.vector(n);
        Vector pp = A.vector(n);
        Vector r = A.vector(n);
        Vector rr = A.vector(n);
        Vector z = A.vector(n);
        Vector zz = A.vector(n);

        A.mv(x, r);
        for (j = 0; j < n; j++) {
            double br = b.get(j) - r.get(j);
            r.set(j, br);
            rr.set(j, br);
        }

        if (itol == 1) {
            bnrm = norm(b, itol);
            P.solve(r, z);
        } else if (itol == 2) {
            P.solve(b, z);
            bnrm = norm(z, itol);
            P.solve(r, z);
        } else { // if (itol == 3 || itol == 4) {
            P.solve(b, z);
            bnrm = norm(z, itol);
            P.solve(r, z);
            znrm = norm(z, itol);
        }

        for (int iter = 1; iter <= maxIter; iter++) {
            P.solve(rr, zz);
            for (bknum = 0.0, j = 0; j < n; j++) {
                bknum += z.get(j) * rr.get(j);
            }
            if (iter == 1) {
                for (j = 0; j < n; j++) {
                    p.set(j, z.get(j));
                    pp.set(j, zz.get(j));
                }
            } else {
                bk = bknum / bkden;
                for (j = 0; j < n; j++) {
                    p.set(j, bk * p.get(j) + z.get(j));
                    pp.set(j, bk * pp.get(j) + zz.get(j));
                }
            }
            bkden = bknum;
            A.mv(p, z);
            for (akden = 0.0, j = 0; j < n; j++) {
                akden += z.get(j) * pp.get(j);
            }
            ak = bknum / akden;
            A.tv(pp, zz);
            for (j = 0; j < n; j++) {
                x.add(j, ak * p.get(j));
                r.sub(j, ak * z.get(j));
                rr.sub(j, ak * zz.get(j));
            }
            P.solve(r, z);
            if (itol == 1) {
                err = norm(r, itol) / bnrm;
            } else if (itol == 2) {
                err = norm(z, itol) / bnrm;
            } else { // if (itol == 3 || itol == 4) {
                zm1nrm = znrm;
                znrm = norm(z, itol);
                if (Math.abs(zm1nrm - znrm) > eps * znrm) {
                    dxnrm = Math.abs(ak) * norm(p, itol);
                    err = znrm / Math.abs(zm1nrm - znrm) * dxnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
                xnrm = norm(x, itol);
                if (err <= 0.5 * xnrm) {
                    err /= xnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
            }

            if (iter % 10 == 0 || err <= tol) {
                logger.info("BCG: the error after {} iterations: {}", iter, err);
            }

            if (err <= tol) break;
        }

        return err;
    }

    /**
     * Computes L2 or L-infinity norms for a vector x, as signaled by itol.
     */
    private static double norm(Vector x, int itol) {
        if (itol <= 3) {
            return x.norm2();
        } else {
            return x.normInf();
        }
    }
}
