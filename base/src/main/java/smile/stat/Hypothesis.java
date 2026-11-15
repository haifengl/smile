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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.stat;

import smile.data.DataFrame;
import smile.sort.IQAgent;
import smile.stat.distribution.Distribution;
import smile.stat.hypothesis.*;

import java.util.Locale;

/**
 * Hypothesis test facade interface.
 *
 * @author Haifeng Li
 */
public interface Hypothesis {
    /**
     * Returns the significance code of p-value.
     * Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
     *
     * @param pvalue a p-value.
     * @return the significance code of p-value.
     */
    static String significance(double pvalue) {
        if (pvalue < 0.001)
            return "***";
        else if (pvalue < 0.01)
            return "**";
        else if (pvalue < 0.05)
            return "*";
        else if (pvalue < 0.1)
            return ".";
        else
            return "";
    }

    /** t-test. */
    interface t {
        /**
         * Independent one-sample t-test whether the mean of a normally distributed
         * population has a value specified in a null hypothesis. Small values of
         * p-value indicate that the array has significantly different mean.
         *
         * @param x the sample values.
         * @param mean the mean.
         * @return the test results.
         */
        static TTest test(double[] x, double mean) {
            return TTest.test(x, mean);
        }

        /**
         * Test if the arrays x and y have significantly different means. The data
         * arrays are assumed to be drawn from populations with unequal variances.
         * Small values of p-value indicate that the two arrays have significantly
         * different means.
         *
         * @param x the sample values.
         * @param y the sample values.
         * @return the test results.
         */
        static TTest test(double[] x, double[] y) {
            return TTest.test(x, y, false);
        }

        /**
         * Test if the arrays x and y have significantly different means.  Small
         * values of p-value indicate that the two arrays have significantly
         * different means.
         *
         * @param x the sample values.
         * @param y the sample values.
         * @param option "equal.var" if the data arrays are assumed to be drawn
         *               from populations with the same true variance.
         *               "unequal.var if the data arrays are allowed to be drawn
         *               from populations with unequal variances.
         *               "paired" if x and y are two values (i.e., a pair of values)
         *               for the same samples.
         * @return the test results.
         */
        static TTest test(double[] x, double[] y, String option) {
            return switch (option.toLowerCase(Locale.ROOT)) {
                case "unequal.var" -> TTest.test(x, y, false);
                case "equal.var" -> TTest.test(x, y, true);
                case "paired" -> TTest.testPaired(x, y);
                default -> throw new IllegalArgumentException("Invalid option: " + option);
            };
        }

        /**
         * Test whether the Pearson correlation coefficient, the slope of
         * a regression line, differs significantly from 0. Small values of p-value
         * indicate a significant correlation.
         *
         * @param r the Pearson correlation coefficient.
         * @param df the degree of freedom. df = n - 2, where n is the number of samples
         * used in the calculation of r.
         * @return the test results.
         */
        static TTest test(double r, int df) {
            return TTest.test(r, df);
        }
    }

    /** F-test. */
    interface F {
        /**
         * Test if the arrays x and y have significantly different variances.
         * Small values of p-value indicate that the two arrays have significantly
         * different variances.
         *
         * @param x the sample values.
         * @param y the sample values.
         * @return the test results.
         */
        static FTest test(double[] x, double[] y) {
            return FTest.test(x, y);
        }

        /**
         * One-way ANOVA. Tests whether the means of several groups are equal.
         * Small values of p-value indicate that at least one group mean differs.
         *
         * @param x the categorical grouping variable.
         * @param y the continuous response variable.
         * @return the test results.
         */
        static FTest test(int[] x, double[] y) {
            return FTest.test(x, y);
        }
    }

    /** The Kolmogorov-Smirnov test (K-S test). */
    interface KS {
        /**
         * The one-sample KS test for the null hypothesis that the data set x
         * is drawn from the given distribution. Small values of p-value show that
         * the cumulative distribution function of x is significantly different from
         * the given distribution. The array x is modified by being sorted into
         * ascending order.
         *
         * @param x the sample values.
         * @param dist the distribution.
         * @return the test results.
         */
        static KSTest test(double[] x, Distribution dist) {
            return KSTest.test(x, dist);
        }

