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

/**
 * Root finding algorithms.
 *
 * @author Haifeng Li
 */
public class Root {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Root.class);

    /**
     * The accuracy tolerance.
     */
    private double tol = 1E-7;
    /**
     * The maximum number of allowed iterations.
     */
    private int maxIter = 500;

    /**
     * Constructor with tol = 1E-7 and maxIter = 500.
     */
    public Root() {
        this(1E-7, 500);
    }

    /**
     * Constructor.
     * @param tol the accuracy tolerance.
     * @param maxIter the maximum number of allowed iterations.
     */
    public Root(double tol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        this.tol = tol;
        this.maxIter = maxIter;
    }

    /**
     * Brent's method for root-finding. It combines the bisection method,
     * the secant method and inverse quadratic interpolation. It has the
     * reliability of bisection but it can be as quick as some of the
     * less-reliable methods. The algorithm tries to use the potentially
     * fast-converging secant method or inverse quadratic interpolation
     * if possible, but it falls back to the more robust bisection method
     * if necessary.
     *
     * The method is guaranteed to converge as long as the function can be
     * evaluated within the initial interval known to contain a root.
     *
     * @param func the function to be evaluated.
     * @param x1 the left end of search interval.
     * @param x2 the right end of search interval.
     * @return the root.
     */
    public double find(Function func, double x1, double x2) {
        double a = x1, b = x2, c = x2, d = 0, e = 0, fa = func.apply(a), fb = func.apply(b), fc, p, q, r, s, xm;
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
                logger.info(String.format("Brent: the root after %3d iterations: %.5g, error = %.5g", iter, b, xm));
            }

            if (Math.abs(xm) <= tol || fb == 0.0) {
                logger.info(String.format("Brent finds the root after %d iterations: %.5g, error = %.5g", iter, b, xm));
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
                if (2.0 * p < (min1 < min2 ? min1 : min2)) {
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
            fb = func.apply(b);
        }

        logger.error("Brent exceeded the maximum number of iterations.");
        return b;
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
     * @param func the function to be evaluated.
     * @param x1 the left end of search interval.
     * @param x2 the right end of search interval.
     * @return the root.
     */
    public double find(DifferentiableFunction func, double x1, double x2) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        double fl = func.apply(x1);
        double fh = func.apply(x2);
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
        double f = func.apply(rts);
        double df = func.g(rts);
        for (int iter = 1; iter <= maxIter; iter++) {
            if ((((rts - xh) * df - f) * ((rts - xl) * df - f) > 0.0) || (Math.abs(2.0 * f) > Math.abs(dxold * df))) {
                dxold = dx;
                dx = 0.5 * (xh - xl);
                rts = xl + dx;
                if (xl == rts) {
                    logger.info(String.format("Newton-Raphson finds the root after %d iterations: %.5g, error = %.5g", iter, rts, dx));
                    return rts;
                }
            } else {
                dxold = dx;
                dx = f / df;
                double temp = rts;
                rts -= dx;
                if (temp == rts) {
                    logger.info(String.format("Newton-Raphson finds the root after %d iterations: %.5g, error = %.5g", iter, rts, dx));
                    return rts;
                }
            }

            if (iter % 10 == 0) {
                logger.info(String.format("Newton-Raphson: the root after %3d iterations: %.5g, error = %.5g", iter, rts, dx));
            }

            if (Math.abs(dx) < tol) {
                logger.info(String.format("Newton-Raphson finds the root after %d iterations: %.5g, error = %.5g", iter, rts, dx));
                return rts;
            }

            f = func.apply(rts);
            df = func.g(rts);
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
