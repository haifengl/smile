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
package smile.stat.hypothesis;

import java.util.Arrays;
import smile.math.MathEx;
import smile.math.special.Beta;
import smile.util.IntSet;

/**
 * F test of the hypothesis that two independent samples come from normal
 * distributions with the same variance, against the alternative that they
 * come from normal distributions with different variances. Note that the F-test
 * is extremely non-robust to non-normality. That is, even if the data displays
 * only modest departures from the normal distribution, the test is unreliable
 * and should not be used.
 *
 * @param f the F-statistic.
 * @param df1 the first degree of freedom of F-statistic.
 * @param df2 the second degree of freedom of F-statistic.
 * @param pvalue the p-value.
 * @author Haifeng Li
 */
public record FTest(double f, int df1, int df2, double pvalue) {
    @Override
    public String toString() {
        return String.format("F-test(f = %.4f, df1 = %d, df2 = %d, p-value = %G)", f, df1, df2, pvalue);
    }

    /**
     * Test if the arrays x and y have significantly different variances.
     * Small values of p-value indicate that the two arrays have significantly
     * different variances.
     *
     * @param x the sample values.
     * @param y the sample values.
     * @return the test results.
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

    /**
     * One-way analysis of variance (ANOVA) between a categorical independent
     * variable (with two or more categories) and a normally distributed
     * interval dependent variable to test for differences in the means of
     * the dependent variable broken down by the levels of the independent
     * variable. Treat the variances in the samples as equal.
     *
     * @param x the categorical independent variable.
     * @param y the normally distributed interval dependent variable.
     * @return the test results.
     */
    public static FTest test(int[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vectors have different size");
        }

        int[] labels = MathEx.unique(x);
        int k = labels.length;

        if (k < 2) {
            throw new IllegalArgumentException("Categorical variable should have two or more levels.");
        }

        Arrays.sort(labels);
        IntSet encoder = new IntSet(labels);
        int[] clazz = Arrays.stream(x).map(encoder::indexOf).toArray();

        double ybar = MathEx.mean(y);
        double[] mu = new double[k];

        int n = x.length;
        int[] ni = new int[k];
        double sst = 0.0;
        for (int i = 0; i < n; i++) {
            mu[clazz[i]] += y[i];
            ni[clazz[i]]++;
            sst += (y[i] - ybar) * (y[i] - ybar);
        }

        double ssb = 0.0;
        for (int i = 0; i < k; i++) {
            mu[i] /= ni[i];
            ssb += ni[i] * (mu[i] - ybar) * (mu[i] - ybar);
        }

        double ssw = sst - ssb;

        int dfb = k - 1;
        int dfw = n - k;
        double f = (ssb / dfb) / (ssw / dfw);

        double p = Beta.regularizedIncompleteBetaFunction(0.5 * dfw, 0.5 * dfb, dfw / (dfw + dfb * f));
        return new FTest(f, dfb, dfw, p);
    }
}
