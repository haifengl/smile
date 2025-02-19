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
package smile.util.function;

import java.io.Serializable;
import smile.math.MathEx;

/**
 * An interface representing a univariate real function.
 *
 * @author Haifeng Li
 */
public interface Function extends Serializable {
    /** Logging facility. */
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Function.class);

    /**
     * Computes the value of the function at x.
     *
     * @param x a real number.
     * @return the function value.
     */
    double f(double x);

    /**
     * Computes the value of the inverse function at x.
     *
     * @param x a real number.
     * @return the inverse function value.
     */
    default double inv(double x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Computes the value of the function at x.
     * It delegates the computation to f().
     * This is simply for Scala convenience.
     *
     * @param x a real number.
     * @return the function value.
     */
    default double apply(double x) {
        return f(x);
    }

    /**
     * Brent's method for root-finding. It combines the bisection method,
     * the secant method and inverse quadratic interpolation. It has the
     * reliability of bisection, but it can be as quick as some of the
     * less-reliable methods. The algorithm tries to use the potentially
     * fast-converging secant method or inverse quadratic interpolation
     * if possible, but it falls back to the more robust bisection method
     * if necessary.
     * <p>
     * The method is guaranteed to converge as long as the function can be
     * evaluated within the initial interval known to contain a root.
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

        double a = x1, b = x2, c = x2, d = 0, e = 0, fa = f(a), fb = f(b), fc, p, q, r, s, xm;
        if ((fa > 0.0 && fb > 0.0) || (fa < 0.0 && fb < 0.0)) {
            throw new IllegalArgumentException("Root must be bracketed.");
        }

        fc = fb;
        for (int iter = 1; iter <= maxIter; iter++) {
            if ((fb > 0.0 && fc > 0.0) || (fb < 0.0 && fc < 0.0)) {
                c = a;
                fc = fa;
                e = d = b - a;
            }

            if (Math.abs(fc) < Math.abs(fb)) {
                a = b;
                b = c;
                c = a;
                fa = fb;
                fb = fc;
                fc = fa;
            }

            tol = 2.0 * MathEx.EPSILON * Math.abs(b) + 0.5 * tol;
            xm = 0.5 * (c - b);

            if (iter % 10 == 0) {
                logger.info("Brent: the root after {} iterations: {}, error = {}", iter, b, xm);
            }

            if (Math.abs(xm) <= tol || fb == 0.0) {
                logger.info("Brent finds the root after {} iterations: {}, error = {}", iter, b, xm);
                return b;
            }

            if (Math.abs(e) >= tol && Math.abs(fa) > Math.abs(fb)) {
                s = fb / fa;
                if (a == c) {
                    p = 2.0 * xm * s;
                    q = 1.0 - s;
                } else {
                    q = fa / fc;
                    r = fb / fc;
                    p = s * (2.0 * xm * q * (q - r) - (b - a) * (r - 1.0));
                    q = (q - 1.0) * (r - 1.0) * (s - 1.0);
                }

                if (p > 0.0) {
                    q = -q;
                }

                p = Math.abs(p);
                double min1 = 3.0 * xm * q - Math.abs(tol * q);
                double min2 = Math.abs(e * q);
                if (2.0 * p < Math.min(min1, min2)) {
                    e = d;
                    d = p / q;
                } else {
                    d = xm;
                    e = d;
                }
            } else {
                d = xm;
                e = d;
            }

            a = b;
            fa = fb;
            if (Math.abs(d) > tol) {
                b += d;
            } else {
                b += Math.copySign(tol, xm);
            }
            fb = f(b);
        }

        logger.error("Brent exceeded the maximum number of iterations.");
        return b;
    }
}
