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
package smile.stat;

import smile.stat.distribution.Distribution;
import smile.stat.hypothesis.ChiSqTest;
import smile.stat.hypothesis.CorTest;
import smile.stat.hypothesis.FTest;
import smile.stat.hypothesis.KSTest;
import smile.stat.hypothesis.TTest;

/**
 * Statistical functions or tests.
 *
 * @author Haifeng Li
 */
public class Stat {
    /**
     * Private constructor.
     */
    private Stat() {
    }

    /**
     * Bayesian information criterion (BIC). BIC or Schwarz Criterion is a criterion for
     * model selection among a class of parametric models with different numbers
     * of parameters. Choosing a model to optimize BIC is a form of regularization.
     * <p>
     * When estimating model parameters using maximum likelihood estimation, it
     * is possible to increase the likelihood by adding additional parameters,
     * which may result in over-fitting. The BIC resolves this problem by
     * introducing a penalty term for the number of parameters in the model.
     * BIC is very closely related to the Akaike information criterion (AIC).
     * However, its penalty for additional parameters is stronger than that of AIC.
     * <p>
     * The formula for the BIC is BIC = L - 0.5 * v * log n where L is the
     * log-likelihood of estimated model, v is the number of free parameters
     * to be estimated in the model, and n is the number of samples.
     * <p>
     * Given any two estimated models, the model with the larger value of BIC is
     * the one to be preferred.
     *
     * @param L the log-likelihood of estimated model.
     * @param v the number of free parameters to be estimated in the model.
     * @param n the number of samples.
     * @return BIC score.
     */
    public static double bic(double L, int v, int n) {
        return L - 0.5 * v * Math.log(n);
    }

    /** t-test. */
    public static class t {
        private t() { }

        /**
         * Independent one-sample t-test whether the mean of a normally distributed
         * population has a value specified in a null hypothesis. Small values of
         * p-value indicate that the array has significantly different mean.
         */
        public static TTest test(double[] x, double mean) {
            return TTest.test(x, mean);
        }

        /**
         * Test if the arrays x and y have significantly different means. The data
         * arrays are assumed to be drawn from populations with unequal variances.
         * Small values of p-value indicate that the two arrays have significantly
         * different means.
         */
        public static TTest test(double[] x, double[] y) {
            return TTest.test(x, y, false);
        }

        /**
         * Test if the arrays x and y have significantly different means.  Small
         * values of p-value indicate that the two arrays have significantly
         * different means.
         * @param option "equal.var" if the data arrays are assumed to be drawn
         *               from populations with the same true variance.
         *               "unequal.var if the data arrays are allowed to be drawn
         *               from populations with unequal variances.
         *               "paired" if x and y are two values (i.e., pair of values)
         *               for the same samples.
         */
        public static TTest test(double[] x, double[] y, String option) {
            switch (option) {
                case "unequal.var": return TTest.test(x, y, false);
                case "equal.var": return TTest.test(x, y, true);
                case "paired": return TTest.testPaired(x, y);
            }
            return TTest.testPaired(x, y);
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
            return TTest.test(r, df);
        }
    }

    /** F-test. */
    public static class F {
        private F() { }

        /**
         * Test if the arrays x and y have significantly different variances.
         * Small values of p-value indicate that the two arrays have significantly
         * different variances.
         */
        public static FTest test(double[] x, double[] y) {
            return FTest.test(x, y);
        }
    }

    /** The Kolmogorov-Smirnov test (K-S test). */
    public static class KS {
        private KS() { }

        /**
         * The one-sample KS test for the null hypothesis that the data set x
         * is drawn from the given distribution. Small values of p-value show that
         * the cumulative distribution function of x is significantly different from
         * the given distribution. The array x is modified by being sorted into
         * ascending order.
         */
        public static KSTest test(double[] x, Distribution dist) {
            return KSTest.test(x, dist);
        }

        /**
         * The two-sample KS test for the null hypothesis that the data sets
         * are drawn from the same distribution. Small values of p-value show that
         * the cumulative distribution function of x is significantly different from
         * that of y. The arrays x and y are modified by being sorted into
         * ascending order.
         */
        public static KSTest test(double[] x, double[] y) {
            return KSTest.test(x, y);
        }
    }

    /** Correlation test. */
    public static class cor {
        private cor() { }

        /** Pearson correlation test. */
        public static CorTest test(double[] x, double[] y) {
            return CorTest.pearson(x, y);
        }

        /**
         * Correlation test. Supported methods include "pearson", "kendall", and "spearman".
         */
        public static CorTest test(double[] x, double[] y, String method) {
            switch (method) {
                case "pearson": return CorTest.pearson(x, y);
                case "kendall": return CorTest.kendall(x, y);
                case "spearman": return CorTest.spearman(x, y);
                default: throw new IllegalArgumentException("Invalid correlation test method: " + method);
            }
        }
    }

    /** Chi-square test. */
    public static class chisq {
        private chisq() { }

        /**
         * One-sample chisq test. Given the array bins containing the observed numbers of events,
         * and an array prob containing the expected probabilities of events, and given
         * one constraint, a small value of p-value indicates a significant
         * difference between the distributions.
         */
        public static ChiSqTest test(int[] bins, double[] prob) {
            return test(bins, prob, 1);
        }

        /**
         * One-sample chisq test. Given the array bins containing the observed numbers of events,
         * and an array prob containing the expected probabilities of events, and given
         * the number of constraints (normally one), a small value of p-value
         * indicates a significant difference between the distributions.
         */
        public static ChiSqTest test(int[] bins, double[] prob, int constraints) {
            return ChiSqTest.test(bins, prob, constraints);
        }

        /**
         * Two-sample chisq test. Given the arrays bins1 and bins2, containing two
         * sets of binned data, and given one constraint, a small value of
         * p-value indicates a significant difference between the distributions.
         */
        public static ChiSqTest test(int[] bins1, int[] bins2) {
            return test(bins1, bins2, 1);
        }

        /**
         * Two-sample chisq test. Given the arrays bins1 and bins2, containing two
         * sets of binned data, and given the number of constraints (normally one),
         * a small value of p-value indicates a significant difference between
         * the distributions.
         */
        public static ChiSqTest test(int[] bins1, int[] bins2, int constraints) {
            return ChiSqTest.test(bins1, bins2, constraints);
        }

        /**
         * Given a two-dimensional contingency table in the form of an array of
         * integers, returns Chi-square test for independence. The rows of contingency table
         * are labels by the values of one nominal variable, the columns are labels
         * by the values of the other nominal variable, and whose entries are
         * nonnegative integers giving the number of observed events for each
         * combination of row and column. Continuity correction
         * will be applied when computing the test statistic for 2x2 tables: one half
         * is subtracted from all |O-E| differences. The correlation coefficient is
         * calculated as Cramer's V.
         */
        public static ChiSqTest test(int[][] table) {
            return ChiSqTest.test(table);
        }
    }
}
