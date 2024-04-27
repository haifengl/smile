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

package smile.interpolation;

import java.util.Arrays;

/**
 * Laplace's interpolation to restore missing or unmeasured values on a 2-dimensional
 * evenly spaced regular grid. In some sense, Laplace interpolation
 * produces the smoothest possible interpolant, which are obtained by solving
 * a very sparse linear equations with biconjugate gradient method.
 *
 * @author Haifeng Li
 */
public class LaplaceInterpolation {
    /**
     * Laplace interpolation.
     * @param matrix on input, values of NaN are deemed to be missing data.
     * On output, they are refilled with the interpolated solution.
     * @return the estimated error.
     */
    public static double interpolate(double[][] matrix) {
        return interpolate(matrix, 1.0E-6);
    }

    /**
     * Laplace interpolation.
     * @param matrix on input, values of NaN are deemed to be missing data.
     * On output, they are refilled with the interpolated solution.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double interpolate(double[][] matrix, double tol) {
        return interpolate(matrix, tol, 2 * Math.max(matrix.length, matrix[0].length));
    }

    /**
     * Laplace interpolation.
     * @param matrix on input, values of NaN are deemed to be missing data.
     * On output, they are refilled with the interpolated solution.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the estimated error.
     */
    public static double interpolate(double[][] matrix, double tol, int maxIter) {
        int nrow = matrix.length;
        int ncol = matrix[0].length;
        int n = nrow * ncol;

        double[] b = new double[n];
        double[] y = new double[n];
        boolean[] mask = new boolean[n];

        double vl = 0.;
        for (int k = 0; k < n; k++) {
            int i = k / ncol;
            int j = k - i * ncol;
            if (!Double.isNaN(matrix[i][j])) {
                b[k] = y[k] = vl = matrix[i][j];
                mask[k] = true;
            } else {
                b[k] = 0.;
                y[k] = vl;
                mask[k] = false;
            }
        }

        double err = solve(matrix, b, y, mask, tol, maxIter);

        for (int k = 0, i = 0; i < nrow; i++) {
            for (int j = 0; j < ncol; j++) {
                matrix[i][j] = y[k++];
            }
        }

        return err;
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the estimated error.
     */
    private static double solve(double[][] matrix, double[] b, double[] x, boolean[] mask, double tol, int maxIter) {
        double err = 0.0;
        double ak, akden, bk, bkden = 1.0, bknum, bnrm;
        int j, n = b.length;

        double[] p = new double[n];
        double[] pp = new double[n];
        double[] r = new double[n];
        double[] rr = new double[n];
        double[] z = new double[n];
        double[] zz = new double[n];

        ax(matrix, x, r, mask);
        for (j = 0; j < n; j++) {
            r[j] = b[j] - r[j];
            rr[j] = r[j];
        }

        bnrm = snorm(b);
        asolve(r, z);

        for (int iter = 0; iter < maxIter; iter++) {
            asolve(rr, zz);
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
            ax(matrix, p, z, mask);
            for (akden = 0.0, j = 0; j < n; j++) {
                akden += z[j] * pp[j];
            }
            ak = bknum / akden;
            atx(matrix, pp, zz, mask);
            for (j = 0; j < n; j++) {
                x[j] += ak * p[j];
                r[j] -= ak * z[j];
                rr[j] -= ak * zz[j];
            }
            asolve(r, z);

            err = snorm(r) / bnrm;
            if (err <= tol) {
                break;
            }
        }

        return err;
    }

    /**
     * Solve A' * x = b for some preconditioner matrix A', which is possibly
     * the trivial diagonal part of A.
     */
    private static void asolve(double[] b, double[] x) {
        System.arraycopy(b, 0, x, 0, b.length);
    }

    /**
     * Returns A * x.
     */
    private static void ax(double[][] matrix, double[] x, double[] r, boolean[] mask) {
        int nrow = matrix.length;
        int ncol = matrix[0].length;

        int n = r.length, jjt, it;

        Arrays.fill(r, 0.0);
        for (int k = 0; k < n; k++) {
            int i = k / ncol;
            int j = k - i * ncol;
            if (mask[k]) {
                r[k] += x[k];
            } else if (i > 0 && i < nrow - 1 && j > 0 && j < ncol - 1) {
                r[k] = x[k] - 0.25 * (x[k - 1] + x[k + 1] + x[k + ncol] + x[k - ncol]);
            } else if (i > 0 && i < nrow - 1) {
                r[k] = x[k] - 0.5 * (x[k + ncol] + x[k - ncol]);
            } else if (j > 0 && j < ncol - 1) {
                r[k] = x[k] - 0.5 * (x[k + 1] + x[k - 1]);
            } else {
                jjt = i == 0 ? ncol : -ncol;
                it = j == 0 ? 1 : -1;
                r[k] = x[k] - 0.5 * (x[k + jjt] + x[k + it]);
            }
        }
    }

    /**
     * Returns A' * x.
     */
    private static void atx(double[][] matrix, double[] x, double[] r, boolean[] mask) {
        int nrow = matrix.length;
        int ncol = matrix[0].length;

        int n = r.length, jjt, it;
        double del;
        
        Arrays.fill(r, 0.0);
        for (int k = 0; k < n; k++) {
            int i = k / ncol;
            int j = k - i * ncol;

            if (mask[k]) {
                r[k] += x[k];
            } else if (i > 0 && i < nrow - 1 && j > 0 && j < ncol - 1) {
                r[k] += x[k];
                del = -0.25 * x[k];
                r[k - 1] += del;
                r[k + 1] += del;
                r[k - ncol] += del;
                r[k + ncol] += del;
            } else if (i > 0 && i < nrow - 1) {
                r[k] += x[k];
                del = -0.5 * x[k];
                r[k - ncol] += del;
                r[k + ncol] += del;
            } else if (j > 0 && j < ncol - 1) {
                r[k] += x[k];
                del = -0.5 * x[k];
                r[k - 1] += del;
                r[k + 1] += del;
            } else {
                jjt = i == 0 ? ncol : -ncol;
                it = j == 0 ? 1 : -1;
                r[k] += x[k];
                del = -0.5 * x[k];
                r[k + jjt] += del;
                r[k + it] += del;
            }
        }
    }

    /**
     * Compute squared root of L2 norms for a vector.
     */
    private static double snorm(double[] sx) {
        double ans = 0.0;
        for (double v : sx) {
            ans += v * v;
        }
        return Math.sqrt(ans);
    }

    @Override
    public String toString() {
        return "Laplace Interpolation";
    }
}
