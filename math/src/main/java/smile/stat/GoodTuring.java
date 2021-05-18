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

package smile.stat;

import smile.math.MathEx;

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
 * <p>
 * This method takes a set of (frequency, frequency-of-frequency) pairs and
 * estimate the probabilities corresponding to the observed frequencies,
 * and P<sub>0</sub>, the joint probability of all unobserved species.
 *
 * @author Haifeng Li
 */
public class GoodTuring {
    /** The probabilities corresponding to the observed frequencies. */
    public final double[] p;
    /** The joint probability of all unobserved species. */
    public final double p0;

    /**
     * Private constructor.
     */
    private GoodTuring(double[] p, double p0) {
        this.p = p;
        this.p0 = p0;
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
     * Good–Turing frequency estimation.
     *
     * @param r the frequency in ascending order.
     * @param Nr the frequency of frequencies.
     * @return the estimation object.
     */
    public static GoodTuring of(int[] r, int[] Nr) {
        final double CONFID_FACTOR = 1.96;

        if (r.length != Nr.length) {
            throw new IllegalArgumentException("The sizes of r and Nr are not same.");
        }

        int len = r.length;
        double[] p = new double[len];
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
            Xsquares += MathEx.pow2(logR[i] - meanX);
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
                if (Math.abs(x - y) <= CONFID_FACTOR * Math.sqrt(MathEx.pow2(r[j] + 1.0) * n / MathEx.pow2(Nr[j]) * (1 + n / (double) Nr[j]))) {
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

        return new GoodTuring(p, p0);
    }
}
