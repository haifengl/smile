/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.stat.distribution;

import smile.math.MathEx;

import java.io.Serializable;

/**
 * Probability distribution of univariate random variable. A probability
 * distribution identifies either the probability of each value of a random
 * variable (when the variable is discrete), or the probability of the value
 * falling within a particular interval (when the variable is continuous).
 * When the random variable takes values in the set of real numbers, the
 * probability distribution is completely described by the cumulative
 * distribution function, whose value at each real x is the probability
 * that the random variable is smaller than or equal to x.
 * <p>
 * Both rejection and inverse transform sampling methods are implemented to
 * provide some general approaches to generate random samples based on
 * probability density function or quantile function. Besides, a quantile
 * function is also provided based on bisection searching.
 *
 * @see MultivariateDistribution
 *
 * @author Haifeng Li
 */
public interface Distribution extends Serializable {

    /**
     * Returns the number of parameters of the distribution.
     * The "length" is in the sense of the minimum description
     * length principle.
     * @return The number of parameters.
     */
    int length();

    /**
     * Returns the mean of distribution.
     * @return The mean.
     */
    double mean();
    
    /**
     * Returns the variance of distribution.
     * @return The variance.
     */
    double variance();
    
    /**
     * Returns the standard deviation of distribution.
     * @return The standard deviation.
     */
    default double sd() {
        return Math.sqrt(variance());
    }

    /**
     * Returns Shannon entropy of the distribution.
     * @return Shannon entropy.
     */
    double entropy();

    /**
     * Generates a random number following this distribution.
     * @return a random number.
     */
    double rand();

    /**
     * Generates a set of random numbers following this distribution.
     * @param n the number of random numbers to generate.
     * @return an array of random numbers.
     */
    default double[] rand(int n) {
        double[] data = new double[n];
        for (int i = 0; i < n; i++) {
            data[i] = rand();
        }
        return data;
    }

    /**
     * The probability density function for continuous distribution
     * or probability mass function for discrete distribution at x.
     * @param x a real number.
     * @return the density.
     */
    double p(double x);

    /**
     * The density at x in log scale, which may prevents the underflow problem.
     * @param x a real number.
     * @return the log density.
     */
    double logp(double x);

    /**
     * Cumulative distribution function. That is the probability to the left of x.
     * @param x a real number.
     * @return the probability.
     */
    double cdf(double x);

    /**
     * The quantile, the probability to the left of quantile is p. It is
     * actually the inverse of cdf.
     * @param p the probability.
     * @return the quantile.
     */
    double quantile(double p);

    /**
     * The likelihood of the sample set following this distribution.
     * @param x a set of samples.
     * @return the likelihood.
     */
    default double likelihood(double[] x) {
        return Math.exp(logLikelihood(x));
    }
    
    /**
     * The log likelihood of the sample set following this distribution.
     * @param x a set of samples.
     * @return the log likelihood.
     */
    default double logLikelihood(double[] x) {
        double L = 0.0;

        for (double xi : x)
            L += logp(xi);

        return L;
    }

    /**
     * Use the rejection technique to draw a sample from the given
     * distribution. <em>WARNING</em>: this simulation technique can
     * take a very long time. Rejection sampling is also commonly
     * called the acceptance-rejection method or "accept-reject algorithm".
     * It generates sampling values from an arbitrary probability distribution
     * function f(x) by using an instrumental distribution g(x), under the
     * only restriction that {@code f(x) < M g(x)} where
     * {@code M > 1} is an appropriate bound on
     * {@code f(x) / g(x)}.
     * <p>
     * Rejection sampling is usually used in cases where the form of
     * <code>f(x)</code> makes sampling difficult. Instead of sampling
     * directly from the distribution <code>f(x)</code>, we use an envelope
     * distribution <code>M g(x)</code> where sampling is easier. These
     * samples from <code>M g(x)</code> are probabilistically
     * accepted or rejected.
     * <p>
     * This method relates to the general field of Monte Carlo techniques,
     * including Markov chain Monte Carlo algorithms that also use a proxy
     * distribution to achieve simulation from the target distribution
     * <code>f(x)</code>. It forms the basis for algorithms such as
     * the Metropolis algorithm.
     *
     * @param pmax the scale of instrumental distribution (uniform).
     * @param xmin the lower bound of random variable range.
     * @param xmax the upper bound of random variable range.
     * @return a random number.
     */
    default double rejectionSampling(double pmax, double xmin, double xmax) {
        double x;
        double y;
        do {
            x = xmin + MathEx.random() * (xmax - xmin);
            y = MathEx.random() * pmax;
        } while (p(x) < y);

        return x;
    }

    /**
     * Use inverse transform sampling (also known as the inverse probability
     * integral transform or inverse transformation method or Smirnov transform)
     * to draw a sample from the given distribution. This is a method for
     * generating sample numbers at random from any probability distribution
     * given its cumulative distribution function (cdf). Subject to the
     * restriction that the distribution is continuous, this method is
     * generally applicable (and can be computationally efficient if the
     * cdf can be analytically inverted), but may be too computationally
     * expensive in practice for some probability distributions. The
     * Box-Muller transform is an example of an algorithm which is
     * less general but more computationally efficient. It is often the
     * case that, even for simple distributions, the inverse transform
     * sampling method can be improved on, given substantial research
     * effort, e.g. the ziggurat algorithm and rejection sampling.
     *
     * @return a random number.
     */
    default double inverseTransformSampling() {
        double u = MathEx.random();
        return quantile(u);
    }

    /**
     * Inversion of CDF by bisection numeric root finding of "cdf(x) = p"
     * for continuous distribution.
     *
     * @param p the probability.
     * @param xmin the lower bound of search range.
     * @param xmax the upper bound of search range.
     * @param eps the epsilon close to zero.
     * @return the quantile.
     */
    default double quantile(double p, double xmin, double xmax, double eps) {
        if (eps <= 0.0) {
            throw new IllegalArgumentException("Invalid epsilon: " + eps);
        }

        while (Math.abs(xmax - xmin) > eps) {
            double xmed = (xmax + xmin) / 2;
            if (cdf(xmed) > p) {
                xmax = xmed;
            } else {
                xmin = xmed;
            }
        }

        return xmin;
    }

    /**
     * Inversion of CDF by bisection numeric root finding of "cdf(x) = p"
     * for continuous distribution. The default epsilon is 1E-6.
     * @param p the probability.
     * @param xmin the lower bound of search range.
     * @param xmax the upper bound of search range.
     * @return the quantile.
     */
    default double quantile(double p, double xmin, double xmax) {
        return quantile(p, xmin, xmax, 1.0E-6);
    }
}
