/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.stat.distribution;

import java.io.Serial;

/**
 * The geometric distribution is a discrete probability distribution of the
 * number X of Bernoulli trials needed to get one success, supported on the set
 * <code>{1, 2, 3, &hellip;}</code>. Sometimes, people define that the probability
 * distribution of the number <code>Y = X - 1</code> of failures before the first
 * success, supported on the set <code>{0, 1, 2, 3, &hellip;}</code>. To reduce
 * the confusion, we denote the latter as shifted geometric distribution.
 * If the probability of success on each trial is p, then the probability that
 * the k-<i>th</i> trial (out of k trials) is the first success is
 * Pr(X = k) = (1 - p)<sup>k-1</sup> p.
 * <p>
 * Like its continuous analogue (the exponential distribution), the geometric
 * distribution is memoryless. That means that if you intend to repeat an
 * experiment until the first success, then, given that the first success has
 * not yet occurred, the conditional probability distribution of the number
 * of additional trials does not depend on how many failures have been
 * observed. The geometric distribution is in fact the only memoryless
 * discrete distribution.
 * <p>
 * Among all discrete probability distributions supported on
 * <code>{1, 2, 3, &hellip;}</code> with given expected value &mu;,
 * the geometric distribution X with parameter
 * <code>p = 1/&mu;</code> is the one with the largest entropy.

 * @see ShiftedGeometricDistribution
 *
 * @author Haifeng Li
 */
public class GeometricDistribution extends DiscreteDistribution implements DiscreteExponentialFamily {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Probability of success on each trial.
     */
    public final double p;
    /**
     * The exponential distribution to generate Geometric distributed
     * random number.
     */
    private ExponentialDistribution expDist;

    /**
     * Constructor.
     * @param p the probability of success.
     */
    public GeometricDistribution(double p) {
        if (p <= 0 || p > 1) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        this.p = p;
    }

    /**
     * Estimates the distribution parameters by MLE.
     * @param data the training data.
     * @return the distribution.
     */
    public static GeometricDistribution fit(int[] data) {
        double sum = 0.0;
        for (int x : data) {
            if (x <= 0) {
                throw new IllegalArgumentException("Invalid value " + x);
            }

            sum += x;
        }

        double p = data.length / sum;
        return new GeometricDistribution(p);
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public double mean() {
        return (1 - p) / p;
    }

    @Override
    public double variance() {
        return (1 - p) / (p * p);
    }

    @Override
    public double sd() {
        return Math.sqrt(1 - p) / p;
    }

    /**
     * Shannon's entropy. Not supported.
     */
    @Override
    public double entropy() {
        throw new UnsupportedOperationException("Geometric distribution does not support entropy()");
    }

    @Override
    public String toString() {
        return String.format("Geometric Distribution(%.4f)", p);
    }

    @Override
    public double rand() {
        if (expDist == null) {
            double lambda = -Math.log(1 - p);
            expDist = new ExponentialDistribution(lambda);
        }

        return Math.floor(expDist.rand()) + 1;
    }

    @Override
    public double p(int k) {
        if (k < 0) {
            return 0.0;
        } else {
            return Math.pow(1 - p, k) * p;
        }
    }

    @Override
    public double logp(int k) {
        if (k < 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return k * Math.log(1 - p) + Math.log(p);
        }
    }

    @Override
    public double cdf(double k) {
        if (k < 0) {
            return 0.0;
        } else {
            return 1 - Math.pow(1 - p, k + 1);
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
            } while (p < cdf(n) && n > 0);
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

        return new DiscreteMixture.Component(alpha, new GeometricDistribution(1 / mean));
    }
}
