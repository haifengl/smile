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
 * Student's t test. A t-test is any statistical hypothesis test in which the test statistic has
 * a Student's t distribution if the null hypothesis is true. It is applied
 * when the population is assumed to be normally distributed but the sample
 * sizes are small enough that the statistic on which inference is based is
 * not normally distributed because it relies on an uncertain estimate of
 * standard deviation rather than on a precisely known value.
 * <p>
 * Among the most frequently used t tests are:
 * <ul>
 * <li> A test of whether the mean of a normally distributed population has
 * a value specified in a null hypothesis.
 * <li> A test of the null hypothesis that the means of two normally
 * distributed populations are equal. Given two data sets, each characterized
 * by its mean, standard deviation and number of data points, we can use some
 * kind of t test to determine whether the means are distinct, provided that
 * the underlying distributions can be assumed to be normal. All such tests
 * are usually called Student's t tests, though strictly speaking that name
 * should only be used if the variances of the two populations are also assumed
 * to be equal; the form of the test used when this assumption is dropped is
 * sometimes called Welch's t test. There are different versions of the t test
 * depending on whether the two samples are
 * <ul>
 * <li> unpaired, independent of each other (e.g., individuals randomly
 * assigned into two groups, measured after an intervention and compared
 * with the other group),
 * <li>or paired, so that each member of one sample has a unique relationship
 * with a particular member of the other sample (e.g., the same people measured
 * before and after an intervention).
 * </ul>
 * If the calculated p-value is below the threshold chosen for
 * statistical significance (usually 0.05 or 0.01 level), then
 * the null hypothesis which usually states that the two groups do not differ
 * is rejected in favor of an alternative hypothesis, which typically states
 * that the groups do differ.
 * <li> A test of whether the slope of a regression line differs significantly
 * from 0.
 * </ul>
 *
 * @author Haifeng Li
 */
public class TTest {
    /**
     * A character string indicating what type of test was performed.
     */
    public final String method;

    /**
     * The degree of freedom of t-statistic.
     */
    public final double df;

    /**
     * t-statistic
     */
    public final double t;

    /**
     * p-value
     */
    public final double pvalue;

    /**
     * Constructor.
     */
    private TTest(String method, double t, double df, double pvalue) {
        this.method = method;
        this.t = t;
        this.df = df;
        this.pvalue = pvalue;
    }

    @Override
    public String toString() {
        return String.format("%s t-test(t = %.4f, df = %.3f, p-value = %G)", method, t, df, pvalue);
    }

    /**
     * Independent one-sample t-test whether the mean of a normally distributed
     * population has a value specified in a null hypothesis. Small values of
     * p-value indicate that the array has significantly different mean.
     */
    public static TTest test(double[] x, double mean) {
        int n = x.length;

        double mu = MathEx.mean(x);
        double var = MathEx.var(x);

        int df = n - 1;

        double t = (mu - mean) / Math.sqrt(var/n);
        double p = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));

        return new TTest("One Sample", t, df, p);
    }

    /**
     * Test if the arrays x and y have significantly different means. The data
     * arrays are assumed to be drawn from populations with unequal variances.
     * Small values of p-value indicate that the two arrays have significantly
     * different means.
     */
    public static TTest test(double[] x, double[] y) {
        return test(x, y, false);
    }

    /**
     * Test if the arrays x and y have significantly different means.  Small
     * values of p-value indicate that the two arrays have significantly
     * different means.
     * @param equalVariance true if the data arrays are assumed to be
     * drawn from populations with the same true variance. Otherwise, The data
     * arrays are allowed to be drawn from populations with unequal variances.
     */
    public static TTest test(double[] x, double[] y, boolean equalVariance) {
        if (equalVariance) {
            int n1 = x.length;
            int n2 = y.length;

            double mu1 = MathEx.mean(x);
            double var1 = MathEx.var(x);

            double mu2 = MathEx.mean(y);
            double var2 = MathEx.var(y);

            int df = n1 + n2 - 2;

            double svar = ((n1 - 1) * var1 + (n2 - 1) * var2) / df;

            double t = (mu1 - mu2) / Math.sqrt(svar * (1.0 / n1 + 1.0 / n2));
            double p = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));

            return new TTest("Equal Variance Two Sample", t, df, p);
        } else {
            int n1 = x.length;
            int n2 = y.length;

            double mu1 = MathEx.mean(x);
            double var1 = MathEx.var(x);

            double mu2 = MathEx.mean(y);
            double var2 = MathEx.var(y);

            double df = MathEx.sqr(var1 / n1 + var2 / n2) / (MathEx.sqr(var1 / n1) / (n1 - 1) + MathEx.sqr(var2 / n2) / (n2 - 1));

            double t = (mu1 - mu2) / Math.sqrt(var1 / n1 + var2 / n2);
            double p = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));

            return new TTest("Unequal Variance Two Sample", t, df, p);
        }
    }

    /**
     * Given the paired arrays x and y, test if they have significantly
     * different means. Small values of p-value indicate that the two arrays
     * have significantly different means.
     */
    public static TTest testPaired(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vectors have different size");
        }

        double mu1 = MathEx.mean(x);
        double var1 = MathEx.var(x);

        double mu2 = MathEx.mean(y);
        double var2 = MathEx.var(y);

        int n = x.length;
        int df = n - 1;

        double cov = 0.0;
        for (int j = 0; j < n; j++) {
            cov += (x[j] - mu1) * (y[j] - mu2);
        }
        cov /= df;

        double sd = Math.sqrt((var1 + var2 - 2.0 * cov) / n);
        double t = (mu1 - mu2) / sd;
        double p = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));

        return new TTest("Paired", t, df, p);
    }

    /**
     * Test whether the Pearson correlation coefficient, the slope of
     * a regression line, differs significantly from 0. Small values of p-value
     * indicate a significant correlation.
     * @param r the Pearson correlation coefficient.
     * @param df the degree of freedom. df = n - 2, where n is the number of samples
     * used in the calculation of r.
     */
    public static TTest test(double r, int df) {
        final double TINY = 1.0e-16;

        double t = r * Math.sqrt(df / ((1.0 - r + TINY) * (1.0 + r + TINY)));
        double p = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));

        return new TTest("Pearson correlation coefficient", t, df, p);
    }
}
