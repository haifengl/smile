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
package smile.math.special;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;

/**
 * The beta function, also called the Euler integral of the first kind.
 * <p>
 * B(x, y) = <i><big>&#8747;</big><sub><small>0</small></sub><sup><small>1</small></sup> t<sup>x-1</sup> (1-t)<sup>y-1</sup>dt</i>
 * <p>
 * for x, y &gt; 0 and the integration is over [0,1].The beta function is symmetric, i.e. B(x,y) = B(y,x).
 *
 * @author Haifeng Li
 */
public class Beta {
    private static final Logger logger = LoggerFactory.getLogger(Beta.class);

    /** Utility classes should not have public constructors. */
    private Beta() {

    }

    /**
     *  A small number close to the smallest representable floating point number.
     */
    private static final double FPMIN = 1e-300;

    /**
     * Beta function, also called the Euler integral of the first kind.
     * The beta function is symmetric, i.e. B(x,y)==B(y,x).
     */
    public static double beta(double x, double y) {
        return Math.exp(Gamma.lgamma(x) + Gamma.lgamma(y) - Gamma.lgamma(x + y));
    }

    /**
     * Regularized Incomplete Beta function.
     * Continued Fraction approximation (see Numerical recipies for details)
     */
    public static double regularizedIncompleteBetaFunction(double alpha, double beta, double x) {
        if (x < 0.0 || x > 1.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        double ibeta = 0.0;
        if (x == 0.0) {
            ibeta = 0.0;
        } else {
            if (x == 1.0) {
                ibeta = 1.0;
            } else {
                // Term before continued fraction
                ibeta = Math.exp(Gamma.lgamma(alpha + beta) - Gamma.lgamma(alpha) - Gamma.lgamma(beta) + alpha * Math.log(x) + beta * Math.log(1.0D - x));
                // Continued fraction
                if (x < (alpha + 1.0) / (alpha + beta + 2.0)) {
                    ibeta = ibeta * incompleteFractionSummation(alpha, beta, x) / alpha;
                } else {
                    // Use symmetry relationship
                    ibeta = 1.0 - ibeta * incompleteFractionSummation(beta, alpha, 1.0 - x) / beta;
                }
            }
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
        if (Math.abs(d) < FPMIN) {
            d = FPMIN;
        }
        d = 1.0 / d;
        double h = d;
        double aa = 0.0;
        double del = 0.0;
        int i = 1, i2 = 0;
        boolean test = true;
        while (test) {
            i2 = 2 * i;
            aa = i * (beta - i) * x / ((aminus1 + i2) * (alpha + i2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < FPMIN) {
                d = FPMIN;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < FPMIN) {
                c = FPMIN;
            }
            d = 1.0 / d;
            h *= d * c;
            aa = -(alpha + i) * (aplusb + i) * x / ((alpha + i2) * (aplus1 + i2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < FPMIN) {
                d = FPMIN;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < FPMIN) {
                c = FPMIN;
            }
            d = 1.0 / d;
            del = d * c;
            h *= del;
            i++;
            if (Math.abs(del - 1.0) < EPS) {
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
     */
    public static double inverseRegularizedIncompleteBetaFunction(double alpha, double beta, double p) {
        final double EPS = 1.0E-8;

        double pp, t, u, err, x, al, h, w, afac;
        double a1 = alpha - 1.;
        double b1 = beta - 1.;

        if (p <= 0.0) {
            return 0.0;
        }

        if (p >= 1.0) {
            return 1.0;
        }

        if (alpha >= 1. && beta >= 1.) {
            pp = (p < 0.5) ? p : 1. - p;
            t = Math.sqrt(-2. * Math.log(pp));
            x = (2.30753 + t * 0.27061) / (1. + t * (0.99229 + t * 0.04481)) - t;
            if (p < 0.5) {
                x = -x;
            }
            al = (x * x - 3.) / 6.;
            h = 2. / (1. / (2. * alpha - 1.) + 1. / (2. * beta - 1.));
            w = (x * Math.sqrt(al + h) / h) - (1. / (2. * beta - 1) - 1. / (2. * alpha - 1.)) * (al + 5. / 6. - 2. / (3. * h));
            x = alpha / (alpha + beta * Math.exp(2. * w));
        } else {
            double lna = Math.log(alpha / (alpha + beta));
            double lnb = Math.log(beta / (alpha + beta));
            t = Math.exp(alpha * lna) / alpha;
            u = Math.exp(beta * lnb) / beta;
            w = t + u;
            if (p < t / w) {
                x = Math.pow(alpha * w * p, 1. / alpha);
            } else {
                x = 1. - Math.pow(beta * w * (1. - p), 1. / beta);
            }
        }
        afac = -Gamma.lgamma(alpha) - Gamma.lgamma(beta) + Gamma.lgamma(alpha + beta);
        for (int j = 0; j < 10; j++) {
            if (x == 0. || x == 1.) {
                return x;
            }
            err = regularizedIncompleteBetaFunction(alpha, beta, x) - p;
            t = Math.exp(a1 * Math.log(x) + b1 * Math.log(1. - x) + afac);
            u = err / t;
            x -= (t = u / (1. - 0.5 * Math.min(1., u * (a1 / x - b1 / (1. - x)))));
            if (x <= 0.) {
                x = 0.5 * (x + t);
            }
            if (x >= 1.) {
                x = 0.5 * (x + t + 1.);
            }
            if (Math.abs(t) < EPS * x && j > 0) {
                break;
            }
        }
        return x;
    }
}
