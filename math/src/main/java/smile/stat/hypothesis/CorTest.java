/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.stat.hypothesis;

import smile.math.MathEx;
import smile.math.special.Beta;
import smile.math.special.Erf;
import smile.sort.QuickSort;

/**
 * Correlation test. Correlation of two variables is a measure of the degree
 * to which they vary together. More accurately, correlation is the covariation
 * of standardized variables. In positive correlation, as one variable
 * increases, so also does the other. In negative correlation, as one variable
 * increases, the other variable decreases. Perfect positive correlation usually
 * is calculated as a value of 1 (or 100%). Perfect negative correlation usually
 * is calculated as a value of -1. A values of zero shows no correlation at all.
 * <p>
 * Three common types of correlation are Pearson, Spearman (for ranked data)
 * and Kendall (for uneven or multiple rankings).
 * <p>
 * To deal with measures of association between nominal variables, we can use Chi-square
 * test for independence. For any pair of nominal variables, the data can be
 * displayed as a contingency table, whose rows are labels by the values of one
 * nominal variable, whose columns are labels by the values of the other nominal
 * variable, and whose entries are nonnegative integers giving the number of
 * observed events for each combination of row and column.
 * 
 * @author Haifeng Li
 */
public class CorTest {
    /**
     * The type of test.
     */
    public final String method;

    /**
     * The correlation coefficient.
     */
    public final double cor;

    /**
     * The test statistic.
     */
    public final double t;

    /**
     * The degree of freedom of test statistic.
     * It is set to 0 in case of Kendall test as the test is non-parametric.
     */
    public final double df;

    /**
     * Two-sided p-value.
     */
    public final double pvalue;

    /**
     * Constructor.
     * @param method the type of correlation.
     * @param cor the correlation coefficient.
     * @param t the t-statistic.
     * @param df the degree of freedom.
     * @param pvalue the p-value.
     */
    public CorTest(String method, double cor, double t, double df, double pvalue) {
        this.method = method;
        this.cor = cor;
        this.t = t;
        this.df = df;
        this.pvalue = pvalue;
    }

    @Override
    public String toString() {
        return String.format("%s Correlation Test(cor = %.2f, t = %.4f, df = %.3f, p-value = %G)", method, cor, t, df, pvalue);
    }

