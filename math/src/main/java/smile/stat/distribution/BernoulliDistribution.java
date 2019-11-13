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
 * Bernoulli distribution is a discrete probability distribution, which takes
 * value 1 with success probability p and value 0 with failure probability
 * q = 1 - p.
 * <p>
 * Although Bernoulli distribtuion belongs to exponential family, we don't
 * implement DiscreteExponentialFamily interface here since it is impossible
 * and meaningless to estimate a mixture of Bernoulli distributions.
 *
 * @author Haifeng Li
 */
public class BernoulliDistribution extends DiscreteDistribution {
    private static final long serialVersionUID = 2L;

    /**
     * Probability of success.
     */
    public final double p;
    /**
     * Probability of failure.
     */
    public final double q;
    /**
     * Shannon entropy.
     */
    private final double entropy;

    /**
     * Constructor.
     * @param p the probability of success.
     */
    public BernoulliDistribution(double p) {
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        this.p = p;
        q = 1 - p;

        entropy = -p * MathEx.log2(p) - q * MathEx.log2(q);
    }

    /**
     * Estimates the distribution parameters by MLE.
     * @param data data[i] == 1 if the i-<i>th</i> trail is success. Otherwise 0.
     */
    public static BernoulliDistribution fit(int[] data) {
        int k = 0;
        for (int i : data) {
            if (i == 1) {
                k++;
            } else if (i != 0) {
                throw new IllegalArgumentException("Invalid value " + i);
            }
        }

        double p = (double) k / data.length;
        return new BernoulliDistribution(p);
    }

    /**
     * Construct an Bernoulli from the given samples. Parameter
     * will be estimated from the data by MLE.
     * @param data the boolean array to indicate if the i-<i>th</i> trail success.
     */
    public BernoulliDistribution(boolean[] data) {
        int k = 0;
        for (boolean b : data) {
            if (b) {
                k++;
            }
        }

        p = (double) k / data.length;
        q = 1 - p;

        entropy = -p * MathEx.log2(p) - q * MathEx.log2(q);
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public double mean() {
        return p;
    }

    @Override
    public double variance() {
        return p * q;
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Bernoulli Distribution(%.4f)", p);
    }

    @Override
    public double rand() {
        if (MathEx.random() < q) {
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public double p(int k) {
        if (k == 0) {
            return q;
        } else if (k == 1) {
            return p;
        } else {
            return 0.0;
        }
    }

    @Override
    public double logp(int k) {
        if (k == 0) {
            return Math.log(q);
        } else if (k == 1) {
            return Math.log(p);
        } else {
            return Double.NEGATIVE_INFINITY;
        }
    }

    @Override
    public double cdf(double k) {
        if (k < 0) {
            return 0.0;
        } else if (k == 0) {
            return q;
        } else {
            return 1.0;
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        if (p <= 1 - this.p) {
            return 0;
        } else {
            return 1;
        }
    }
}
