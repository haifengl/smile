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

package smile.math.special;

import static java.lang.Math.*;
import static smile.math.special.Gamma.lgamma;

/**
 * The beta function, also called the Euler integral of the first kind.
 * <p>
 *     B(x, y) = <i>&#8747;<sub><small>0</small></sub><sup><small>1</small></sup> t<sup>x-1</sup> (1-t)<sup>y-1</sup>dt</i>
 * <p>
 * for {@code x, y > 0} and the integration is over [0, 1].
 * The beta function is symmetric, i.e. <code>B(x, y) = B(y, x)</code>.
 *
 * @author Haifeng Li
 */
public class Beta {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Beta.class);

    /** Utility classes should not have public constructors. */
    private Beta() {

    }

    /**
     * A small number close to the smallest representable floating point number.
     */
    private static final double FPMIN = 1e-300;
    /**
     * A tiny slack to avoid brute exception.
     */
    private static final double EPS = 1E-8;

    /**
     * Beta function, also called the Euler integral of the first kind.
     * The beta function is symmetric, i.e. B(x,y)==B(y,x).
     * @param x {@code x > 0}
     * @param y {@code y > 0}
     * @return the function value.
     */
    public static double beta(double x, double y) {
        return exp(lgamma(x) + lgamma(y) - lgamma(x + y));
    }

    /**
     * Regularized Incomplete Beta function. The regularized incomplete
     * beta function is the cumulative distribution function of the beta
     * distribution, and is related to the cumulative distribution function
     * of a random variable X following a binomial distribution.
     * <p>
     * The implementation employs the continued fraction approximation
     * (see Numerical Recipes).
     *
     * @param x {@code x > 0}
     * @param alpha {@code alpha > 0}
     * @param beta {@code beta > 0}
     * @return the function value.
     */
    public static double regularizedIncompleteBetaFunction(double alpha, double beta, double x) {
        // This function is often used to calculate p-value of model fitting.
        // Due to floating error, the model may provide an x that could be slightly
        // greater than 1 or less than 0. We allow tiny slack here to avoid brute exception.
        if (x < 0.0 && abs(x) < EPS) {
            return 0.0;
        }

        if (x > 1.0 && abs(x - 1.0) < EPS) {
            return 1.0;
        }

        if (x < 0.0 || x > 1.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        if (x == 0.0) return 0.0;
        if (x == 1.0) return 1.0;

        // Term before continued fraction
        double ibeta = exp(lgamma(alpha + beta) - lgamma(alpha) - lgamma(beta) + alpha * log(x) + beta * log(1.0D - x));
        // Continued fraction
        if (x < (alpha + 1.0) / (alpha + beta + 2.0)) {
            ibeta = ibeta * incompleteFractionSummation(alpha, beta, x) / alpha;
        } else {
            // Use symmetry relationship
            ibeta = 1.0 - ibeta * incompleteFractionSummation(beta, alpha, 1.0 - x) / beta;
        }

        return ibeta;
    }

    /**
     * Incomplete fraction summation used in the method regularizedIncompleteBeta
     * using a modified Lentz's method.
     */
    private static double incompleteFractionSummation(double alpha, double beta, double x) {
        final int MAXITER = 500;
        final double EPS = 3.0E-7;

        double aplusb = alpha + beta;
        double aplus1 = alpha + 1.0;
        double aminus1 = alpha - 1.0;
        double c = 1.0;
        double d = 1.0 - aplusb * x / aplus1;
        if (abs(d) < FPMIN) {
            d = FPMIN;
        }
        d = 1.0 / d;
        double h = d;
        int i = 1;
        boolean test = true;
        while (test) {
            int i2 = 2 * i;
            double aa = i * (beta - i) * x / ((aminus1 + i2) * (alpha + i2));
            d = 1.0 + aa * d;
            if (abs(d) < FPMIN) {
                d = FPMIN;
            }
            c = 1.0 + aa / c;
            if (abs(c) < FPMIN) {
                c = FPMIN;
            }
            d = 1.0 / d;
            h *= d * c;
            aa = -(alpha + i) * (aplusb + i) * x / ((alpha + i2) * (aplus1 + i2));
            d = 1.0 + aa * d;
            if (abs(d) < FPMIN) {
                d = FPMIN;
            }
            c = 1.0 + aa / c;
            if (abs(c) < FPMIN) {
                c = FPMIN;
            }
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            i++;
            if (abs(del - 1.0) < EPS) {
                test = false;
            }
            if (i > MAXITER) {
                test = false;
                logger.error("Beta.incompleteFractionSummation: Maximum number of iterations wes exceeded");
            }
        }
        return h;
    }

    /**
     * Inverse of regularized incomplete beta function.
     * @param alpha {@code alpha > 0}
     * @param beta {@code beta > 0}
     * @param p {@code 0 <= p <= 1}
     * @return the function value.
     */
    public static double inverseRegularizedIncompleteBetaFunction(double alpha, double beta, double p) {
        if (p <= 0.0) {
            return 0.0;
        }

        if (p >= 1.0) {
            return 1.0;
        }

        double pp, t, u, err, x, al, h, w;
        double a1 = alpha - 1.;
        double b1 = beta - 1.;

        if (alpha >= 1. && beta >= 1.) {
            pp = (p < 0.5) ? p : 1. - p;
            t = sqrt(-2. * log(pp));
            x = (2.30753 + t * 0.27061) / (1. + t * (0.99229 + t * 0.04481)) - t;
            if (p < 0.5) {
                x = -x;
            }
            al = (x * x - 3.) / 6.;
            h = 2. / (1. / (2. * alpha - 1.) + 1. / (2. * beta - 1.));
            w = (x * sqrt(al + h) / h) - (1. / (2. * beta - 1) - 1. / (2. * alpha - 1.)) * (al + 5. / 6. - 2. / (3. * h));
            x = alpha / (alpha + beta * exp(2. * w));
        } else {
            double lna = log(alpha / (alpha + beta));
            double lnb = log(beta / (alpha + beta));
            t = exp(alpha * lna) / alpha;
            u = exp(beta * lnb) / beta;
            w = t + u;
            if (p < t / w) {
                x = pow(alpha * w * p, 1. / alpha);
            } else {
                x = 1. - pow(beta * w * (1. - p), 1. / beta);
            }
        }

        double afac = -lgamma(alpha) - lgamma(beta) + lgamma(alpha + beta);

        for (int j = 0; j < 10; j++) {
            if (x == 0. || x == 1.) {
                return x;
            }
            err = regularizedIncompleteBetaFunction(alpha, beta, x) - p;
            t = exp(a1 * log(x) + b1 * log(1. - x) + afac);
            u = err / t;
            x -= (t = u / (1. - 0.5 * min(1., u * (a1 / x - b1 / (1. - x)))));
            if (x <= 0.) {
                x = 0.5 * (x + t);
            }
            if (x >= 1.) {
                x = 0.5 * (x + t + 1.);
            }
            if (abs(t) < EPS * x && j > 0) {
                break;
            }
        }
        return x;
    }
}
