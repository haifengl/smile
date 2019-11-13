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

import smile.math.MathEx;

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
    private static final long serialVersionUID = 2L;

    /** The probability of success. */
    public final double p;
    private double entropy;
    /**
     * The exponential distribution to generate Geometric distributed
     * random number.
     */
    private ExponentialDistribution expDist;

    /**
     * Constructor.
     * @param p the probability of success.
     */
    public ShiftedGeometricDistribution(double p) {
        if (p <= 0 || p > 1) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        this.p = p;
        entropy = (-p * MathEx.log2(p) - (1 - p) * MathEx.log2(1 - p)) / p;
    }

    /**
     * Estimates the distribution parameters by MLE.
     */
    public static ShiftedGeometricDistribution fit(int[] data) {
        double sum = 0.0;
        for (int x : data) {
            if (x < 0) {
                throw new IllegalArgumentException("Invalid value " + x);
            }

            sum += x + 1;
        }

        double p = data.length / sum;
        return new ShiftedGeometricDistribution(p);
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public double mean() {
        return 1 / p;
    }

    @Override
    public double variance() {
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

        return new DiscreteMixture.Component(alpha, new GeometricDistribution(1 / (1 + mean)));
    }
}
