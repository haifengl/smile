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
import smile.math.MathEx;
import smile.util.IntSet;

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
     * The probabilities for each x.
     */
    public final double[] p;
    /**
     * The possible values of random variable.
     */
    private IntSet x;
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
     * @param prob the probabilities.
     */
    public EmpiricalDistribution(double[] prob) {
        this(prob, IntSet.of(prob.length));
    }

    /**
     * Constructor.
     * @param prob the probabilities.
     * @param x the values of random variable.
     */
    public EmpiricalDistribution(double[] prob, IntSet x) {
        if (prob.length == 0) {
            throw new IllegalArgumentException("Empty probability set.");
        }

        this.x = x;
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

            p[i] = prob[i];

            if (i > 0) {
                cdf[i] = cdf[i - 1] + p[i];
            }

            int xi = x.valueOf(i);
            mean += xi * p[i];
            mean2 += xi * xi * p[i];
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
     * @param data the training data.
     */
    public static EmpiricalDistribution fit(int[] data) {
        return fit(data, IntSet.of(data));
    }

    /**
     * Estimates the distribution. Sometimes, the data may not
     * contain all possible values. In this case, the user should
     * provide the value set.
     * @param data the training data.
     * @param x the value set.
     */
    public static EmpiricalDistribution fit(int[] data, IntSet x) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Empty dataset.");
        }

        int k = x.size();
        double[] p = new double[k];

        for (int xi : data) {
            p[x.indexOf(xi)]++;
        }

        int n = data.length;
        for (int i = 0; i < k; i++) {
            p[i] /= n;
        }

        return new EmpiricalDistribution(p, x);
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
            return x.valueOf(k);
        } else {
            return x.valueOf(a[k]);
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
                ans[i] = x.valueOf(k);
            } else {
                ans[i] = x.valueOf(a[k]);
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
        if (k < x.min || k > x.max) {
            return 0.0;
        } else {
            return p[x.indexOf(k)];
        }
    }

    @Override
    public double logp(int k) {
        if (k < x.min || k > x.max) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return Math.log(p[x.indexOf(k)]);
        }
    }

    @Override
    public double cdf(double k) {
        if (k < x.min) {
            return 0.0;
        } else if (k >= x.max) {
            return 1.0;
        } else {
            return cdf[x.indexOf((int) Math.floor(k))];
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        int k = Arrays.binarySearch(cdf, p);
        if (k < 0) {
            return x.valueOf(-k - 1);
        } else {
            return x.valueOf(k);
        }
    }
}
