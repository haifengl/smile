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

import smile.math.special.Gamma;

/**
 * Pearson's chi-square test, also known as the chi-square goodness-of-fit test
 * or chi-square test for independence. Note that the chi-square distribution
 * is only approximately valid for large sample size. If a significant fraction
 * of bins have small numbers of counts (say, {@code < 10}), then the statistic is
 * not well approximated by a chi-square probability function.
 *
 * @author Haifeng Li
 */
public class ChiSqTest {
    /**
     * The type of test.
     */
    public final String method;

    /**
     * The degree of freedom of chi-square statistic.
     */
    public final double df;

    /**
     * chi-square statistic
     */
    public final double chisq;

    /**
     * p-value
     */
    public final double pvalue;

    /**
     * Cramér's V is a measure of association between two nominal variables,
     * giving a value between 0 and 1 (inclusive). In the case of a 2 × 2
     * contingency table, Cramér's V is equal to the Phi coefficient.
     */
    public final double CramerV;

    /**
     * Constructor.
     * @param method the type of test.
     * @param chisq the chi-square statistic.
     * @param df the degree of freedom.
     * @param pvalue the p-value.
     */
    public ChiSqTest(String method, double chisq, double df, double pvalue) {
        this(method, chisq, df, pvalue, Double.NaN);
    }

    /**
     * Constructor.
     * @param method the type of test.
     * @param chisq the chi-square statistic.
     * @param df the degree of freedom.
     * @param pvalue the p-value.
     * @param CramerV Cramer's V measure.
     */
    public ChiSqTest(String method, double chisq, double df, double pvalue, double CramerV) {
        this.method = method;
        this.chisq = chisq;
        this.df = df;
        this.pvalue = pvalue;
        this.CramerV = CramerV;
    }

    @Override
    public String toString() {
        if (Double.isNaN(CramerV)) {
            return String.format("%s Chi-squared Test(t = %.4f, df = %.3f, p-value = %G)", method, chisq, df, pvalue);
        } else {
            return String.format("%s Chi-squared Test(t = %.4f, df = %.3f, p-value = %G, Cramer's V = %.2f)", method, chisq, df, pvalue, CramerV);
        }
    }

    /**
     * One-sample Pearson's chi-square test.
     * Given the array bins containing the observed numbers of events,
     * and an array prob containing the expected probabilities of events,
     * and given one constraint, a small value of p-value indicates
     * a significant difference between the distributions.
     *
     * @param bins the observed number of events.
     * @param prob the expected probabilities of events.
     * @return the test results.
     */
    public static ChiSqTest test(int[] bins, double[] prob) {
        return test(bins, prob, 1);
    }

    /**
     * One-sample Pearson's chi-square test.
     * Given the array bins containing the observed numbers of events,
     * and an array prob containing the expected probabilities of events,
     * and given the number of constraints (normally one), a small value
     * of p-value indicates a significant difference between the distributions.
     *
     * @param bins the observed number of events.
     * @param prob the expected probabilities of events.
     * @param constraints the constraints on the degree of freedom.
     * @return the test results.
     */
    public static ChiSqTest test(int[] bins, double[] prob, int constraints) {
        int nbins = bins.length;
        int df = nbins - constraints;

        int n = 0;
        for (int bin : bins) {
            n += bin;
        }

        double chisq = 0.0;
        for (int j = 0; j < nbins; j++) {
            if (prob[j] < 0.0 || prob[j] > 1.0 || (prob[j] == 0.0 && bins[j] > 0)) {
                throw new IllegalArgumentException("Bad expected number");
            }

            if (prob[j] == 0.0 && bins[j] == 0) {
                --df;
            } else {
                double nj = n * prob[j];
                double temp = bins[j] - nj;
                chisq += temp * temp / nj;
            }
        }

        double p = Gamma.regularizedUpperIncompleteGamma(0.5 * df, 0.5 * chisq);

        return new ChiSqTest("One Sample", chisq, df, p);
    }

    /**
     * Two-sample Pearson's chi-square test.
     * Given the arrays bins1 and bins2, containing two sets of binned data,
     * and given one constraint, a small value of p-value indicates
     * a significant difference between the distributions.
     *
     * @param bins1 the observed number of events in first sample.
     * @param bins2 the observed number of events in second sample.
     * @return the test results.
     */
    public static ChiSqTest test(int[] bins1, int[] bins2) {
        return test(bins1, bins2, 1);
    }

    /**
     * Two-sample Pearson's chi-square test.
     * Given the arrays bins1 and bins2, containing two sets of binned data,
     * and given the number of constraints (normally one), a small value of
     * p-value indicates a significant difference between the distributions.
     *
     * @param bins1 the observed number of events in first sample.
     * @param bins2 the observed number of events in second sample.
     * @param constraints the constraints on the degree of freedom.
     * @return the test results.
     */
    public static ChiSqTest test(int[] bins1, int[] bins2, int constraints) {
        if (bins1.length != bins2.length) {
            throw new IllegalArgumentException("Input vectors have different size");
        }

        int nbins = bins1.length;
        int df = nbins - constraints;
        double chisq = 0.0;
        for (int j = 0; j < nbins; j++) {
            if (bins1[j] == 0 && bins2[j] == 0) {
                --df;
            } else {
                double temp = bins1[j] - bins2[j];
                chisq += temp * temp / (bins1[j] + bins2[j]);
            }
        }

        double p = Gamma.regularizedUpperIncompleteGamma(0.5 * df, 0.5 * chisq);

        return new ChiSqTest("Two Sample", chisq, df, p);
    }

    /**
     * Independence test on a two-dimensional contingency table.
     * The rows of contingency table are the values of
     * one nominal variable, the columns are the values of
     * the other nominal variable. The entries are the number of
     * observed events for each combination of row and column.
     * <p>
     * Continuity correction will be applied when computing the
     * test statistic for 2x2 tables: one half is subtracted from
     * all |O-E| differences. The correlation coefficient is
     * calculated as Cramer's V.
     *
     * @param table the contingency table.
     * @return the test results.
     */
    public static ChiSqTest test(int[][] table) {
        final double TINY = 1.0e-16;

        int nrow = table.length;
        int ncol = table[0].length;

        boolean correct = false;
        if (nrow == 2 && ncol == 2) {
            correct = true;
        }

        double n = 0.0; // total observations
        int r = nrow; // without all zero rows
        double[] ni = new double[nrow]; // observations per row
        for (int i = 0; i < nrow; i++) {
            for (int j = 0; j < ncol; j++) {
                ni[i] += table[i][j];
                n += table[i][j];
            }
            if (ni[i] == 0.0) {
                --r;
            }
        }

        int k = ncol; // without all zero columns
        double[] nj = new double[ncol]; // observations per column
        for (int j = 0; j < ncol; j++) {
            for (int[] row : table) {
                nj[j] += row[j];
            }
            if (nj[j] == 0.0) {
                --k;
            }
        }

        int df = r * k - r - k + 1;
        double chisq = 0.0;
        for (int i = 0; i < nrow; i++) {
            for (int j = 0; j < ncol; j++) {
                double expctd = nj[j] * ni[i] / n;
                double temp = table[i][j] - expctd;
                if (correct) temp = Math.abs(temp) - 0.5;
                chisq += temp * temp / (expctd + TINY);
            }
        }

        double prob = Gamma.regularizedUpperIncompleteGamma(0.5 * df, 0.5 * chisq);
        int min = Math.min(r, k) - 1;
        double CramerV = Math.sqrt(chisq/(n*min));

        return new ChiSqTest("Pearson's", chisq, df, prob, CramerV);
    }
}
