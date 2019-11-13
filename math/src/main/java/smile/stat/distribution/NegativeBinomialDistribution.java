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

import static smile.math.MathEx.factorial;
import static smile.math.MathEx.lfactorial;
import static smile.math.special.Beta.regularizedIncompleteBetaFunction;
import static smile.math.special.Gamma.gamma;
import static smile.math.special.Gamma.lgamma;

/**
 * Negative binomial distribution arises as the probability distribution of
 * the number of successes in a series of independent and identically distributed
 * Bernoulli trials needed to get a specified (non-random) number r of failures.
 * If r is an integer, it is usually called Pascal distribution. Otherwise, it
 * is often called Polya distribution for the real-valued case. When r = 1 we
 * get the probability distribution of number of successes before the
 * first failure, which is a geometric distribution.
 * <p>
 * An alternative definition is that X is the total number of trials needed
 * to get r failures, not simply the number of successes. This alternative
 * parameterization can be used as an alternative to the Poisson distribution.
 * It is especially useful for discrete data over an unbounded positive range
 * whose sample variance exceeds the sample mean. If a Poisson distribution is
 * used to model such data, the model mean and variance are equal. In that case,
 * the observations are overdispersed with respect to the Poisson model.
 * Since the negative binomial distribution has one more parameter than the
 * Poisson, the second parameter can be used to adjust the variance
 * independently of the mean. In the case of modest overdispersion, this may
 * produce substantially similar results to an overdispersed Poisson distribution.
 * <p>
 * The negative binomial distribution also arises as a continuous mixture
 * of Poisson distributions where the mixing distribution of the Poisson rate
 * is a gamma distribution. That is, we can view the negative binomial as a
 * Poisson(&lambda;) distribution, where &lambda; is itself a random variable,
 * distributed according to &Gamma;(r, p/(1 - p)).
 *
 * @author Haifeng Li
 */
public class NegativeBinomialDistribution extends DiscreteDistribution {
    private static final long serialVersionUID = 2L;

    /**
     * The number of failures until the experiment is stopped.
     */
    public final double r;
    /**
     * The success probability in each experiment.
     */
    public final double p;

    /**
     * Constructor.
     * @param r the number of failures until the experiment is stopped.
     * @param p success probability in each experiment.
     */
    public NegativeBinomialDistribution(double r, double p) {
        if (p <= 0 || p >= 1) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        if (r <= 0) {
            throw new IllegalArgumentException("Invalid r: " + r);
        }

        this.p = p;
        this.r = r;
    }

    @Override
    public int length() {
        return 2;
    }

    @Override
    public double mean() {
        return r * (1 - p) / p;
    }

    @Override
    public double variance() {
        return r * (1 - p) / (p * p);
    }

    @Override
    public double sd() {
        return Math.sqrt(r * (1 - p)) / p;
    }

    /**
     * Shannon entropy. Not supported.
     */
    @Override
    public double entropy() {
        throw new UnsupportedOperationException("Negative Binomial distribution does not support entropy()");
    }

    @Override
    public String toString() {
        if (r == (int) r) {
            return String.format("Negative Binomial(%d, %.4f)", r, p);
        } else {
            return String.format("Negative Binomial(%.4f, %.4f)", r, p);
        }
    }

    @Override
    public double rand() {
        return inverseTransformSampling();
    }

    @Override
    public double p(int k) {
        if (k < 0) {
            return 0.0;
        } else {
            return gamma(r + k) / (factorial(k) * gamma(r)) * Math.pow(p, r) * Math.pow(1 - p, k);
        }
    }

    @Override
    public double logp(int k) {
        if (k < 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return lgamma(r + k) - lfactorial(k) - lgamma(r) + r * Math.log(p) + k * Math.log(1 - p);
        }
    }

    @Override
    public double cdf(double k) {
        if (k < 0) {
            return 0.0;
        } else {
            return regularizedIncompleteBetaFunction(r, k + 1, p);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        // Starting guess near peak of density.
        // Expand interval until we bracket.
        int kl, ku, inc = 1;
        int k = (int) mean();
        if (p < cdf(k)) {
            do {
                k = Math.max(k - inc, 0);
                inc *= 2;
            } while (p < cdf(k) && k > 0);
            kl = k;
            ku = k + inc / 2;
        } else {
            do {
                k += inc;
                inc *= 2;
            } while (p > cdf(k));
            ku = k;
            kl = k - inc / 2;
        }

        return quantile(p, kl, ku);
    }
}