        /**
         * The two-sample KS test for the null hypothesis that the data sets
         * are drawn from the same distribution. Small values of p-value show that
         * the cumulative distribution function of x is significantly different from
         * that of y. The arrays x and y are modified by being sorted into
         * ascending order.
         *
         * @param x the sample values.
         * @param y the sample values.
         * @return the test results.
         */
        static KSTest test(double[] x, double[] y) {
            return KSTest.test(x, y);
        }

        /**
         * The one-sample streaming KS test for the null hypothesis that the
         * observations summarized by {@code x} are drawn from the given distribution.
         *
         * @param x the streaming quantile sketch of sample values.
         * @param dist the reference distribution.
         * @return the test results.
         */
        static KSTest test(IQAgent x, Distribution dist) {
            return KSTest.test(x, dist);
        }

        /**
         * The two-sample streaming KS test for the null hypothesis that two
         * streams summarized by {@code x} and {@code y} are drawn from the same
         * continuous distribution.
         *
         * @param x the streaming quantile sketch of the first sample.
         * @param y the streaming quantile sketch of the second sample.
         * @return the test results.
         */
        static KSTest test(IQAgent x, IQAgent y) {
            return KSTest.test(x, y);
        }

        /**
         * The two-sample KS test comparing a reference sample array {@code x}
         * against a streaming quantile sketch {@code y}.
         *
         * @param x the reference sample values.
         * @param y the streaming quantile sketch of the second sample.
         * @return the test results.
         */
        static KSTest test(double[] x, IQAgent y) {
            return KSTest.test(x, y);
        }
    }

    /** Correlation test. */
    interface cor {
        /**
         * Pearson correlation test.
         *
         * @param x the sample values.
         * @param y the sample values.
         * @return the test results.
         */
        static CorTest test(double[] x, double[] y) {
            return CorTest.pearson(x, y);
        }

        /**
         * Correlation test. Supported methods include "pearson", "kendall", and "spearman".
         *
         * @param x the sample values.
         * @param y the sample values.
         * @param method supported methods include "pearson", "kendall", and "spearman".
         * @return the test results.
         */
        static CorTest test(double[] x, double[] y, String method) {
            return switch (method.toLowerCase(Locale.ROOT)) {
                case "pearson" -> CorTest.pearson(x, y);
                case "kendall" -> CorTest.kendall(x, y);
                case "spearman" -> CorTest.spearman(x, y);
                default -> throw new IllegalArgumentException("Invalid correlation test method: " + method);
            };
        }
    }

    /** Chi-square test. */
    interface chisq {
        /**
         * One-sample chisq test. Given the array bins containing the observed numbers of events,
         * and an array prob containing the expected probabilities of events, and given
         * one constraint, a small value of p-value indicates a significant
         * difference between the distributions.
         *
         * @param bins the observed number of events.
         * @param prob the expected probabilities of events.
         * @return the test results.
         */
        static ChiSqTest test(int[] bins, double[] prob) {
            return test(bins, prob, 1);
        }

        /**
         * One-sample chisq test. Given the array bins containing the observed numbers of events,
         * and an array prob containing the expected probabilities of events, and given
         * the number of constraints (normally one), a small value of p-value
         * indicates a significant difference between the distributions.
         *
         * @param bins the observed number of events.
         * @param prob the expected probabilities of events.
         * @param constraints the constraints on the degree of freedom.
         * @return the test results.
         */
        static ChiSqTest test(int[] bins, double[] prob, int constraints) {
            return ChiSqTest.test(bins, prob, constraints);
        }

        /**
         * Two-sample chisq test. Given the arrays bins1 and bins2, containing two
         * sets of binned data, and given one constraint, a small value of
         * p-value indicates a significant difference between the distributions.
         *
         * @param bins1 the observed number of events in first sample.
         * @param bins2 the observed number of events in second sample.
         * @return the test results.
         */
        static ChiSqTest test(int[] bins1, int[] bins2) {
            return test(bins1, bins2, 1);
        }

