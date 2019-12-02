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
import smile.math.special.Beta;

import static java.lang.Math.*;
import static smile.math.MathEx.lfactorial;

/**
 * The binomial distribution is the discrete probability distribution of
 * the number of successes in a sequence of n independent yes/no experiments,
 * each of which yields success with probability p. Such a success/failure
 * experiment is also called a Bernoulli experiment or Bernoulli trial.
 * In fact, when n = 1, the binomial distribution is a Bernoulli distribution.
 * The probability of getting exactly k successes in n trials is given by the
 * probability mass function:
 * <p>
 * Pr(K = k) = <sub>n</sub>C<sub>k</sub> p<sup>k</sup> (1-p)<sup>n-k</sup>
 * <p>
 * where <sub>n</sub>C<sub>k</sub> is n choose k.
 * <p>
 * It is frequently used to model number of successes in a sample of size
 * n from a population of size N. Since the samples are not independent
 * (this is sampling without replacement), the resulting distribution
 * is a hypergeometric distribution, not a binomial one. However, for N much
 * larger than n, the binomial distribution is a good approximation, and
 * widely used.
 * <p>
 * Binomial distribution describes the number of successes for draws with
 * replacement. In constrast, the hypergeometric distribution describes the
 * number of successes for draws without replacement.
 * <p>
 * Although Binomial distribtuion belongs to exponential family, we don't
 * implement DiscreteExponentialFamily interface here since it is impossible
 * and meaningless to estimate a mixture of Binomial distributions.
 *
 * @see HyperGeometricDistribution
 * 
 * @author Haifeng Li
 */
public class BinomialDistribution extends DiscreteDistribution {
    private static final long serialVersionUID = 2L;

    /** The probability of success. */
    public final double p;
    /** The number of experiments. */
    public final int n;
    /** The entropy. */
    private final double entropy;
    /** The random number generator. */
    private RandomNumberGenerator rng;

    /**
     * Constructor.
     * @param p the probability of success.
     * @param n the number of experiments.
     */
    public BinomialDistribution(int n, double p) {
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        if (n < 0) {
            throw new IllegalArgumentException("Invalid n: " + n);
        }

        this.n = n;
        this.p = p;

        entropy = log(2 * PI * E * n * p * (1 - p)) / 2;
    }

    @Override
    public int length() {
        return 2;
    }

    @Override
    public double mean() {
        return n * p;
    }

    @Override
    public double variance() {
        return n * p * (1 - p);
    }

    @Override
    public double sd() {
        return Math.sqrt(n * p * (1 - p));
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Binomial Distribution(%d, %.4f)", n, p);
    }

    @Override
    public double p(int k) {
        if (k < 0 || k > n) {
            return 0.0;
        } else {
            return Math.floor(0.5 + exp(lfactorial(n) - lfactorial(k) - lfactorial(n - k))) * Math.pow(p, k) * Math.pow(1.0 - p, n - k);
        }
    }

