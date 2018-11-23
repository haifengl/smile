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

import smile.math.Math;

/**
 * The "shifted" geometric distribution is a discrete probability distribution of the
 * number of failures before the first success, supported on the set
 * {0, 1, 2, 3, &hellip;}.
 * If the probability of success on each trial is p, then the probability that
 * the k-<i>th</i> trial (out of k trials) is the first success is
 * Pr(X = k) = (1 - p)<sup>k</sup> p.

 * @see GeometricDistribution
 *
 * @author Haifeng Li
 */
public class ShiftedGeometricDistribution extends DiscreteDistribution implements DiscreteExponentialFamily {
    private static final long serialVersionUID = 1L;

    private double p;
    private double entropy;
    /**
     * The exponential distribution to generate Geometric distributed
     * random number.
     */
    ExponentialDistribution expDist;

    /**
     * Constructor.
     * @param p the probability of success.
     */
    public ShiftedGeometricDistribution(double p) {
        if (p <= 0 || p > 1) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        this.p = p;
        entropy = (-p * Math.log2(p) - (1 - p) * Math.log2(1 - p)) / p;
    }

    /**
     * Constructor. Parameter will be estimated from the data by MLE.
     */
    public ShiftedGeometricDistribution(int[] data) {
        double sum = 0.0;
        for (int x : data) {
            if (x < 0) {
                throw new IllegalArgumentException("Invalid value " + x);
            }

            sum += x + 1;
        }

        p = data.length / sum;
        entropy = (-p * Math.log2(p) - (1 - p) * Math.log2(1 - p)) / p;
    }

    /**
     * Returns the probability of success.
     */
    public double getProb() {
        return p;
    }

    @Override
    public int npara() {
        return 1;
    }

    @Override
    public double mean() {
        return 1 / p;
    }

    @Override
    public double var() {
        return (1 - p) / (p * p);
    }

    @Override
    public double sd() {
        return Math.sqrt(1 - p) / p;
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Shifted Geometric Distribution(%.4f)", p);
    }

    @Override
    public double rand() {
        if (expDist == null) {
            double lambda = -Math.log(1 - p);
            expDist = new ExponentialDistribution(lambda);
        }

        return Math.floor(expDist.rand());
    }

    @Override
    public double p(int k) {
        if (k <= 0) {
            return 0.0;
        } else {
            return Math.pow(1 - p, k - 1) * p;
        }
    }

    @Override
    public double logp(int k) {
        if (k <= 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return (k - 1) * Math.log(1 - p) + Math.log(p);
        }
    }

    @Override
    public double cdf(double k) {
        if (k < 0) {
            return 0.0;
        } else {
            return 1 - Math.pow(1 - p, k);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        int n = (int) Math.max(Math.sqrt(1 / this.p), 5.0);
        int nl, nu, inc = 1;

        if (p < cdf(n)) {
            do {
                n = Math.max(n - inc, 0);
                inc *= 2;
            } while (p < cdf(n));
            nl = n;
            nu = n + inc / 2;
        } else {
            do {
                n += inc;
                inc *= 2;
            } while (p > cdf(n));
            nu = n;
            nl = n - inc / 2;
        }

        return quantile(p, nl, nu);
    }

    @Override
    public DiscreteMixture.Component M(int[] x, double[] posteriori) {
        double alpha = 0.0;
        double mean = 0.0;

        for (int i = 0; i < x.length; i++) {
            alpha += posteriori[i];
            mean += x[i] * posteriori[i];
        }

        mean /= alpha;

        DiscreteMixture.Component c = new DiscreteMixture.Component();
        c.priori = alpha;
        c.distribution = new GeometricDistribution(1 / (1 + mean));

        return c;
    }
}
