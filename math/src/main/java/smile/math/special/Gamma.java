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

package smile.math.special;

import static java.lang.Math.*;
import static smile.math.MathEx.factorial;
import static smile.math.MathEx.lfactorial;

/**
 * The gamma, digamma, and incomplete gamma functions.
 * 
 * @author Haifeng Li
 */
public class Gamma {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Gamma.class);

    /** Utility classes should not have public constructors. */
    private Gamma() {

    }

    /**
     *  A small number close to the smallest representable floating point number.
     */
    private static final double FPMIN = 1e-300;
    /**
     * Lanczos Gamma Function approximation - N (number of coefficients - 1)
     */
    private static final int LANCZOS_N = 6;
    /**
     * Lanczos Gamma Function approximation - Coefficients
     */
    private static final double[] LANCZOS_COEFF = {1.000000000190015, 76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179E-2, -0.5395239384953E-5};
    /**
     * Lanczos Gamma Function approximation - small gamma
     */
    private static final double LANCZOS_SMALL_GAMMA = 5.0;
    /**
     * Maximum number of iterations allowed in Incomplete Gamma Function calculations
     */
    private static final int INCOMPLETE_GAMMA_MAX_ITERATIONS = 1000;
    /**
     * Tolerance used in terminating series in Incomplete Gamma Function calculations
     */
    private static final double INCOMPLETE_GAMMA_EPSILON = 1.0E-8;

    /**
     * Gamma function. Lanczos approximation (6 terms).
     */
    public static double gamma(double x) {
        double xcopy = x;
        double first = x + LANCZOS_SMALL_GAMMA + 0.5;
        double second = LANCZOS_COEFF[0];
        double fg = 0.0;

        if (x >= 0.0) {
            if (x >= 1.0 && x - (int) x == 0.0) {
                fg = factorial((int) x - 1);
            } else {
                first = pow(first, x + 0.5) * exp(-first);
                for (int i = 1; i <= LANCZOS_N; i++) {
                    second += LANCZOS_COEFF[i] / ++xcopy;
                }
                fg = first * sqrt(2.0 * PI) * second / x;
            }
        } else {
            fg = -PI / (x * gamma(-x) * sin(PI * x));
        }
        return fg;
    }

    /**
     * The log of the Gamma function. Lanczos approximation (6 terms)
     */
    public static double lgamma(double x) {
        double xcopy = x;
        double fg = 0.0;
        double first = x + LANCZOS_SMALL_GAMMA + 0.5;
        double second = LANCZOS_COEFF[0];

        if (x >= 0.0) {
            if (x >= 1.0 && x - (int) x == 0.0) {
                fg = lfactorial((int) x - 1);
            } else {
                first -= (x + 0.5) * log(first);
                for (int i = 1; i <= LANCZOS_N; i++) {
                    second += LANCZOS_COEFF[i] / ++xcopy;
                }
                fg = log(sqrt(2.0 * PI) * second / x) - first;
            }
        } else {
            fg = PI / (gamma(1.0 - x) * sin(PI * x));

            if (fg != 1.0 / 0.0 && fg != -1.0 / 0.0) {
                if (fg < 0) {
                    throw new IllegalArgumentException("The gamma function is negative: " + fg);
                } else {
                    fg = log(fg);
                }
            }
        }
        return fg;
    }

    /**
     * Regularized Incomplete Gamma Function
     * P(s,x) = <i><big>&#8747;</big><sub><small>0</small></sub><sup><small>x</small></sup> e<sup>-t</sup> t<sup>(s-1)</sup> dt</i>
     */
    public static double regularizedIncompleteGamma(double s, double x) {
        if (s < 0.0) {
            throw new IllegalArgumentException("Invalid s: " + s);
        }

        if (x < 0.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        double igf = 0.0;

        if (x < s + 1.0) {
            // Series representation
            igf = regularizedIncompleteGammaSeries(s, x);
        } else {
            // Continued fraction representation
            igf = regularizedIncompleteGammaFraction(s, x);
        }

        return igf;
    }

    /**
     * Regularized Upper/Complementary Incomplete Gamma Function
     * Q(s,x) = 1 - P(s,x) = 1 - <i><big>&#8747;</big><sub><small>0</small></sub><sup><small>x</small></sup> e<sup>-t</sup> t<sup>(s-1)</sup> dt</i>
     */
    public static double regularizedUpperIncompleteGamma(double s, double x) {
        if (s < 0.0) {
            throw new IllegalArgumentException("Invalid s: " + s);
        }

        if (x < 0.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        double igf = 0.0;

        if (x != 0.0) {
            if (x == 1.0 / 0.0) {
                igf = 1.0;
            } else {
                if (x < s + 1.0) {
                    // Series representation
                    igf = 1.0 - regularizedIncompleteGammaSeries(s, x);
                } else {
                    // Continued fraction representation
                    igf = 1.0 - regularizedIncompleteGammaFraction(s, x);
                }
            }
        }
        return igf;
    }

    /**
     * Regularized Incomplete Gamma Function P(a,x) = <i><big>&#8747;</big><sub><small>0</small></sub><sup><small>x</small></sup> e<sup>-t</sup> t<sup>(a-1)</sup> dt</i>.
     * Series representation of the function - valid for x < a + 1
     */
    private static double regularizedIncompleteGammaSeries(double a, double x) {
        if (a < 0.0 || x < 0.0 || x >= a + 1) {
            throw new IllegalArgumentException(String.format("Invalid a = %f, x = %f", a, x));
        }

        int i = 0;
        double igf = 0.0;
        boolean check = true;

        double acopy = a;
        double sum = 1.0 / a;
        double incr = sum;
        double loggamma = lgamma(a);

        while (check) {
            ++i;
            ++a;
            incr *= x / a;
            sum += incr;
            if (abs(incr) < abs(sum) * INCOMPLETE_GAMMA_EPSILON) {
                igf = sum * exp(-x + acopy * log(x) - loggamma);
                check = false;
            }
            if (i >= INCOMPLETE_GAMMA_MAX_ITERATIONS) {
                check = false;
                igf = sum * exp(-x + acopy * log(x) - loggamma);
                logger.error("Gamma.regularizedIncompleteGammaSeries: Maximum number of iterations wes exceeded");
            }
        }
        return igf;
    }

    /**
     * Regularized Incomplete Gamma Function P(a,x) = <i><big>&#8747;</big><sub><small>0</small></sub><sup><small>x</small></sup> e<sup>-t</sup> t<sup>(a-1)</sup> dt</i>.
     * Continued Fraction representation of the function - valid for x &ge; a + 1
     * This method follows the general procedure used in Numerical Recipes.
     */
    private static double regularizedIncompleteGammaFraction(double a, double x) {
        if (a < 0.0 || x < 0.0 || x < a + 1) {
            throw new IllegalArgumentException(String.format("Invalid a = %f, x = %f", a, x));
        }

        int i = 0;
        double ii = 0.0;
        double igf = 0.0;
        boolean check = true;

        double loggamma = lgamma(a);
        double numer = 0.0;
        double incr = 0.0;
        double denom = x - a + 1.0;
        double first = 1.0 / denom;
        double term = 1.0 / FPMIN;
        double prod = first;

        while (check) {
            ++i;
            ii = (double) i;
            numer = -ii * (ii - a);
            denom += 2.0D;
            first = numer * first + denom;
            if (abs(first) < FPMIN) {
                first = FPMIN;
            }
            term = denom + numer / term;
            if (abs(term) < FPMIN) {
                term = FPMIN;
            }
            first = 1.0D / first;
            incr = first * term;
            prod *= incr;
            if (abs(incr - 1.0D) < INCOMPLETE_GAMMA_EPSILON) {
                check = false;
            }
            if (i >= INCOMPLETE_GAMMA_MAX_ITERATIONS) {
                check = false;
                logger.error("Gamma.regularizedIncompleteGammaFraction: Maximum number of iterations wes exceeded");
            }
        }
        igf = 1.0 - exp(-x + a * log(x) - loggamma) * prod;
        return igf;
    }

    /**
     * The digamma function is defined as the logarithmic derivative of the gamma function.
     */
    public static double digamma(double x) {
        final double C7[][] = {
            {
                1.3524999667726346383e4, 4.5285601699547289655e4,
                4.5135168469736662555e4, 1.8529011818582610168e4,
                3.3291525149406935532e3, 2.4068032474357201831e2,
                5.1577892000139084710, 6.2283506918984745826e-3
            },
            {
                6.9389111753763444376e-7, 1.9768574263046736421e4,
                4.1255160835353832333e4, 2.9390287119932681918e4,
                9.0819666074855170271e3, 1.2447477785670856039e3,
                6.7429129516378593773e1, 1.0
            }
        };

        final double C4[][] = {
            {
                -2.728175751315296783e-15, -6.481571237661965099e-1,
                -4.486165439180193579, -7.016772277667586642,
                -2.129404451310105168
            },
            {
                7.777885485229616042, 5.461177381032150702e1,
                8.929207004818613702e1, 3.227034937911433614e1,
                1.0
            }
        };

        double prodPj = 0.0;
        double prodQj = 0.0;
        double digX = 0.0;

        if (x >= 3.0) {
            double x2 = 1.0 / (x * x);
            for (int j = 4; j >= 0; j--) {
                prodPj = prodPj * x2 + C4[0][j];
                prodQj = prodQj * x2 + C4[1][j];
            }
            digX = log(x) - (0.5 / x) + (prodPj / prodQj);

        } else if (x >= 0.5) {
            final double X0 = 1.46163214496836234126;
            for (int j = 7; j >= 0; j--) {
                prodPj = x * prodPj + C7[0][j];
                prodQj = x * prodQj + C7[1][j];
            }
            digX = (x - X0) * (prodPj / prodQj);

        } else {
            double f = (1.0 - x) - floor(1.0 - x);
            digX = digamma(1.0 - x) + PI / tan(PI * f);
        }

        return digX;
    }

    /**
     * The inverse of regularized incomplete gamma function.
     */
    public static double inverseRegularizedIncompleteGamma(double a, double p) {
        if (a <= 0.0) {
            throw new IllegalArgumentException("a must be pos in invgammap");
        }

        final double EPS = 1.0E-8;

        double x, err, t, u, pp;
        double lna1 = 0.0;
        double afac = 0.0;
        double a1 = a - 1;
        double gln = lgamma(a);
        if (p >= 1.) {
            return max(100., a + 100. * sqrt(a));
        }

        if (p <= 0.0) {
            return 0.0;
        }

        if (a > 1.0) {
            lna1 = log(a1);
            afac = exp(a1 * (lna1 - 1.) - gln);
            pp = (p < 0.5) ? p : 1. - p;
            t = sqrt(-2. * log(pp));
            x = (2.30753 + t * 0.27061) / (1. + t * (0.99229 + t * 0.04481)) - t;
            if (p < 0.5) {
                x = -x;
            }
            x = max(1.e-3, a * pow(1. - 1. / (9. * a) - x / (3. * sqrt(a)), 3));
        } else {
            t = 1.0 - a * (0.253 + a * 0.12);
            if (p < t) {
                x = pow(p / t, 1. / a);
            } else {
                x = 1. - log(1. - (p - t) / (1. - t));
            }
        }
        for (int j = 0; j < 12; j++) {
            if (x <= 0.0) {
                return 0.0;
            }
            err = regularizedIncompleteGamma(a, x) - p;
            if (a > 1.) {
                t = afac * exp(-(x - a1) + a1 * (log(x) - lna1));
            } else {
                t = exp(-x + a1 * log(x) - gln);
            }
            u = err / t;
            x -= (t = u / (1. - 0.5 * min(1., u * ((a - 1.) / x - 1))));
            if (x <= 0.) {
                x = 0.5 * (x + t);
            }
            if (abs(t) < EPS * x) {
                break;
            }
        }
        return x;
    }
}
