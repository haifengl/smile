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
package smile.math;

/**
 * Root finding algorithms.
 *
 * @author Haifeng Li
 */
public class Root {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Root.class);

    /** The instance with default settings. */
    private static Root instance = new Root();

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

    }

    /** Returns the instance with default settings. */
    public static Root getInstance() {
        return instance;
    }

    /**
     * Sets the accuracy tolerance.
     * @return return this object.
     */
    public Root setTolerance(double tol) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        this.tol = tol;
        return this;
    }

    /**
     * Sets the maximum number of allowed iterations.
     * @return return this object.
     */
    public Root setMaxIter(int maxIter) {
        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        this.maxIter = maxIter;
        return this;
    }

    /**
     * Returns the root of a function known to lie between x1 and x2 by
     * Brent's method. The root will be refined until its accuracy is tol.
     * The method is guaranteed to converge as long as the function can be
     * evaluated within the initial interval known to contain a root.
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
                logger.info(String.format("Brent finds the root after %3d iterations: %.5g, error = %.5g", iter, b, xm));
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

        logger.error("Brent's method exceeded the maximum number of iterations.");
        return b;
    }

    /**
     * Returns the root of a function whose derivative is available known
     * to lie between x1 and x2 by Newton-Raphson method. The root will be
     * refined until its accuracy is within xacc.
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
                    logger.info(String.format("Newton-Raphson finds the root after %3d iterations: %.5g, error = %.5g", iter, rts, dx));
                    return rts;
                }
            } else {
                dxold = dx;
                dx = f / df;
                double temp = rts;
                rts -= dx;
                if (temp == rts) {
                    logger.info(String.format("Newton-Raphson finds the root after %3d iterations: %.5g, error = %.5g", iter, rts, dx));
                    return rts;
                }
            }

            if (iter % 10 == 0) {
                logger.info(String.format("Newton-Raphson: the root after %3d iterations: %.5g, error = %.5g", iter, rts, dx));
            }

            if (Math.abs(dx) < tol) {
                logger.info(String.format("Newton-Raphson finds the root after %3d iterations: %.5g, error = %.5g", iter, rts, dx));
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

        logger.error("Newton-Raphson method exceeded the maximum number of iterations.");
        return rts;
    }
}
