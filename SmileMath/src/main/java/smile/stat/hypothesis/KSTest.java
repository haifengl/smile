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

package smile.stat.hypothesis;

import java.util.Arrays;
import smile.stat.distribution.Distribution;
import smile.math.Math;

/**
 * The Kolmogorov-Smirnov test (K-S test) is a form of minimum distance
 * estimation used as a non-parametric test of equality of one-dimensional
 * probability distributions. K-S test is used to compare a sample with a reference
 * probability distribution (one-sample K-S test), or to compare two samples
 * (two-sample K-S test). The Kolmogorov-Smirnov statistic quantifies a
 * distance between the empirical distribution function of the sample and the
 * cumulative distribution function of the reference distribution, or between
 * the empirical distribution functions of two samples. The null distribution
 * of this statistic is calculated under the null hypothesis that the samples
 * are drawn from the same distribution (in the two-sample case) or that the
 * sample is drawn from the reference distribution (in the one-sample case).
 * In each case, the distributions considered under the null hypothesis are
 * continuous distributions but are otherwise unrestricted.
 * <p>
 * The two-sample KS test is one of the most useful and general non-parametric
 * methods for comparing two samples, as it is sensitive to differences in
 * both location and shape of the empirical cumulative distribution functions
 * of the two samples.
 * <p>
 * The Kolmogorov-Smirnov test can be modified to serve goodness of fit test.
 * In the special case of testing for normality of the distribution, samples
 * are standardized and compared with a standard normal distribution. This is
 * equivalent to setting the mean and variance of the reference distribution
 * equal to the sample estimates, and it is known that using the sample to
 * modify the null hypothesis reduces the power of a test. Correcting for this
 * bias leads to the Lilliefors test. However, even Lilliefors' modification
 * is less powerful than the Shapiro-Wilk test or Anderson-Darling test for
 * testing normality.
 *
 * @author Haifeng Li
 */
public class KSTest {
    /**
     * Kolmogorov-Smirnov statistic
     */
    public double d;

    /**
     * P-value
     */
    public double pvalue;

    private KSTest(double d, double pvalue) {
        this.d = d;
        this.pvalue = pvalue;
    }

    /**
     * Cumulative distribution function of Kolmogorov-Smirnov distribution.
     */
    private static double pks(double z) {
        if (z < 0.0) {
            throw new IllegalArgumentException("Invalid z: " + z);
        }

        if (z == 0.0) {
            return 0.0;
        }

        if (z < 1.18) {
            double y = Math.exp(-1.23370055013616983 / (z * z));
            return 2.25675833419102515 * Math.sqrt(-Math.log(y)) * (y + Math.pow(y, 9) + Math.pow(y, 25) + Math.pow(y, 49));
        } else {
            double x = Math.exp(-2. * (z * z));
            return 1. - 2. * (x - Math.pow(x, 4) + Math.pow(x, 9));
        }
    }

    /**
     * Complementary cumulative distribution function of Kolmogorov-Smirnov
     * distribution.
     */
    private static double qks(double z) {
        if (z < 0.0) {
            throw new IllegalArgumentException("Invalid z: " + z);
        }

        if (z == 0.0) {
            return 1.0;
        }

        if (z < 1.18) {
            return 1.0 - pks(z);
        }

        double x = Math.exp(-2. * (z * z));
        return 2. * (x - Math.pow(x, 4) + Math.pow(x, 9));
    }