    /**
     * Pearson correlation coefficient test.
     *
     * @param x the sample values.
     * @param y the sample values.
     * @return the test results.
     */
    public static CorTest pearson(double[] x, double[] y) {
        final double TINY = 1.0e-16;

        int n = x.length;
        double syy = 0.0, sxy = 0.0, sxx = 0.0, ay = 0.0, ax = 0.0;

        for (int j = 0; j < n; j++) {
            ax += x[j];
            ay += y[j];
        }

        ax /= n;
        ay /= n;

        for (int j = 0; j < n; j++) {
            double xt = x[j] - ax;
            double yt = y[j] - ay;
            sxx += xt * xt;
            syy += yt * yt;
            sxy += xt * yt;
        }

        double r = sxy / (Math.sqrt(sxx * syy) + TINY);
        int df = n - 2;
        double t = r * Math.sqrt(df / ((1.0 - r + TINY) * (1.0 + r + TINY)));
        double pvalue = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));

        return new CorTest("Pearson", r, t, df, pvalue);
    }

    /**
     * Given a sorted array, replaces the elements by their rank, including
     * midranking of ties, and returns the sum of f<sup>3</sup>-f, where
     * f is the number of elements in each tie.
     */
    private static double crank(double[] w) {
        int n = w.length;
        double s = 0.0;
        int j = 1;
        while (j < n) {
            if (w[j] != w[j - 1]) {
                w[j - 1] = j;
                ++j;
            } else {
                int jt = j + 1;
                while (jt <= n && w[jt - 1] == w[j - 1]) {
                    jt++;
                }

                double rank = 0.5 * (j + jt - 1);
                for (int ji = j; ji <= (jt - 1); ji++) {
                    w[ji - 1] = rank;
                }

                double t = jt - j;
                s += (t * t * t - t);
                j = jt;
            }
        }

        if (j == n) {
            w[n - 1] = n;
        }

        return s;
    }

    /**
     * Spearman rank correlation coefficient test. The Spearman Rank Correlation
     * Coefficient is a form of the Pearson coefficient with the data converted
     * to rankings (ie. when variables are ordinal). It can be used when there
     * is non-parametric data and hence Pearson cannot be used.
     * <p>
     * The raw scores are converted to ranks and the differences between
     * the ranks of each observation on the two variables are calculated.
     * <p>
     * The p-value is calculated by approximation, which is good for {@code n > 10}.
     *
     * @param x the sample values.
     * @param y the sample values.
     * @return the test results.
     */
    public static CorTest spearman(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vectors have different size");
        }

        int n = x.length;
        double[] wksp1 = new double[n];
        double[] wksp2 = new double[n];
        for (int j = 0; j < n; j++) {
            wksp1[j] = x[j];
            wksp2[j] = y[j];
        }

        QuickSort.sort(wksp1, wksp2);
        double sf = crank(wksp1);
        QuickSort.sort(wksp2, wksp1);
        double sg = crank(wksp2);

        double d = 0.0;
        for (int j = 0; j < n; j++) {
            d += MathEx.pow2(wksp1[j] - wksp2[j]);
        }

        double en3n = n * n * n - n;
        double fac = (1.0 - sf / en3n) * (1.0 - sg / en3n);
        double rs = (1.0 - (6.0 / en3n) * (d + (sf + sg) / 12.0)) / Math.sqrt(fac);
        fac = (rs + 1.0) * (1.0 - rs);

        double pvalue = 0.0;
        double t = rs * Math.sqrt((n - 2.0) / fac);
        int df = n - 2;
        if (fac > 0.0) {
            pvalue = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
        }

        return new CorTest("Spearman", rs, t, df, pvalue);
    }

    /**
     * Kendall rank correlation test. The Kendall Tau Rank Correlation
     * Coefficient is used to measure the degree of correspondence
     * between sets of rankings where the measures are not equidistant.
     * It is used with non-parametric data. The p-value is calculated by
     * approximation, which is good for {@code n > 10}.
     *
     * @param x the sample values.
     * @param y the sample values.
     * @return the test results.
     */
    public static CorTest kendall(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vectors have different size");
        }

        int is = 0, n2 = 0, n1 = 0, n = x.length;
        double aa, a2, a1;
        for (int j = 0; j < n - 1; j++) {
            for (int k = j + 1; k < n; k++) {
                a1 = x[j] - x[k];
                a2 = y[j] - y[k];
                aa = a1 * a2;
                if (aa != 0.0) {
                    ++n1;
                    ++n2;
                    if (aa > 0)
                        ++is;
                    else
                        --is;

                } else {
                    if (a1 != 0.0) ++n1;
                    if (a2 != 0.0) ++n2;
                }
            }
        }

        double tau = is / (Math.sqrt(n1) * Math.sqrt(n2));

        // Kendall test is non-parametric as it does not rely on any
        // assumptions on the distributions of X or Y or the distribution
        // of (X,Y).

        // Under the null hypothesis of independence of X and Y, the sampling
        // distribution of tau has an expected value of zero. The precise
        // distribution cannot be characterized in terms of common distributions,
        // but may be calculated exactly for small samples. For larger samples,
        // it is common to use an approximation to the normal distribution,
        // with mean zero and variance sqrt(2(2n+5)/9n(n-1)).
        double var = (4.0 * n + 10.0) / (9.0 * n * (n - 1.0));
        double z = tau / Math.sqrt(var);
        double pvalue = Erf.erfcc(Math.abs(z) / 1.4142136);

        // Set the degree of freedom to 0 as the test is non-parametric.
        return new CorTest("Kendall", tau, z, 0, pvalue);
    }
}