        /**
         * Two-sample chisq test. Given the arrays bins1 and bins2, containing two
         * sets of binned data, and given the number of constraints (normally one),
         * a small value of p-value indicates a significant difference between
         * the distributions.
         *
         * @param bins1 the observed number of events in first sample.
         * @param bins2 the observed number of events in second sample.
         * @param constraints the constraints on the degree of freedom.
         * @return the test results.
         */
        static ChiSqTest test(int[] bins1, int[] bins2, int constraints) {
            return ChiSqTest.test(bins1, bins2, constraints);
        }

        /**
         * Given a two-dimensional contingency table in the form of an array of
         * integers, returns Chi-square test for independence. The rows of contingency table
         * are labels by the values of one nominal variable, the columns are labels
         * by the values of the other nominal variable, and whose entries are
         * non-negative integers giving the number of observed events for each
         * combination of row and column. Continuity correction
         * will be applied when computing the test statistic for 2x2 tables: one half
         * is subtracted from all |O-E| differences. The correlation coefficient is
         * calculated as Cramer's V.
         *
         * @param table the contingency table.
         * @return the test results.
         */
        static ChiSqTest test(int[][] table) {
            return ChiSqTest.test(table);
        }
    }

    /** Population Stability Index (PSI) and categorical frequency shift test. */
    interface psi {
        /**
         * Computes PSI between a baseline and an actual continuous sample using
         * 10 quantile bins fitted on {@code expected}.
         *
         * @param expected the baseline sample values.
         * @param actual the actual sample values.
         * @return the PSI result.
         */
        static PSI test(double[] expected, double[] actual) {
            return PSI.test(expected, actual);
        }

        /**
         * Computes PSI between a baseline and an actual continuous sample using
         * {@code nbins} quantile bins fitted on {@code expected}.
         *
         * @param expected the baseline sample values.
         * @param actual the actual sample values.
         * @param nbins the number of quantile bins.
         * @return the PSI result.
         */
        static PSI test(double[] expected, double[] actual, int nbins) {
            return PSI.test(expected, actual, nbins);
        }

        /**
         * Computes PSI between a baseline and an actual continuous sample using
         * custom interior breakpoints {@code breaks}.
         *
         * @param expected the baseline sample values.
         * @param actual the actual sample values.
         * @param breaks the strictly increasing interior breakpoints.
         * @return the PSI result.
         */
        static PSI test(double[] expected, double[] actual, double[] breaks) {
            return PSI.test(expected, actual, breaks);
        }

        /**
         * Computes PSI from expected bin probabilities and observed bin counts.
         *
         * @param expectedProb the baseline bin probabilities.
         * @param actualBins the observed counts per bin.
         * @return the PSI result.
         */
        static PSI test(double[] expectedProb, int[] actualBins) {
            return PSI.test(expectedProb, actualBins);
        }

        /**
         * Computes PSI from two sets of binned counts.
         *
         * @param expectedBins the baseline bin counts.
         * @param actualBins the actual bin counts.
         * @return the PSI result.
         */
        static PSI test(int[] expectedBins, int[] actualBins) {
            return PSI.test(expectedBins, actualBins);
        }

        /**
         * Computes categorical frequency shift from two arrays of non-negative
         * integer category codes.
         *
         * @param expected the baseline category codes.
         * @param actual the actual category codes.
         * @return the PSI result.
         */
        static PSI testCategorical(int[] expected, int[] actual) {
            return PSI.testCategorical(expected, actual);
        }

        /**
         * Computes PSI for all numeric and nominal columns shared between a
         * baseline {@link DataFrame} and an actual {@link DataFrame}.
         *
         * @param expected the baseline DataFrame.
         * @param actual the actual DataFrame.
         * @return an array of per-feature PSI results.
         */
        static PSI[] test(DataFrame expected, DataFrame actual) {
            return PSI.test(expected, actual);
        }
    }
}
