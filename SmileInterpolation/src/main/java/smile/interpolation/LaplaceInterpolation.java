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
package smile.interpolation;

import java.util.Arrays;

/**
 * Laplace interpolation to restore missing or unmeasured values on a 2-dimensional
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
        int nrows = matrix.length;
        int ncols = matrix[0].length;
        int n = nrows * ncols;

        double[] b = new double[n];
        double[] y = new double[n];
        boolean[] mask = new boolean[n];

        double vl = 0.;
        for (int k = 0; k < n; k++) {
            int i = k / ncols;
            int j = k - i * ncols;
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

        for (int k = 0, i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
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
        int nrows = matrix.length;
        int ncols = matrix[0].length;

        int n = r.length, jjt, it;

        Arrays.fill(r, 0.0);
        for (int k = 0; k < n; k++) {
            int i = k / ncols;
            int j = k - i * ncols;
            if (mask[k]) {
                r[k] += x[k];
            } else if (i > 0 && i < nrows - 1 && j > 0 && j < ncols - 1) {
                r[k] = x[k] - 0.25 * (x[k - 1] + x[k + 1] + x[k + ncols] + x[k - ncols]);
            } else if (i > 0 && i < nrows - 1) {
                r[k] = x[k] - 0.5 * (x[k + ncols] + x[k - ncols]);
            } else if (j > 0 && j < ncols - 1) {
                r[k] = x[k] - 0.5 * (x[k + 1] + x[k - 1]);
            } else {
                jjt = i == 0 ? ncols : -ncols;
                it = j == 0 ? 1 : -1;
                r[k] = x[k] - 0.5 * (x[k + jjt] + x[k + it]);
            }
        }
    }

    /**
     * Returns A' * x.
     */
    private static void atx(double[][] matrix, double[] x, double[] r, boolean[] mask) {
        int nrows = matrix.length;
        int ncols = matrix[0].length;

        int n = r.length, jjt, it;
        double del;
        
        Arrays.fill(r, 0.0);
        for (int k = 0; k < n; k++) {
            int i = k / ncols;
            int j = k - i * ncols;

            if (mask[k]) {
                r[k] += x[k];
            } else if (i > 0 && i < nrows - 1 && j > 0 && j < ncols - 1) {
                r[k] += x[k];
                del = -0.25 * x[k];
                r[k - 1] += del;
                r[k + 1] += del;
                r[k - ncols] += del;
                r[k + ncols] += del;
            } else if (i > 0 && i < nrows - 1) {
                r[k] += x[k];
                del = -0.5 * x[k];
                r[k - ncols] += del;
                r[k + ncols] += del;
            } else if (j > 0 && j < ncols - 1) {
                r[k] += x[k];
                del = -0.5 * x[k];
                r[k - 1] += del;
                r[k + 1] += del;
            } else {
                jjt = i == 0 ? ncols : -ncols;
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
        int n = sx.length;

        double ans = 0.0;
        for (int i = 0; i < n; i++) {
            ans += sx[i] * sx[i];
        }
        return Math.sqrt(ans);
    }
}
