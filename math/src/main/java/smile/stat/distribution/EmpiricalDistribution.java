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

package smile.stat.distribution;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import smile.math.MathEx;

/**
 * An empirical distribution function or empirical cdf, is a cumulative
 * probability distribution function that concentrates probability 1/n at
 * each of the n numbers in a sample. As n grows the empirical distribution
 * will getting closer to the true distribution.
 * Empirical distribution is a very important estimator in Statistics. In
 * particular, the Bootstrap method rely heavily on the empirical distribution.
 *
 * @author Haifeng Li
 */
public class EmpiricalDistribution extends DiscreteDistribution {
    private static final long serialVersionUID = 2L;

    /**
     * The possible values of random variable.
     */
    private int[] x;
    /**
     * Minimum value of x.
     */
    private int xMin;
    /**
     * Maximum value of x.
     */
    private int xMax;
    /**
     * Probabilities for each x.
     */
    private double[] p;
    /**
     * CDF at each x.
     */
    private double[] cdf;
    private double mean;
    private double variance;
    private double sd;
    private double entropy;
    // Walker's alias method to generate random samples.
    private int[] a;
    private double[] q;

    /**
     * Constructor.
     */
    public EmpiricalDistribution(double[] prob) {
        if (prob.length == 0) {
            throw new IllegalArgumentException("Empty probability set.");
        }

        xMin = 0;
        xMax = prob.length - 1;

        x = new int[prob.length];
        p = new double[prob.length];
        cdf = new double[prob.length];

        mean = 0.0;
        double mean2 = 0.0;
        entropy = 0.0;
        cdf[0] = prob[0];
        for (int i = 0; i < prob.length; i++) {
            if (prob[i] < 0 || prob[i] > 1) {
                throw new IllegalArgumentException("Invalid probability " + p[i]);
            }

            x[i] = i;
            p[i] = prob[i];

            if (i > 0) {
                cdf[i] = cdf[i - 1] + p[i];
            }

            mean += x[i] * p[i];
            mean2 += x[i] * x[i] * p[i];
            entropy -= p[i] * MathEx.log2(p[i]);
        }

        variance = mean2 - mean * mean;
        sd = Math.sqrt(variance);

        if (Math.abs(cdf[cdf.length - 1] - 1.0) > 1E-7) {
            throw new IllegalArgumentException("The sum of probabilities is not 1.");
        }
    }

    /**
     * Estimates the distribution.
     */
    public EmpiricalDistribution(int[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Empty dataset.");
        }

        xMin = MathEx.min(data);
        xMax = MathEx.max(data);

        int n = xMax - xMin + 1;
        x = new int[n];
        p = new double[n];
        cdf = new double[n];

        for (int i = 0; i < data.length; i++) {
            p[data[i] - xMin]++;
        }

        mean = 0.0;
        double mean2 = 0.0;
        entropy = 0.0;
        for (int i = 0; i < n; i++) {
            x[i] = xMin + i;
            p[i] /= data.length;

            if (i == 0) {
                cdf[0] = p[0];
            } else {
                cdf[i] = cdf[i - 1] + p[i];
            }

            mean += x[i] * p[i];
            mean2 += x[i] * x[i] * p[i];
            entropy -= p[i] * MathEx.log2(p[i]);
        }

        variance = mean2 - mean * mean;
        sd = Math.sqrt(variance);
    }

    @Override
    public int length() {
        return p.length;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double variance() {
        return variance;
    }

    @Override
    public double sd() {
        return sd;
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return Arrays.stream(p).mapToObj(pi -> String.format("%.2f", pi)).collect(Collectors.joining(", ", "Empirical Distribution(", ")"));
    }

    @Override
    public double rand() {
        if (a == null) {
            initRand();
        }

        // generate sample
        double rU = MathEx.random() * p.length;

        int k = (int) (rU);
        rU -= k;  /* rU becomes rU-[rU] */

        if (rU < q[k]) {
            return k;
        } else {
            return a[k];
        }
    }

    @Override
    public int[] randi(int n) {
        if (a == null) {
            initRand();
        }

        // generate sample
        int[] ans = new int[n];
        for (int i = 0; i < n; i++) {
            double rU = MathEx.random() * p.length;

            int k = (int) (rU);
            rU -= k;  /* rU becomes rU-[rU] */

            if (rU < q[k]) {
                ans[i] = k;
            } else {
                ans[i] = a[k];
            }
        }

        return ans;
    }

    /** Initializes the random number generator. */
    private synchronized void initRand() {
        // set up alias table
        q = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            q[i] = p[i] * p.length;
        }

        // initialize a with indices
        a = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            a[i] = i;
        }

        // set up H and L
        int[] HL = new int[p.length];
        int head = 0;
        int tail = p.length - 1;
        for (int i = 0; i < p.length; i++) {
            if (q[i] >= 1.0) {
                HL[head++] = i;
            } else {
                HL[tail--] = i;
            }
        }

        while (head != 0 && tail != p.length - 1) {
            int j = HL[tail + 1];
            int k = HL[head - 1];
            a[j] = k;
            q[k] += q[j] - 1;
            tail++;                                  // remove j from L
            if (q[k] < 1.0) {
                HL[tail--] = k;                      // add k to L
                head--;                              // remove k
            }
        }

    }

    @Override
    public double p(int k) {
        if (k < xMin || k > xMax) {
            return 0.0;
        } else {
            return p[k - xMin];
        }
    }

    @Override
    public double logp(int k) {
        if (k < xMin || k > xMax) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return Math.log(p[k - xMin]);
        }
    }

    @Override
    public double cdf(double k) {
        if (k < xMin) {
            return 0.0;
        } else if (k >= xMax) {
            return 1.0;
        } else {
            return cdf[(int) Math.floor(k - xMin)];
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        int k = Arrays.binarySearch(cdf, p);
        if (k < 0) {
            return x[-k - 1];
        } else {
            return x[k];
        }
    }
}
