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

import smile.math.special.Gamma;

/**
 * Pearson's chi-square test, also known as the chi-square goodness-of-fit test
 * or chi-square test for independence. Note that the chi-square distribution
 * is only approximately valid for large sample size. If a siginficant fraction
 * of bins have small numbers of counts (say, &lt; 10), then it the statistic is
 * not well approximated by a chi-square probability function.
 *
 * @author Haifeng Li
 */
public class ChiSqTest {
    /**
     * The degree of freedom of chisq-statistic.
     */
    public double df;

    /**
     * chi-square statistic
     */
    public double chisq;

    /**
     * p-value
     */
    public double pvalue;

    /**
     * Constructor.
     */
    private ChiSqTest(double chisq, double df, double pvalue) {
        this.chisq = chisq;
        this.df = df;
        this.pvalue = pvalue;
    }

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
        int nbins = bins.length;
        int df = nbins - constraints;

        int n = 0;
        for (int i = 0; i < nbins; i++)
            n+= bins[i];

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

        return new ChiSqTest(chisq, df, p);
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

        return new ChiSqTest(chisq, df, p);
    }
}
