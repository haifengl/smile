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
package smile.stat.distribution;

import java.util.Arrays;
import smile.math.Math;

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
    private double var;
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
            entropy -= p[i] * Math.log2(p[i]);
        }

        var = mean2 - mean * mean;
        sd = Math.sqrt(var);

        if (Math.abs(cdf[cdf.length - 1] - 1.0) > 1E-7) {
            throw new IllegalArgumentException("The sum of probabilities is not 1.");
        }
    }

    /**
     * Constructor. CDF will be estimated from the data.
     */
    public EmpiricalDistribution(int[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Empty dataset.");
        }

        xMin = Math.min(data);
        xMax = Math.max(data);

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
            entropy -= p[i] * Math.log2(p[i]);
        }

        var = mean2 - mean * mean;
        sd = Math.sqrt(var);
    }

    @Override
    public int npara() {
        return p.length;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double var() {
        return var;
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
        StringBuilder builder = new StringBuilder("Empirical Distribution(");
        for (int i = 0; i < p.length; i++) {
            builder.append(p[i]);
            builder.append(' ');
        }
        builder.setCharAt(builder.length() - 1, ')');
        return builder.toString();
    }

    @Override
    public double rand() {

        if (a == null) {
            initRand();
        }

        // generate sample
        double rU = Math.random() * p.length;

        int k = (int) (rU);
        rU -= k;  /* rU becomes rU-[rU] */

        if (rU < q[k]) {
            return k;
        } else {
            return a[k];
        }
    }

    public int[] rand(int n) {

        if (a == null) {
            initRand();
        }

        // generate sample
        int[] ans = new int[n];
        for (int i = 0; i < n; i++) {
            double rU = Math.random() * p.length;

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
