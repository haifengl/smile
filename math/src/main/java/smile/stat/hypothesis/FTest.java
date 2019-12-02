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

package smile.stat.hypothesis;

import smile.math.MathEx;
import smile.math.special.Beta;

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
    public final int df1, df2;

    /**
     * f-statistic
     */
    public final double f;

    /**
     * p-value
     */
    public final double pvalue;

    /**
     * Constructor.
     */
    private FTest(double f, int df1, int df2, double pvalue) {
        this.f = f;
        this.df1 = df1;
        this.df2 = df2;
        this.pvalue = pvalue;
    }

    @Override
    public String toString() {
        return String.format("F-test(f = %.4f, df1 = %d, df2 = %d, p-value = %G)", f, df1, df2, pvalue);
    }

    /**
     * Test if the arrays x and y have significantly different variances.
     * Small values of p-value indicate that the two arrays have significantly
     * different variances.
     */
    public static FTest test(double[] x, double[] y) {
        int n1 = x.length;
        int n2 = y.length;

        double var1 = MathEx.var(x);
        double var2 = MathEx.var(y);

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
