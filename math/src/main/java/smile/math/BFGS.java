/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.math;

import java.util.Arrays;

/**
 * The Broyden–Fletcher–Goldfarb–Shanno (BFGS) algorithm is an iterative
 * method for solving unconstrained nonlinear optimization problems.
 *
 * The BFGS method belongs to quasi-Newton methods, a class of hill-climbing
 * optimization techniques that seek a stationary point of a (preferably
 * twice continuously differentiable) function. For such problems,
 * a necessary condition for optimality is that the gradient be zero.
 * Newton's method and the BFGS methods are not guaranteed to converge
 * unless the function has a quadratic Taylor expansion near an optimum.
 * However, BFGS has proven to have good performance even for non-smooth
 * optimizations.
 *
 * In quasi-Newton methods, the Hessian matrix of second derivatives is
 * not computed. Instead, the Hessian matrix is approximated using
 * updates specified by gradient evaluations (or approximate gradient
 * evaluations). Quasi-Newton methods are generalizations of the secant
 * method to find the root of the first derivative for multidimensional
 * problems. In multi-dimensional problems, the secant equation does not
 * specify a unique solution, and quasi-Newton methods differ in how they
 * constrain the solution. The BFGS method is one of the most popular
 * members of this class.
 *
 * Like the original BFGS, the limited-memory BFGS (L-BFGS) uses an
 * estimation to the inverse Hessian matrix to steer its search
 * through variable space, but where BFGS stores a dense n × n
 * approximation to the inverse Hessian (n being the number of
 * variables in the problem), L-BFGS stores only a few vectors
 * that represent the approximation implicitly. Due to its resulting
 * linear memory requirement, the L-BFGS method is particularly well
 * suited for optimization problems with a large number of variables
 * (e.g., > 1000). Instead of the inverse Hessian H_k, L-BFGS maintains
 * a history of the past m updates of the position x and gradient ∇f(x),
 * where generally the history size m can be small (often m < 10). These
 * updates are used to implicitly do operations requiring the H_k-vector
 * product.
 *
 * <h2>References</h2>
 * <ol>
 * <li>D. Liu and J. Nocedal. On the limited memory BFGS method for large scale optimization. Mathematical Programming B 45 (1989) 503-528.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class BFGS {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BFGS.class);

    /**
     * The desired convergence tolerance.
     */
    private double gtol = 1E-5;
    /**
     * The maximum number of allowed iterations.
     */
    private int maxIter = 500;

    /**
     * Constructor with gtol = 1E-5 and maxIter = 500.
     */
    public BFGS() {
        this(1E-5, 500);
    }

    /**
     * Constructor.
     * @param gtol the convergence requirement on zeroing the gradient.
     * @param maxIter the maximum number of allowed iterations.
     */
    public BFGS(double gtol, int maxIter) {
        if (gtol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + gtol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        this.gtol = gtol;
        this.maxIter = maxIter;
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
     *          computing time. <code>3 &lt;= m &lt;= 7</code> is recommended.
     *          A common choice for m is m = 5.
     *
     * @param x on initial entry this must be set by the user to the values
     *          of the initial estimate of the solution vector. On exit with
     *          <code>iflag = 0</code>, it contains the values of the variables
     *          at the best point found (usually a solution).
     *
     * @return the minimum value of the function.
     */
    public double minimize(DifferentiableMultivariateFunction func, int m, double[] x) {
        // Initialize.
        if (m <= 0) {
            throw new IllegalArgumentException("Invalid m: " + m);
        }

        // The convergence criterion on x values.
        final double TOLX = 4 * MathEx.EPSILON;
        // The scaled maximum step length allowed in line searches.
        final double STPMX = 100.0;

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

        double sum = 0.0;
        // Initial line search direction.
        for (int i = 0; i < n; i++) {
            xi[i] = -g[i];
            sum += x[i] * x[i];
        }

        // Upper limit for line search step.
        double stpmax = STPMX * Math.max(Math.sqrt(sum), n);

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
                double temp = Math.abs(s[k][i]) / Math.max(Math.abs(x[i]), 1.0);
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
            double den = Math.max(f, 1.0);

            for (int i = 0; i < n; i++) {
                double temp = Math.abs(g[i]) * Math.max(Math.abs(x[i]), 1.0) / den;
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

            double ys = MathEx.dot(y[k], s[k]);
            double yy = MathEx.dot(y[k], y[k]);

            diag = ys / yy;

            rho[k] = 1.0 / ys;

            for (int i = 0; i < n; i++) {
                xi[i] = -g[i];
            }

            int cp = k;
            int bound = iter > m ? m : iter;
            for (int i = 0; i < bound; i++) {
                a[cp] = rho[cp] * MathEx.dot(s[cp], xi);
                MathEx.axpy(-a[cp], y[cp], xi);
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
                double b = rho[cp] * MathEx.dot(y[cp], xi);
                MathEx.axpy(a[cp]-b, s[cp], xi);
            }

            if (++k == m) {
                k = 0;
            }
        }

        logger.warn("L-BFGS reaches the maximum number of iterations: " + maxIter);
        return f;
    }

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
     * @return the minimum value of the function.
     */
    public double minimize(DifferentiableMultivariateFunction func, double[] x) {
        // The convergence criterion on x values.
        final double TOLX = 4 * MathEx.EPSILON;
        // The scaled maximum step length allowed in line searches.
        final double STPMX = 100.0;

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

        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            hessin[i][i] = 1.0;
            // Initialize line direction.
            xi[i] = -g[i];
            sum += x[i] * x[i];
        }

        double stpmax = STPMX * Math.max(Math.sqrt(sum), n);

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
                temp = Math.abs(xi[i]) / Math.max(Math.abs(x[i]), 1.0);
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
            den = Math.max(f, 1.0);
            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = Math.abs(g[i]) * Math.max(Math.abs(x[i]), 1.0) / den;
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

            // Skip upudate if fac is not sufficiently positive.
            if (fac > Math.sqrt(MathEx.EPSILON * sumdg * sumxi)) {
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

        logger.warn("BFGS reaches the maximum number of iterations: " + maxIter);
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
     * <pre>
     *      f(x+stp*s) - f(x) - ftol*stp*(gradf(x)'s).
     * </pre>
     * If a step is obtained for which the modified function
     * has a nonpositive function value and nonnegative derivative,
     * then the interval of uncertainty is chosen so that it
     * contains a minimizer of <code>f(x+stp*s)</code>.
     * <p>
     * The algorithm is designed to find a step which satisfies
     * the sufficient decrease condition
     * <pre>
     *       f(x+stp*s) &lt;= f(X) + ftol*stp*(gradf(x)'s),
     * </pre>
     * and the curvature condition
     * <pre>
     *       abs(gradf(x+stp*s)'s)) &lt;= gtol*abs(gradf(x)'s).
     * </pre>
     * If <code>ftol</code> is less than <code>gtol</code> and if, for example,
     * the function is bounded below, then there is always a step which
     * satisfies both conditions. If no step can be found which satisfies both
     * conditions, then the algorithm usually stops when rounding
     * errors prevent further progress. In this case <code>stp</code> only
     * satisfies the sufficient decrease condition.
     * <p>
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
     * @param x on output, it contains <code>xold + alam*p</code>.
     *
     * @param stpmax specify upper bound for the step in the line search so that
     *          we do not try to evaluate the function in regions where it is
     *          undefined or subject to overflow.
     *
     * @return the new function value.
     */
    private double linesearch(MultivariateFunction func, double[] xold, double fold, double[] g, double[] p, double[] x, double stpmax) {
        if (stpmax <= 0) {
            throw new IllegalArgumentException("Invalid upper bound of linear search step: " + stpmax);
        }

        // Termination occurs when the relative width of the interval
        // of uncertainty is at most xtol.
        final double xtol = MathEx.EPSILON;
        // Tolerance for the sufficient decrease condition.
        final double ftol = 1.0E-4;

        int n = xold.length;

        // Scale if attempted step is too big
        double pnorm = MathEx.norm(p);
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
            throw new IllegalArgumentException("Line Search: the search direction is not a descent direction, which may be caused by roundoff problem.");
        }

        // Calculate minimum step.
        double test = 0.0;
        for (int i = 0; i < n; i++) {
            double temp = Math.abs(p[i]) / Math.max(xold[i], 1.0);
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
                            tmpalam = (-b + Math.sqrt(disc)) / (3.0 * a);
                        } else {
                            tmpalam = -slope / (b + Math.sqrt(disc));
                        }
                    }
                    if (tmpalam > 0.5 * alam) {
                        tmpalam = 0.5 * alam;
                    }
                }
            }
            alam2 = alam;
            f2 = f;
            alam = Math.max(tmpalam, 0.1 * alam);
        }
    }
}