    /**
     * Inverse of the complementary cumulative distribution function of
     * Kolmogorov-Smirnov distribution.
     */
    private static double invqks(double q) {
        if (q <= 0.0 || q > 1.0) {
            throw new IllegalArgumentException("Invalid q: " + q);
        }

        if (q == 1.0) {
            return 0.0;
        }

        if (q > 0.3) {
            double f = -0.392699081698724155 * Math.sqr(1. - q);
            double y = invxlogx(f);
            double t;
            do {
                double logy = Math.log(y);
                double ff = f / Math.sqr(1. + Math.pow(y, 4) + Math.pow(y, 12));
                double u = (y * logy - ff) / (1. + logy);
                t = u / Math.max(0.5, 1. - 0.5 * u / (y * (1. + logy)));
                y -= t;
            } while (Math.abs(t / y) > 1.e-15);
            return 1.57079632679489662 / Math.sqrt(-Math.log(y));
        } else {
            double x = 0.03;
            double xp;
            do {
                xp = x;
                x = 0.5 * q + Math.pow(x, 4) - Math.pow(x, 9);
                if (x > 0.06) {
                    x += Math.pow(x, 16) - Math.pow(x, 25);
                }
            } while (Math.abs((xp - x) / x) > 1.e-15);
            return Math.sqrt(-0.5 * Math.log(x));
        }
    }

    /**
     * Inverse of the cumulative distribution function of Kolmogorov-Smirnov
     * distribution.
     */
    static double invpks(double p) {
        return invqks(1.0 - p);
    }

    /**
     * Inverse function to y log y. Used for initial guess of invqks.
     */
    private static double invxlogx(double y) {
        final double ooe = 0.367879441171442322;

        double t, u, to = 0.;
        if (y >= 0. || y <= -ooe) {
            throw new IllegalArgumentException("Invalid y: " + y);
        }
        if (y < -0.2) {
            u = Math.log(ooe - Math.sqrt(2 * ooe * (y + ooe)));
        } else {
            u = -10.;
        }
        do {
            u += (t = (Math.log(y / u) - u) * (u / (1. + u)));
            if (t < 1.e-8 && Math.abs(t + to) < 0.01 * Math.abs(t)) {
                break;
            }
            to = t;
        } while (Math.abs(t / u) > 1.e-15);
        return Math.exp(u);
    }

    /**
     * The one-sample KS test for the null hypothesis that the data set x
     * is drawn from the given distribution. Small values of p-value show that
     * the cumulative distribution function of x is significantly different from
     * the given distribution. The array x is modified by being sorted into
     * ascending order.
     */
    public static KSTest test(double[] x, Distribution dist) {
        Arrays.sort(x);

        int n = x.length;

        double en = n;
        double d = 0.0;
        double fo = 0.0;
        
        for (int j = 0; j < n; j++) {
            double fn = (j + 1) / en;
            double ff = dist.cdf(x[j]);
            double dt = Math.max(Math.abs(fo - ff), Math.abs(fn - ff));
            if (dt > d) {
                d = dt;
            }
            fo = fn;
        }

        en = Math.sqrt(en);
        double p = qks((en + 0.12 + 0.11 / en) * d);

        return new KSTest(d, p);
    }

    /**
     * The two-sample KS test for the null hypothesis that the data sets
     * are drawn from the same distribution. Small values of p-value show that
     * the cumulative distribution function of x is significantly different from
     * that of y. The arrays x and y are modified by being sorted into
     * ascending order.
     */
    public static KSTest test(double[] x, double[] y) {
        Arrays.sort(x);
        Arrays.sort(y);

        int j1 = 0, j2 = 0;
        int n1 = x.length, n2 = y.length;

        double en1 = n1;
        double en2 = n2;
        double d = 0.0;

        double d1, d2, dt, fn1 = 0.0, fn2 = 0.0;
        while (j1 < n1 && j2 < n2) {
            if ((d1 = x[j1]) <= (d2 = y[j2])) {
                do {
                    fn1 = ++j1 / en1;
                } while (j1 < n1 && d1 == x[j1]);
            }
            if (d2 <= d1) {
                do {
                    fn2 = ++j2 / en2;
                } while (j2 < n2 && d2 == y[j2]);
            }
            if ((dt = Math.abs(fn2 - fn1)) > d) {
                d = dt;
            }
        }

        double en = Math.sqrt(en1 * en2 / (en1 + en2));
        double p = qks((en + 0.12 + 0.11 / en) * d);

        return new KSTest(d, p);
    }
}
