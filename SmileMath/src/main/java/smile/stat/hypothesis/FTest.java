/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.stat.hypothesis;

import smile.math.special.Beta;
import smile.math.Math;

/**
 * F test of the hypothesis that two independent samples come from normal
 * distributions with the same variance, against the alternative that they
 * come from normal distributions with different variances. Note that the F-test
 * is extremely non-robust to non-normality. That is, even if the data displays
 * only modest departures from the normal distribution, the test is unreliable
 * and should not be used.
 *
 * @author Haifeng Li
 */
public class FTest {
    /**
     * The degree of freedom of f-statistic.
     */
    public int df1, df2;

    /**
     * f-statistic
     */
    public double f;

    /**
     * p-value
     */
    public double pvalue;

    /**
     * Constructor.
     */
    private FTest(double f, int df1, int df2, double pvalue) {
        this.f = f;
        this.df1 = df1;
        this.df2 = df2;
        this.pvalue = pvalue;
    }

    /**
     * Test if the arrays x and y have significantly different variances.
     * Small values of p-value indicate that the two arrays have significantly
     * different variances.
     */
    public static FTest test(double[] x, double[] y) {
        int n1 = x.length;
        int n2 = y.length;

        double var1 = Math.var(x);
        double var2 = Math.var(y);

        int df1, df2;
        double f;

        // Make F the ratio of the larger variance to the smaller one.
        if (var1 > var2) {
            f = var1 / var2;
            df1 = n1 - 1;
            df2 = n2 - 1;
        } else {
            f = var2 / var1;
            df1 = n2 - 1;
            df2 = n1 - 1;
        }

        double p = 2.0 * Beta.regularizedIncompleteBetaFunction(0.5 * df2, 0.5 * df1, df2 / (df2 + df1 * f));
        if (p > 1.0) {
            p = 2.0 - p;
        }

        return new FTest(f, df1, df2, p);
    }
}
