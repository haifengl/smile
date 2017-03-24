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

import smile.math.special.Beta;
import smile.math.special.Erf;
import smile.math.special.Gamma;
import smile.math.Math;
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
 * and Kendall (for uneven or multiple rankings), and can be selected using
 * the table below.
 * <table summary="" border="1" style="border-collapse: collapse" width="95%" cellspacing="0" id="table3">
 *   <tr>
 *     <td align="center" colspan="2" width="100%" bgcolor="#FFFF99">
 *     <p align="left" style="margin-top: 0; margin-bottom: 0">
 *     Parametric variables follow normal distribution and linear
 *     relationship between x and y)</td>
 *   </tr>
 *   <tr>
 *     <td width="5%" align="center" bgcolor="#99FF99">
 *     <p style="margin-top: 0; margin-bottom: 0">Y</td>
 *     <td>
 *     <p style="margin-left: 2px; margin-right: 2px">Pearson correlation</td>
 *   </tr>
 *   <tr>
 *     <td width="5%" align="center" bgcolor="#FF66FF">
 *     <p style="margin-top: 0; margin-bottom: 0">N</td>
 *     <td width="*" align="left">
 * <table summary="" border="1" style="border-collapse: collapse" width="100%" cellspacing="0" id="table4">
 *   <tr>
 *     <td align="center" colspan="2" width="100%" bgcolor="#FFFF99">
 *     <p align="left" style="margin-top: 0; margin-bottom: 0">Equidistant
 *     positions on variables measured?</td>
 *   </tr>
 *   <tr>
 *     <td width="5%" align="center" bgcolor="#99FF99">
 *     <p style="margin-top: 0; margin-bottom: 0">Y</td>
 *     <td>
 *     <p style="margin-left: 2px; margin-right: 2px">
 *     Spearman correlation</td>
 *   </tr>
 *   <tr>
 *     <td width="5%" align="center" bgcolor="#FF66FF">
 *     <p style="margin-top: 0; margin-bottom: 0">N</td>
 *     <td width="*" align="left">
 *     <p style="margin-left: 2px; margin-right: 2px">Kendall correlation</td>
 *   </tr>
 * </table>
 *     </td>
 *   </tr>
 * </table>
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
     * Correlation coefficient
     */
    public double cor;

    /**
     * Degree of freedom
     */
    public double df;

    /**
     * test statistic
     */
    public double t;

    /**
     * (two-sided) p-value of test
     */
    public double pvalue;

    /**
     * Constructor.
     */
    private CorTest(double cor, double df, double t, double pvalue) {
        this.cor = cor;
        this.df = df;
        this.t = t;
        this.pvalue = pvalue;
    }

    /**
     * Pearson correlation coefficient test.
     */
    public static CorTest pearson(double[] x, double[] y) {
        final double TINY = 1.0e-20;

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
        double prob = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));

        return new CorTest(r, df, t, prob);
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
     *  The p-value is calculated by approximation, which is good for n &gt; 10.
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
            d += Math.sqr(wksp1[j] - wksp2[j]);
        }

        int en = n;
        double  en3n = en * en * en - en;
        double fac = (1.0 - sf / en3n) * (1.0 - sg / en3n);
        double rs = (1.0 - (6.0 / en3n) * (d + (sf + sg) / 12.0)) / Math.sqrt(fac);
        fac = (rs + 1.0) * (1.0 - rs);

        double probrs = 0.0;
        double t = rs * Math.sqrt((en - 2.0) / fac);
        int df = en - 2;
        if (fac > 0.0) {
            probrs = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
        }

        return new CorTest(rs, df, t, probrs);
    }

    /**
     * Kendall rank correlation test. The Kendall Tau Rank Correlation
     * Coefficient is used to measure the degree of correspondence
     * between sets of rankings where the measures are not equidistant.
     * It is used with non-parametric data. The p-value is calculated by
     * approximation, which is good for n &gt; 10.
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
        double svar = (4.0 * n + 10.0) / (9.0 * n * (n - 1.0));
        double z = tau / Math.sqrt(svar);
        double prob = Erf.erfcc(Math.abs(z) / 1.4142136);
        return new CorTest(tau, 0, z, prob);
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
    public static CorTest chisq(int[][] table) {
        final double TINY = 1.0e-30;

        int ni = table.length;
        int nj = table[0].length;

        boolean correct = false;
        if (ni == 2 && nj == 2) {
            correct = true;
        }

        double sum = 0.0;

        int nni = ni;
        double[] sumi = new double[ni];
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                sumi[i] += table[i][j];
                sum += table[i][j];
            }
            if (sumi[i] == 0.0) {
                --nni;
            }
        }
        
        int nnj = nj;
        double[] sumj = new double[nj];
        for (int j = 0; j < nj; j++) {
            for (int i = 0; i < ni; i++) {
                sumj[j] += table[i][j];
            }
            if (sumj[j] == 0.0) {
                --nnj;
            }
        }

        int df = nni * nnj - nni - nnj + 1;
        double chisq = 0.0;
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                double expctd = sumj[j] * sumi[i] / sum;
                double temp = table[i][j] - expctd;
                if (correct) temp = Math.abs(temp) - 0.5;
                chisq += temp * temp / (expctd + TINY);
            }
        }

        double prob = Gamma.regularizedUpperIncompleteGamma(0.5 * df, 0.5 * chisq);
        int minij = nni < nnj ? nni-1 : nnj-1;
        double v = Math.sqrt(chisq/(sum*minij));

        return new CorTest(v, df, chisq, prob);
    }
}
