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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util.function;

/**
 * A differentiable function is a function whose derivative exists at each point
 * in its domain.
 *
 * @author Haifeng Li
 */
public interface DifferentiableFunction extends Function {
    /**
     * Computes the gradient/derivative at x.
     *
     * @param x a real number.
     * @return the derivative.
     */
    double g(double x);

    /**
     * Compute the second-order derivative at x.
     *
     * @param x a real number.
     * @return the second-order derivative.
     */
    default double g2(double x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Newton's method (also known as the Newton–Raphson method). This method
     * finds successively better approximations to the roots of a real-valued
     * function. Newton's method assumes the function to have a continuous
     * derivative. Newton's method may not converge if started too far away
     * from a root. However, when it does converge, it is faster than the
     * bisection method, and is usually quadratic. Newton's method is also
     * important because it readily generalizes to higher-dimensional problems.
     * Newton-like methods with higher orders of convergence are the
     * Householder's methods.
     *
     * @param x1 the left end of search interval.
     * @param x2 the right end of search interval.
     * @param tol the desired accuracy (convergence tolerance).
     * @param maxIter the maximum number of iterations.
     * @return the root.
     */
    default double root(double x1, double x2, double tol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        double fl = f(x1);
        double fh = f(x2);
        if ((fl > 0.0 && fh > 0.0) || (fl < 0.0 && fh < 0.0)) {
            throw new IllegalArgumentException("Root must be bracketed in rtsafe");
        }

        if (fl == 0.0) {
            return x1;
        }
        if (fh == 0.0) {
            return x2;
        }

        double xh, xl;
        if (fl < 0.0) {
            xl = x1;
            xh = x2;
        } else {
            xh = x1;
            xl = x2;
        }
        double rts = 0.5 * (x1 + x2);
        double dxold = Math.abs(x2 - x1);
        double dx = dxold;
        double f = f(rts);
        double df = g(rts);
        for (int iter = 1; iter <= maxIter; iter++) {
            if ((((rts - xh) * df - f) * ((rts - xl) * df - f) > 0.0) || (Math.abs(2.0 * f) > Math.abs(dxold * df))) {
                dxold = dx;
                dx = 0.5 * (xh - xl);
                rts = xl + dx;
                if (xl == rts) {
                    logger.info("Newton-Raphson finds the root after {} iterations: {}, error = {}", iter, rts, dx);
                    return rts;
                }
            } else {
                dxold = dx;
                dx = f / df;
                double temp = rts;
                rts -= dx;
                if (temp == rts) {
                    logger.info("Newton-Raphson finds the root after {} iterations: {}, error = {}", iter, rts, dx);
                    return rts;
                }
            }

            if (iter % 10 == 0) {
                logger.info("Newton-Raphson: the root after {} iterations: {}, error = {}", iter, rts, dx);
            }

            if (Math.abs(dx) < tol) {
                logger.info("Newton-Raphson finds the root after {} iterations: {}, error = {}", iter, rts, dx);
                return rts;
            }

            f = f(rts);
            df = g(rts);
            if (f < 0.0) {
                xl = rts;
            } else {
                xh = rts;
            }
        }

        logger.error("Newton-Raphson exceeded the maximum number of iterations.");
        return rts;
    }
}
