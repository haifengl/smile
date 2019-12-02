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

package smile.stat;

import smile.math.MathEx;

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

    /**
     * Returns the fitted linear function value y = intercept + slope * log(x).
     */
    private static double smoothed(int x, double slope, double intercept) {
        return Math.exp(intercept + slope * Math.log(x));
    }

    /**
     * Returns the index of given frequency.
     * @param r the frequency list.
     * @param f the given frequency.
     * @return the index of given frequency or -1 if it doesn't exist in the list.
     */
    private static int row(int[] r, int f) {
        int i = 0;

        while (i < r.length && r[i] < f) {
            ++i;
        }

        return ((i < r.length && r[i] == f) ? i : -1);
    }

    /**
     * Good–Turing frequency estimation. This technique is for estimating the
     * probability of encountering an object of a hitherto unseen species,
     * given a set of past observations of objects from different species.
     * In drawing balls from an urn, the 'objects' would be balls and the
     * 'species' would be the distinct colors of the balls (finite but
     * unknown in number). After drawing R_red red balls, R_black black
     * balls and , R_green green balls, we would ask what is the probability
     * of drawing a red ball, a black ball, a green ball or one of a
     * previously unseen color.
     *
     * This method takes a set of (frequency, frequency-of-frequency) pairs and
     * estimate the probabilities corresponding to the observed frequencies,
     * and P<sub>0</sub>, the joint probability of all unobserved species.
     *
     * @param r the frequency in ascending order.
     * @param Nr the frequency of frequencies.
     * @param p on output, it is the estimated probabilities.
     * @return P<sub>0</sub> for all unobserved species.
     */
    public static double GoodTuring(int[] r, int[] Nr, double[] p) {
        final double CONFID_FACTOR = 1.96;

        if (r.length != Nr.length) {
            throw new IllegalArgumentException("The sizes of r and Nr are not same.");
        }

        int len = r.length;
        double[] logR = new double[len];
        double[] logZ = new double[len];
        double[] Z = new double[len];

        int N = 0;
        for (int j = 0; j < len; ++j) {
            N += r[j] * Nr[j];
        }

        int n1 = (r[0] != 1) ? 0 : Nr[0];
        double p0 = n1 / (double) N;

        for (int j = 0; j < len; ++j) {
            int q = j == 0     ?  0           : r[j - 1];
            int t = j == len - 1 ? 2 * r[j] - q : r[j + 1];
            Z[j] = 2.0 * Nr[j] / (t - q);
            logR[j] = Math.log(r[j]);
            logZ[j] = Math.log(Z[j]);
        }

        // Simple linear regression.
        double XYs = 0.0, Xsquares = 0.0, meanX = 0.0, meanY = 0.0;
        for (int i = 0; i < len; ++i) {
            meanX += logR[i];
            meanY += logZ[i];
        }
        meanX /= len;
        meanY /= len;
        for (int i = 0; i < len; ++i) {
            XYs += (logR[i] - meanX) * (logZ[i] - meanY);
            Xsquares += MathEx.sqr(logR[i] - meanX);
        }

        double slope = XYs / Xsquares;
        double intercept = meanY - slope * meanX;

        boolean indiffValsSeen = false;
        for (int j = 0; j < len; ++j) {
            double y = (r[j] + 1) * smoothed(r[j] + 1, slope, intercept) / smoothed(r[j], slope, intercept);

            if (row(r, r[j] + 1) < 0) {
                indiffValsSeen = true;
            }

            if (!indiffValsSeen) {
                int n = Nr[row(r, r[j] + 1)];
                double x = (r[j] + 1) * n / (double) Nr[j];
                if (Math.abs(x - y) <= CONFID_FACTOR * Math.sqrt(MathEx.sqr(r[j] + 1.0) * n / MathEx.sqr(Nr[j]) * (1 + n / (double) Nr[j]))) {
                    indiffValsSeen = true;
                } else {
                    p[j] = x;
                }
            }

            if (indiffValsSeen) {
                p[j] = y;
            }
        }

        double Nprime = 0.0;
        for (int j = 0; j < len; ++j) {
            Nprime += Nr[j] * p[j];
        }

        for (int j = 0; j < len; ++j) {
            p[j] = (1 - p0) * p[j] / Nprime;
        }

        return p0;
    }
}
