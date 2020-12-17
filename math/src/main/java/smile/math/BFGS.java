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

package smile.math;

import java.util.ArrayList;
import java.util.Arrays;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;
import smile.sort.QuickSort;
import smile.util.Strings;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;
import static smile.math.MathEx.axpy;
import static smile.math.MathEx.dot;
import static smile.math.MathEx.norm;

/**
 * The Broyden–Fletcher–Goldfarb–Shanno (BFGS) algorithm is an iterative
 * method for solving unconstrained nonlinear optimization problems.
 * <p>
 * The BFGS method belongs to quasi-Newton methods, a class of hill-climbing
 * optimization techniques that seek a stationary point of a (preferably
 * twice continuously differentiable) function. For such problems,
 * a necessary condition for optimality is that the gradient be zero.
 * Newton's method and the BFGS methods are not guaranteed to converge
 * unless the function has a quadratic Taylor expansion near an optimum.
 * However, BFGS has proven to have good performance even for non-smooth
 * optimizations.
 * <p>
 * In quasi-Newton methods, the Hessian matrix of second derivatives is
 * not computed. Instead, the Hessian matrix is approximated using
 * updates specified by gradient evaluations (or approximate gradient
 * evaluations). Quasi-Newton methods are generalizations of the secant
 * method to find the root of the first derivative for multidimensional
 * problems. In multi-dimensional problems, the secant equation does not
 * specify a unique solution, and quasi-Newton methods differ in how they
 * constrain the solution. The BFGS method is one of the most popular
 * members of this class.
 * <p>
 * Like the original BFGS, the limited-memory BFGS (L-BFGS) uses an
 * estimation to the inverse Hessian matrix to steer its search
 * through variable space, but where BFGS stores a dense {@code n × n}
 * approximation to the inverse Hessian (<code>n</code> being the number of
 * variables in the problem), L-BFGS stores only a few vectors
 * that represent the approximation implicitly. Due to its resulting
 * linear memory requirement, the L-BFGS method is particularly well
 * suited for optimization problems with a large number of variables
 * (e.g., {@code > 1000}). Instead of the inverse Hessian <code>H_k</code>, L-BFGS
 * maintains * a history of the past <code>m</code> updates of the position
 * <code>x</code> and gradient <code>∇f(x)</code>, where generally the
 * history size <code>m</code> can be small (often {@code m < 10}).
 * These updates are used to implicitly do operations requiring the
 * <code>H_k</code>-vector product.
 *
 * <h2>References</h2>
 * <ol>
 *     <li>Roger Fletcher. Practical methods of optimization.</li>
 *     <li>D. Liu and J. Nocedal. On the limited memory BFGS method for large scale optimization. Mathematical Programming B 45 (1989) 503-528.</li>
 *     <li>Richard H. Byrd, Peihuang Lu, Jorge Nocedal and Ciyou Zhu. A limited memory algorithm for bound constrained optimization.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class BFGS {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BFGS.class);
    /** A number close to zero, between machine epsilon and its square root. */
    private static final double EPSILON = Double.parseDouble(System.getProperty("smile.bfgs.epsilon", "1E-8"));
    /** The convergence criterion on x values. */
    private static final double TOLX = 4 * EPSILON;
    /** The convergence criterion on function value. */
    private static final double TOLF = 4 * EPSILON;
    /** The scaled maximum step length allowed in line searches. */
    private static final double STPMX = 100.0;

    /**
     * This method solves the unconstrained minimization problem
     * <pre>
     *     min f(x),    x = (x1,x2,...,x_n),
     * </pre>
     * using the BFGS method.
     *
     * @param func the function to be minimized.
     *
     * @param x on initial entry this must be set by the user to the values
     *          of the initial estimate of the solution vector. On exit, it
     *          contains the values of the variables at the best point found
     *          (usually a solution).
     *
     * @param gtol the convergence tolerance on zeroing the gradient.
     * @param maxIter the maximum number of iterations.
     *
     * @return the minimum value of the function.
     */
    public static double minimize(DifferentiableMultivariateFunction func, double[] x, double gtol, int maxIter) {
        if (gtol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + gtol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        double den, fac, fad, fae, sumdg, sumxi, temp, test;

        int n = x.length;
        double[] dg = new double[n];
        double[] g = new double[n];
        double[] hdg = new double[n];
        double[] xnew = new double[n];
        double[] xi = new double[n];
        double[][] hessin = new double[n][n];

        // Calculate starting function value and gradient and initialize the
        // inverse Hessian to the unit matrix.
        double f = func.g(x, g);
        logger.info(String.format("BFGS: initial function value: %.5f", f));

        for (int i = 0; i < n; i++) {
            hessin[i][i] = 1.0;
            // Initialize line direction.
            xi[i] = -g[i];
        }

        double stpmax = STPMX * max(norm(x), n);

        for (int iter = 1; iter <= maxIter; iter++) {
            // The new function evaluation occurs in line search.
            f = linesearch(func, x, f, g, xi, xnew, stpmax);

            if (iter % 100 == 0) {
                logger.info(String.format("BFGS: the function value after %3d iterations: %.5f", iter, f));
            }

            // update the line direction and current point.
            for (int i = 0; i < n; i++) {
                xi[i] = xnew[i] - x[i];
                x[i] = xnew[i];
            }

            // Test for convergence on x.
            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = abs(xi[i]) / max(abs(x[i]), 1.0);
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < TOLX) {
                logger.info(String.format("BFGS converges on x after %d iterations: %.5f", iter, f));
                return f;
            }

            System.arraycopy(g, 0, dg, 0, n);

            func.g(x, g);

            // Test for convergence on zero gradient.
            den = max(f, 1.0);
            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = abs(g[i]) * max(abs(x[i]), 1.0) / den;
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < gtol) {
                logger.info(String.format("BFGS converges on gradient after %d iterations: %.5f", iter, f));
                return f;
            }

            for (int i = 0; i < n; i++) {
                dg[i] = g[i] - dg[i];
            }

            for (int i = 0; i < n; i++) {
                hdg[i] = 0.0;
                for (int j = 0; j < n; j++) {
                    hdg[i] += hessin[i][j] * dg[j];
                }
            }

            fac = fae = sumdg = sumxi = 0.0;
            for (int i = 0; i < n; i++) {
                fac += dg[i] * xi[i];
                fae += dg[i] * hdg[i];
                sumdg += dg[i] * dg[i];
                sumxi += xi[i] * xi[i];
            }

            // Skip update if fac is not sufficiently positive.
            if (fac > sqrt(EPSILON * sumdg * sumxi)) {
                fac = 1.0 / fac;
                fad = 1.0 / fae;

                // The vector that makes BFGS different from DFP.
                for (int i = 0; i < n; i++) {
                    dg[i] = fac * xi[i] - fad * hdg[i];
                }

                // BFGS updating formula.
                for (int i = 0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        hessin[i][j] += fac * xi[i] * xi[j] - fad * hdg[i] * hdg[j] + fae * dg[i] * dg[j];
                        hessin[j][i] = hessin[i][j];
                    }
                }
            }

            // Calculate the next direction to go.
            Arrays.fill(xi, 0.0);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    xi[i] -= hessin[i][j] * g[j];
                }
            }
        }

        logger.warn(String.format("BFGS reaches maximum %d iterations: %.5f", maxIter, f));
        return f;

    }

    /**
     * This method solves the unconstrained minimization problem
     * <pre>
     *     min f(x),    x = (x1,x2,...,x_n),
     * </pre>
     * using the limited-memory BFGS method. The method is especially
     * effective on problems involving a large number of variables. In
     * a typical iteration of this method an approximation <code>Hk</code> to the
     * inverse of the Hessian is obtained by applying <code>m</code> BFGS updates to
     * a diagonal matrix <code>Hk0</code>, using information from the previous M steps.
     * The user specifies the number <code>m</code>, which determines the amount of
     * storage required by the routine.
     *
     * @param func the function to be minimized.
     *
     * @param m the number of corrections used in the L-BFGS update.
     *          Values of <code>m</code> less than 3 are not recommended;
     *          large values of <code>m</code> will result in excessive
     *          computing time. {@code 3 <= m <= 7} is recommended.
     *          A common choice for m is m = 5.
     *
     * @param x on initial entry this must be set by the user to the values
     *          of the initial estimate of the solution vector. On exit with
     *          {@code iflag = 0}, it contains the values of the variables
     *          at the best point found (usually a solution).
     *
     * @param gtol the convergence tolerance on zeroing the gradient.
     * @param maxIter the maximum number of iterations.
     *
     * @return the minimum value of the function.
     */
    public static double minimize(DifferentiableMultivariateFunction func, int m, double[] x, double gtol, int maxIter) {
        if (gtol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + gtol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        if (m <= 0) {
            throw new IllegalArgumentException("Invalid m: " + m);
        }

        int n = x.length;

        // The solution vector of line search.
        double[] xnew = new double[n];
        // The gradient vector of line search.
        double[] gnew = new double[n];
        // Line search direction.
        double[] xi = new double[n];

        // difference of x from previous step.
        double[][] s = new double[m][n];
        // difference of g from previous step.
        double[][] y = new double[m][n];

        // buffer for 1 / (y' * s)
        double[] rho = new double[m];
        double[] a = new double[m];

        // Diagonal of initial H0.
        double diag = 1.0;

        // Current gradient.
        double[] g = new double[n];
        // Current function value.
        double f = func.g(x, g);

        logger.info(String.format("L-BFGS: initial function value: %.5f", f));

        // Initial line search direction.
        for (int i = 0; i < n; i++) {
            xi[i] = -g[i];
        }

        // Upper limit for line search step.
        double stpmax = STPMX * max(norm(x), n);

        for (int iter = 1, k = 0; iter <= maxIter; iter++) {
            linesearch(func, x, f, g, xi, xnew, stpmax);

            f = func.g(xnew, gnew);
            for (int i = 0; i < n; i++) {
                s[k][i] = xnew[i] - x[i];
                y[k][i] = gnew[i] - g[i];
                x[i] = xnew[i];
                g[i] = gnew[i];
            }

            // Test for convergence on x.
            double test = 0.0;
            for (int i = 0; i < n; i++) {
                double temp = abs(s[k][i]) / max(abs(x[i]), 1.0);
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < TOLX) {
                logger.info(String.format("L-BFGS converges on x after %d iterations: %.5f", iter, f));
                return f;
            }

            // Test for convergence on zero gradient.
            test = 0.0;
            double den = max(f, 1.0);

            for (int i = 0; i < n; i++) {
                double temp = abs(g[i]) * max(abs(x[i]), 1.0) / den;
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < gtol) {
                logger.info(String.format("L-BFGS converges on gradient after %d iterations: %.5f", iter, f));
                return f;
            }

            if (iter % 100 == 0) {
                logger.info(String.format("L-BFGS: the function value after %3d iterations: %.5f", iter, f));
            }

            double ys = dot(y[k], s[k]);
            double yy = dot(y[k], y[k]);

            diag = ys / yy;

            rho[k] = 1.0 / ys;

            for (int i = 0; i < n; i++) {
                xi[i] = -g[i];
            }

            int cp = k;
            int bound = Math.min(iter, m);
            for (int i = 0; i < bound; i++) {
                a[cp] = rho[cp] * dot(s[cp], xi);
                axpy(-a[cp], y[cp], xi);
                if (--cp == - 1) {
                    cp = m - 1;
                }
            }

            for (int i = 0; i < n; i++) {
                xi[i] *= diag;
            }

            for (int i = 0; i < bound; i++) {
                if (++cp == m) {
                    cp = 0;
                }
                double b = rho[cp] * dot(y[cp], xi);
                axpy(a[cp]-b, s[cp], xi);
            }

            if (++k == m) {
                k = 0;
            }
        }

        logger.warn(String.format("L-BFGS reaches maximum %d iterations: %.5f", maxIter, f));
        return f;
    }

    /**
     * Minimize a function along a search direction by find a step which satisfies
     * a sufficient decrease condition and a curvature condition.
     * <p>
     * At each stage this function updates an interval of uncertainty with
     * endpoints <code>stx</code> and <code>sty</code>. The interval of
     * uncertainty is initially chosen so that it contains a
     * minimizer of the modified function
     * <pre>{@code
     *      f(x+stp*s) - f(x) - ftol*stp*(gradf(x)'s).
     * }</pre>
     * If a step is obtained for which the modified function
     * has a nonpositive function value and nonnegative derivative,
     * then the interval of uncertainty is chosen so that it
     * contains a minimizer of {@code f(x+stp*s)}.
     * <p>
     * The algorithm is designed to find a step which satisfies
     * the sufficient decrease condition
     * <pre>{@code
     *       f(x+stp*s) <= f(X) + ftol*stp*(gradf(x)'s),
     * }</pre>
     * and the curvature condition
     * <p>
     * <pre>{@code
     *       abs(gradf(x+stp*s)'s)) <= gtol*abs(gradf(x)'s).
     * }</pre>
     * If <code>ftol</code> is less than <code>gtol</code> and if, for example,
     * the function is bounded below, then there is always a step which
     * satisfies both conditions. If no step can be found which satisfies both
     * conditions, then the algorithm usually stops when rounding
     * errors prevent further progress. In this case <code>stp</code> only
     * satisfies the sufficient decrease condition.
     *
     * @param xold on input this contains the base point for the line search.
     *
     * @param fold on input this contains the value of the objective function
     *             at <code>x</code>.
     *
     * @param g on input this contains the gradient of the objective function
     *          at <code>x</code>.
     *
     * @param p the search direction.
     *
     * @param x on output, it contains <code>xold + &gamma;*p</code>, where
     *          &gamma; {@code > 0} is the step length.
     *
     * @param stpmax specify upper bound for the step in the line search so that
     *          we do not try to evaluate the function in regions where it is
     *          undefined or subject to overflow.
     *
     * @return the new function value.
     */
    private static double linesearch(MultivariateFunction func, double[] xold, double fold, double[] g, double[] p, double[] x, double stpmax) {
        if (stpmax <= 0) {
            throw new IllegalArgumentException("Invalid upper bound of linear search step: " + stpmax);
        }

        // Termination occurs when the relative width of the interval
        // of uncertainty is at most xtol.
        final double xtol = EPSILON;
        // Tolerance for the sufficient decrease condition.
        final double ftol = 1.0E-4;

        int n = xold.length;

        // Scale if attempted step is too big
        double pnorm = norm(p);
        if (pnorm > stpmax) {
            double r = stpmax / pnorm;
            for (int i = 0; i < n; i++) {
                p[i] *= r;
            }
        }

        // Check if s is a descent direction.
        double slope = 0.0;
        for (int i = 0; i < n; i++) {
            slope += g[i] * p[i];
        }

        if (slope >= 0) {
            logger.warn("Line Search: the search direction is not a descent direction, which may be caused by roundoff problem.");
        }

        // Calculate minimum step.
        double test = 0.0;
        for (int i = 0; i < n; i++) {
            double temp = abs(p[i]) / max(xold[i], 1.0);
            if (temp > test) {
                test = temp;
            }
        }

        double alammin = xtol / test;
        double alam = 1.0;

        double alam2 = 0.0, f2 = 0.0;
        double a, b, disc, rhs1, rhs2, tmpalam;
        while (true) {
            // Evaluate the function and gradient at stp
            // and compute the directional derivative.
            for (int i = 0; i < n; i++) {
                x[i] = xold[i] + alam * p[i];
            }

            double f = func.apply(x);

            // Convergence on &Delta; x.
            if (alam < alammin) {
                System.arraycopy(xold, 0, x, 0, n);
                return f;
            } else if (f <= fold + ftol * alam * slope) {
                // Sufficient function decrease.
                return f;
            } else {
                // Backtrack
                if (alam == 1.0) {
                    // First time
                    tmpalam = -slope / (2.0 * (f - fold - slope));
                } else {
                    // Subsequent backtracks.
                    rhs1 = f - fold - alam * slope;
                    rhs2 = f2 - fold - alam2 * slope;
                    a = (rhs1 / (alam * alam) - rhs2 / (alam2 * alam2)) / (alam - alam2);
                    b = (-alam2 * rhs1 / (alam * alam) + alam * rhs2 / (alam2 * alam2)) / (alam - alam2);
                    if (a == 0.0) {
                        tmpalam = -slope / (2.0 * b);
                    } else {
                        disc = b * b - 3.0 * a * slope;
                        if (disc < 0.0) {
                            tmpalam = 0.5 * alam;
                        } else if (b <= 0.0) {
                            tmpalam = (-b + sqrt(disc)) / (3.0 * a);
                        } else {
                            tmpalam = -slope / (b + sqrt(disc));
                        }
                    }
                    if (tmpalam > 0.5 * alam) {
                        tmpalam = 0.5 * alam;
                    }
                }
            }
            alam2 = alam;
            f2 = f;
            alam = max(tmpalam, 0.1 * alam);
        }
    }

    /**
     * This method solves the bound constrained minimization problem
     * using the L-BFGS-B method. The L-BFGS-B algorithm extends L-BFGS
     * to handle simple box constraints on variables; that is, constraints
     * of the form li ≤ xi ≤ ui where li and ui are per-variable constant
     * lower and upper bounds, respectively (for each xi, either or both
     * bounds may be omitted). The method works by identifying fixed and
     * free variables at every step (using a simple gradient method),
     * and then using the L-BFGS method on the free variables only to
     * get higher accuracy, and then repeating the process.
     *
     * @param func the function to be minimized.
     *
     * @param m the number of corrections used in the L-BFGS update.
     *          Values of <code>m</code> less than 3 are not recommended;
     *          large values of <code>m</code> will result in excessive
     *          computing time. {@code 3 <= m <= 7} is recommended.
     *          A common choice for m is m = 5.
     *
     * @param x on initial entry this must be set by the user to the values
     *          of the initial estimate of the solution vector. On exit with
     *          {@code iflag = 0}, it contains the values of the variables
     *          at the best point found (usually a solution).
     *
     * @param l the lower bound.
     * @param u the upper bound.
     * @param gtol the convergence tolerance on zeroing the gradient.
     * @param maxIter the maximum number of iterations.
     *
     * @return the minimum value of the function.
     */
    public static double minimize(DifferentiableMultivariateFunction func, int m, double[] x, double[] l, double[] u, double gtol, int maxIter) {
        if (gtol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + gtol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        if (m <= 0) {
            throw new IllegalArgumentException("Invalid m: " + m);
        }

        if (l.length != x.length) {
            throw new IllegalArgumentException("Invalid lower bound size: " + l.length);
        }

        if (u.length != x.length) {
            throw new IllegalArgumentException("Invalid upper bound size: " + u.length);
        }

        int n = x.length;
        double theta = 1.0;

        Matrix Y = null, S = null;
        Matrix W = new Matrix(n, 1);
        Matrix M = new Matrix(1, 1);

        ArrayList<double[]> yHistory = new ArrayList<>();
        ArrayList<double[]> sHistory = new ArrayList<>();
        double[] y = new double[n];
        double[] s = new double[n];

        double[] p = new double[n];
        double[] g = new double[n];
        double[] cauchy = new double[n];
        double f   = func.g(x, g);

        double[] x_old = new double[n];
        double[] g_old = new double[n];

        // Upper limit for line search step.
        double stpmax = STPMX * max(norm(x), n);

        for (int iter = 1; iter <= maxIter; iter++) {
            double f_old   = f;
            System.arraycopy(x, 0, x_old, 0, n);
            System.arraycopy(g, 0, g_old, 0, n);

            // STEP 2: compute the cauchy point
            System.arraycopy(x, 0, cauchy, 0, n);
            double[] c = cauchy(x, g, cauchy, l, u, theta, W, M);
            clampToBound(cauchy, l, u);

            // STEP 3: compute a search direction d_k by the primal method for the sub-problem
            double[] subspaceMin = subspaceMinimization(x, g, cauchy, c, l, u, theta, W, M);
            clampToBound(subspaceMin, l, u);

            // STEP 4: perform line search
            for (int i = 0; i < n; i++) {
                p[i] = subspaceMin[i] - x[i];
            }
            linesearch(func, x_old, f_old, g, p, x, stpmax);
            clampToBound(x, l, u);

            for (double xi : x) {
                if (Double.isNaN(xi) || Double.isInfinite(xi)) {
                    logger.warn("L-BFGS-B: bad x produced by line search, return previous good x");
                    System.arraycopy(x_old, 0, x, 0, n);
                    return f_old;
                }
            }

            // STEP 5: compute gradient update current guess and function information
            f = func.g(x, g);
            if (Double.isNaN(f) || Double.isInfinite(f)) {
                logger.warn("L-BFGS-B: bad f(x) produced by line search, return previous good x");
                System.arraycopy(x_old, 0, x, 0, n);
                return f_old;
            }

            if (gnorm(x, g, l, u) < gtol) {
                logger.info(String.format("L-BFGS-B converges on gradient after %d iterations: %.5f", iter, f));
                return f;
            }

            if (iter % 100 == 0) {
                logger.info(String.format("L-BFGS-B: the function value after %3d iterations: %.5f", iter, f));
            }

            // prepare for next iteration
            for (int i = 0; i < n; i++) {
                y[i] = g[i] - g_old[i];
                s[i] = x[i] - x_old[i];
            }

            // STEP 6
            double sy = dot(s, y);
            double yy = dot(y, y);
            double test = abs(sy);
            if (test > EPSILON * yy) {
                if (yHistory.size() >= m) {
                    yHistory.remove (0);
                    sHistory.remove (0);
                }

                yHistory.add(y);
                sHistory.add(s);

                int h = yHistory.size();
                if (iter <= m) {
                    Y = new Matrix(n, h);
                    S = new Matrix(n, h);
                    W = new Matrix(n, 2*h);
                    M = new Matrix(2*h, 2*h);
                }

                // STEP 7
                theta = yy / sy;
                for (int j = 0; j < h; j++) {
                    double[] yj = yHistory.get(j);
                    double[] sj = sHistory.get(j);

                    for (int i = 0; i < n; i++) {
                        Y.set(i, j, yj[i]);
                        S.set(i, j, sj[i]);
                        W.set(i, j, yj[i]);
                        W.set(i, h+j, sj[i] * theta);
                    }
                }

                Matrix SY = S.tm(Y);
                Matrix SS = S.ata();
                for (int j = 0; j < h; j++) {
                    M.set(j, j, -SY.get(j, j));
                    for (int i = 0; i <= j; i++) {
                        M.set(h+i, j, 0.0);
                        M.set(j, h+i, 0.0);
                    }

                    for (int i = j+1; i < h; i++) {
                        M.set(h+i, j, SY.get(i, j));
                        M.set(j, h+i, SY.get(i, j));
                    }

                    for (int i = 0; i < h; i++) {
                        M.set(h+i, h+j, theta * SS.get(i, j));
                    }
                }

                M.uplo(UPLO.LOWER);
                M = M.inverse();
            }

            logger.debug("L-BFGS-B iteration {} moves from {} to {} where f(x) = {}", iter, Strings.toString(x_old), Strings.toString(x), f);

            if (abs(f_old - f) < TOLF) {
                logger.info(String.format("L-BFGS-B converges on f(x) after %d iterations: %.5f", iter, f));
                return f;
            }
        }

        logger.warn(String.format("L-BFGS-B reaches maximum %d iterations: %.5f", maxIter, f));
        return f;
    }

    /**
     * Algorithm CP: Computation of the Generalized Cauchy Point. See page 8.
     * @param x  the parameter vector
     * @param g  the gradient vector
     */
    private static double[] cauchy(double[] x, double[] g, double[] cauchy, double[] l, double[] u, double theta, Matrix W, Matrix M) {
        int n = x.length;
        double[] t = new double[n];
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = g[i] == 0 ? Double.MAX_VALUE
                : (g[i] <  0 ? (x[i] - u[i]) / g[i]
                             : (x[i] - l[i]) / g[i]);
            if (t[i] != 0.0) d[i] = -g[i];
        }

        int[] index = QuickSort.sort(t);
        double[] p = W.tv(d);
        double[] c = new double[p.length];
        double fPrime = -dot(d, d);
        double fDoublePrime  = max(-theta * fPrime - M.xAx(p), EPSILON);
        double f_dp_orig     = fDoublePrime;
        double dt_min        = -fPrime / fDoublePrime;
        double t_old         = 0.0;

        int i = 0;
        for (; i < n; i++) {
            if (t[index[i]] > 0) break;
        }

        double dt = t[i];
        for (; dt_min >= dt && i < n; i++) {
            int b = index[i];
            double tb = t[i];
            dt = tb - t_old;

            cauchy[b] = d[b] > 0 ? u[b] :
                       (d[b] < 0 ? l[b] : cauchy[b]);
            double zb = cauchy[b] - x[b];
            for (int j = 0; j < c.length; j++) {
                c[j] += p[j] * dt;
            }

            double gb = g[b];
            double[] wbt  = W.row(b);
            fPrime       += dt * fDoublePrime + gb * gb + theta * gb * zb - gb * dot(wbt, M.mv(c));
            fDoublePrime -= theta * gb * gb + 2.0 * gb * dot(wbt, M.mv(p)) + gb * gb * M.xAx(wbt);
            fDoublePrime  = max(fDoublePrime, EPSILON * f_dp_orig);
            for (int j = 0; j < p.length; j++) {
                p[j] += wbt[j] * gb;
            }

            d[b]          = 0;
            dt_min        = -fPrime / fDoublePrime;
            t_old         = tb;
        }

        dt_min = max(dt_min, 0.0);
        t_old += dt_min;

        for (int ii = i; ii < n; ii++) {
            int si = index[ii];
            cauchy[si] = x[si] + t_old * d[si];
        }

        for (int j = 0; j < c.length; j++) {
            c[j] += p[j] * dt_min;
        }

        return c;
    }

    /**
     * Minimization of the subspace of free variables. See Section 5 on page 9.
     * @param x        the parameter vector
     * @param g        the gradient vector
     * @param cauchy   the cauchy points
     * @param c        vector obtained from gcp() used to initialize the subspace
     *                 minimization process
     */
    private static double[] subspaceMinimization(double[] x, double[] g, double[] cauchy, double[] c, double[] l, double[] u, double theta, Matrix W, Matrix M) {
        int n = x.length;
        double thetaInverse = 1.0 / theta;
        ArrayList<Integer> freeVarIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (cauchy[i] != u[i] && cauchy[i] != l[i]) {
                freeVarIdx.add(i);
            }
        }

        if (freeVarIdx.isEmpty()) {
            return cauchy.clone();
        }

        int freeVarCount = freeVarIdx.size();
        int[] freeVar = new int[freeVarCount];
        for (int i = 0; i < freeVarCount; i++) {
            freeVar[i] = freeVarIdx.get(i);
        }

        double[] wmc = W.mv(M.mv(c));
        double[] r = new double[freeVarCount];
        for (int i = 0; i < freeVarCount; i++) {
            int fi = freeVar[i];
            r[i] = g[fi] + (cauchy[fi] - x[fi]) * theta - wmc[fi];
        }

        Matrix WZ = W.row(freeVar);
        double[] v  = M.mv(WZ.tv(r));
        Matrix N = WZ.ata().mul(-thetaInverse);
        N = M.mm(N);
        int n1 = N.nrow();
        for (int i = 0; i < n1; i++) {
            N.add(i, i, 1.0);
        }

        Matrix.LU lu = N.lu();
        v = lu.solve(v);

        double[] wzv = WZ.mv(v);

        double[] du = new double[freeVarCount];
        for (int i = 0; i < freeVarCount; i++) {
            du[i] = -thetaInverse * (r[i] + wzv[i] * thetaInverse);
        }
        double alphaStar = findAlpha(cauchy, du, l, u, freeVar);
        double[] dStar   = new double[freeVarCount];
        for (int i = 0; i < freeVarCount; i++) {
            dStar[i] = du[i] * alphaStar;
        }

        double[] subspaceMin = cauchy.clone();
        for (int i = 0; i < freeVarCount; i++) {
            subspaceMin[freeVar[i]] += dStar[i];
        }

        return subspaceMin;
    }

    /**
     * Find the alpha* parameter, a positive scalar. See Equation 5.8 on page 11.
     * @param cauchy   cauchy point
     * @param du       intermediate results used to find alpha*
       @param freeVar  the indices of free variable
     */
    private static double findAlpha(double[] cauchy, double[] du, double[] l, double[] u, int[] freeVar) {
        double alphaStar = 1.0;
        int n = freeVar.length;

        for (int i = 0; i < n; i++) {
            int fi = freeVar[i];
            alphaStar = du[i] > 0 ? min(alphaStar, (u[fi] - cauchy[fi]) / du[i])
                                  : min(alphaStar, (l[fi] - cauchy[fi]) / du[i]);
        }

        return alphaStar;
    }

    /**
     * Returns the infinity norm of  gradient.
     * @param x  the parameter vector
     * @param g  the gradient vector
     */
    private static double gnorm(double[] x, double[] g, double[] l, double[] u) {
        double norm = 0.0;
        int n = x.length;

        for (int i = 0; i < n; i++) {
            double gi = g[i];
            if (gi < 0) gi = max(x[i] - u[i], gi);
            else gi = min(x[i] - l[i], gi);
            norm = max(norm, abs(gi));
        }

        return norm;
    }

    /**
     * Clamps the values of v to stay within the pre-defined bounds.
     * @param v  the vector to be adjusted
     */
    private static void clampToBound(double[] v, double[] l, double[] u) {
        int n = v.length;

        for (int i = 0; i < n; i++) {
            if (v[i] > u[i]) v[i] = u[i];
            else if (v[i] < l[i]) v[i] = l[i];
        }
    }
}