    @Override
    public double logp(int k) {
        if (k < 0 || k > n) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return lfactorial(n) - lfactorial(k)
                 - lfactorial(n - k) + k * log(p) + (n - k) * log(1.0 - p);
        }
    }

    @Override
    public double cdf(double k) {
        if (k < 0) {
            return 0.0;
        } else if (k >= n) {
            return 1.0;
        } else {
            return Beta.regularizedIncompleteBetaFunction(n - k, k + 1, 1 - p);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        if (p == 0.0) {
            return 0;
        }

        if (p == 1.0) {
            return n;
        }

        // Starting guess near peak of density.
        // Expand interval until we bracket.
        int kl, ku, inc = 1;
        int k = Math.max(0, Math.min(n, (int) (n * p)));
        if (p < cdf(k)) {
            do {
                k = Math.max(k - inc, 0);
                inc *= 2;
            } while (p < cdf(k) && k > 0);
            kl = k;
            ku = k + inc / 2;
        } else {
            do {
                k = Math.min(k + inc, n + 1);
                inc *= 2;
            } while (p > cdf(k));
            ku = k;
            kl = k - inc / 2;
        }

        return quantile(p, kl, ku);
    }

    /**
     * This function generates a random variate with the binomial distribution.
     *
     * Uses down/up search from the mode by chop-down technique for n*p &lt; 55,
     * and patchwork rejection method for n*p &ge; 55.
     *
     * For n*p &lt; 1.E-6 numerical inaccuracy is avoided by poisson approximation.
     */
    @Override
    public double rand() {
        double np = n * p;

        // Poisson approximation for extremely low np
        if (np < 1.E-6) {
            return PoissonDistribution.tinyLambdaRand(np);
        }

        boolean inv = false;                // invert
        if (p > 0.5) {                      // faster calculation by inversion
            inv = true;
        }

        if (np < 55) {
            // inversion method, using chop-down search from 0
            if (p <= 0.5) {
                rng = new ModeSearch(p);
            } else {
                rng = new ModeSearch(1.0 - p); // faster calculation by inversion
            }
        } else {
            // ratio of uniforms method
            if (p <= 0.5) {
                rng = new Patchwork(p);
            } else {
                rng = new Patchwork(1.0 - p); // faster calculation by inversion
            }
        }

        int x = rng.rand();

        if (inv) {
            x = n - x;                         // undo inversion
        }

        return x;
    }

    interface RandomNumberGenerator {

        public int rand();
    }

    class Patchwork implements RandomNumberGenerator {

        private int mode;
        private int k1, k2, k4, k5;
        private double dl, dr, r1, r2, r4, r5, ll, lr, l_pq, c_pm, f1, f2, f4, f5, p1, p2, p3, p4, p5, p6;

        public Patchwork(double p) {
            double nu = (n + 1) * p;
            double q = 1.0 - p;      // main parameters

            // approximate deviation of reflection points k2, k4 from nu - 1/2
            double W = Math.sqrt(nu * q + 0.25);

            // mode, reflection points k2 and k4, and points k1 and k5, which
            // delimit the centre region of h(x)
            mode = (int) nu;
            k2 = (int) Math.ceil(nu - 0.5 - W);
            k4 = (int) (nu - 0.5 + W);
            k1 = k2 + k2 - mode + 1;
            k5 = k4 + k4 - mode;

            // range width of the critical left and right centre region
            dl = (double) (k2 - k1);
            dr = (double) (k5 - k4);

            // recurrence constants r(k) = p(k)/p(k-1) at k = k1, k2, k4+1, k5+1
            nu = nu / q;
            p = p / q;
            r1 = nu / (double) k1 - p;    // nu = (n+1)p / q
            r2 = nu / (double) k2 - p;    //  p =      p / q
            r4 = nu / (double) (k4 + 1) - p;
            r5 = nu / (double) (k5 + 1) - p;

            // reciprocal values of the scale parameters of expon. tail envelopes
            ll = log(r1);                     // expon. tail left
            lr = -log(r5);                     // expon. tail right

            // binomial constants, necessary for computing function values f(k)
            l_pq = log(p);
            c_pm = mode * l_pq - lfactorial(mode) - lfactorial(n - mode);

            // function values f(k) = p(k)/p(mode) at k = k2, k4, k1, k5
            f2 = f(k2, n, l_pq, c_pm);
            f4 = f(k4, n, l_pq, c_pm);
            f1 = f(k1, n, l_pq, c_pm);
            f5 = f(k5, n, l_pq, c_pm);

            // area of the two centre and the two exponential tail regions
            // area of the two immediate acceptance regions between k2, k4
            p1 = f2 * (dl + 1.);               // immed. left
            p2 = f2 * dl + p1;    // centre left
            p3 = f4 * (dr + 1.) + p2;    // immed. right
            p4 = f4 * dr + p3;    // centre right
            p5 = f1 / ll + p4;    // expon. tail left
            p6 = f5 / lr + p5;    // expon. tail right
        }

        /**
         * Ppatchwork rejection method (BPRS).
         */
        @Override
        public int rand() {
            int Dk, X, Y;
            double U, V, W;

            for (;;) {
                // generate uniform number U -- U(0, p6)
                // case distinction corresponding to U

                if ((U = MathEx.random() * p6) < p2) {         // centre left
                    // immediate acceptance region R2 = [k2, mode) *[0, f2),  X = k2, ... mode -1
                    if ((V = U - p1) < 0.) {
                        return (k2 + (int) (U / f2));
                    }
                    // immediate acceptance region R1 = [k1, k2)*[0, f1),  X = k1, ... k2-1
                    if ((W = V / dl) < f1) {
                        return (k1 + (int) (V / f1));
                    }

                    // computation of candidate X < k2, and its counterpart Y > k2
                    // either squeeze-acceptance of X or acceptance-rejection of Y
                    Dk = (int) (dl * MathEx.random()) + 1;
                    if (W <= f2 - Dk * (f2 - f2 / r2)) {     // quick accept of
                        return (k2 - Dk);
                    }                                   // X = k2 - Dk
                    if ((V = f2 + f2 - W) < 1.) {                    // quick reject of Y
                        Y = k2 + Dk;
                        if (V <= f2 + Dk * (1. - f2) / (dl + 1.)) { // quick accept of
                            return (Y);
                        }                                            // Y = k2 + Dk
                        if (V <= f(Y, n, l_pq, c_pm)) {
                            return (Y);
                        }
                    } // final accept of Y
                    X = k2 - Dk;
                } else if (U < p4) {                                      // centre right
                    // immediate acceptance region R3 = [mode, k4+1)*[0, f4), X = mode, ... k4
                    if ((V = U - p3) < 0.) {
                        return (k4 - (int) ((U - p2) / f4));
                    }
                    // immediate acceptance region R4 = [k4+1, k5+1)*[0, f5)
                    if ((W = V / dr) < f5) {
                        return (k5 - (int) (V / f5));
                    }

                    // computation of candidate X > k4, and its counterpart Y < k4
                    // either squeeze-acceptance of X or acceptance-rejection of Y
                    Dk = (int) (dr * MathEx.random()) + 1;
                    if (W <= f4 - Dk * (f4 - f4 * r4)) {     // quick accept of
                        return (k4 + Dk);
                    }                                   // X = k4 + Dk
                    if ((V = f4 + f4 - W) < 1.) {                    // quick reject of Y
                        Y = k4 - Dk;
                        if (V <= f4 + Dk * (1. - f4) / dr) {       // quick accept of
                            return (Y);
                        }                                            // Y = k4 - Dk
                        if (V <= f(Y, n, l_pq, c_pm)) {
                            return (Y);       // final accept of Y
                        }
                    }
                    X = k4 + Dk;
                } else {
                    W = MathEx.random();
                    if (U < p5) {                                   // expon. tail left
                        Dk = (int) (1. - log(W) / ll);
                        if ((X = k1 - Dk) < 0) {
                            continue;              // 0 <= X <= k1 - 1
                        }
                        W *= (U - p4) * ll;                      // W -- U(0, h(x))
                        if (W <= f1 - Dk * (f1 - f1 / r1)) {
                            return X;                                       // quick accept of X
                        }
                    } else {                                               // expon. tail right
                        Dk = (int) (1. - log(W) / lr);
                        if ((X = k5 + Dk) > n) {
                            continue;             // k5 + 1 <= X <= n
                        }
                        W *= (U - p5) * lr;                      // W -- U(0, h(x))
                        if (W <= f5 - Dk * (f5 - f5 * r5)) {
                            return X;                                       // quick accept of X
                        }
                    }
                }


                // acceptance-rejection test of candidate X from the original area
                // test, whether  W <= BinomialF(k),    with  W = U*h(x)  and  U -- U(0, 1)
                // log BinomialF(X) = (X - mode)*log(p/q) - log X!(n - X)! + log mode!(n - mode)!
                if (log(W) <= X * l_pq - lfactorial(X) - lfactorial(n - X) - c_pm) {
                    return X;
                }
            }
        }

        // used by BinomialPatchwork
        private double f(int k, int n, double l_pq, double c_pm) {
            return exp(k * l_pq - lfactorial(k) - lfactorial(n - k) - c_pm);
        }
    }

    class ModeSearch implements RandomNumberGenerator {

        private int mode;                                  // mode
        private int bound;                                 // upper bound
        private double modeValue;                          // value at mode
        private double r1;

        public ModeSearch(double p) {
            double nu = (n + 1) * p;

            // safety bound guarantees at least 17 significant decimal digits
            bound = (int) (nu + 11.0 * (Math.sqrt(nu) + 1.0));
            if (bound > n) {
                bound = n;
            }

            mode = (int) nu;
            if (mode == nu && p == 0.5) {
                mode--;    // mode
            }
            r1 = p / (1.0 - p);
            modeValue = exp(lfactorial(n) - lfactorial(mode) - lfactorial(n - mode) + mode * log(p) + (n - mode) * log(1. - p));
        }

        /**
         * Uses inversion method by down-up search starting at the mode (BMDU).
         * <p>
         * Assumes p < 0.5. Gives overflow for n*p > 60.
         * <p>
         * This method is fast when n*p is low.
         */
        @Override
        public int rand() {
            int K, x;
            double U, c, d, divisor;

            while (true) {
                U = MathEx.random();
                if ((U -= modeValue) <= 0.0) {
                    return (mode);
                }
                c = d = modeValue;

                // down- and upward search from the mode
                for (K = 1; K <= mode; K++) {
                    x = mode - K;                         // downward search from mode
                    divisor = (n - x) * r1;
                    c *= x + 1;
                    U *= divisor;
                    d *= divisor;
                    if ((U -= c) <= 0.0) {
                        return x;
                    }

                    x = mode + K;                         // upward search from mode
                    divisor = x;
                    d *= (n - x + 1) * r1;
                    U *= divisor;
                    c *= divisor;
                    if ((U -= d) <= 0.0) {
                        return x;
                    }
                }

                // upward search from 2*mode + 1 to bound
                for (x = mode + mode + 1; x <= bound; x++) {
                    d *= (n - x + 1) * r1;
                    U *= x;
                    if ((U -= d) <= 0.0) {
                        return x;
                    }
                }
            }
        }
    }
}
