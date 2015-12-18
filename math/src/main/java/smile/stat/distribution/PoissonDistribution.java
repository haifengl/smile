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

import smile.math.special.Gamma;
import smile.math.Math;

/**
 * Poisson distribution expresses the probability of a number of events
 * occurring in a fixed period of time if these events occur with a known
 * average rate and independently of the time since the last event. The Poisson
 * distribution can also be used for the number of events in other specified
 * intervals such as distance, area or volume. If the expected number of
 * occurrences in this interval is &lambda;, then the probability that there
 * are exactly n occurrences (n = 0, 1, 2, ...) is equal to
 * <pre>
 *             &lambda;<sup>n</sup> e<sup>-&lambda;</sup>
 * f(n; &lambda;) = ---------
 *               n!
 * </pre>
 * For sufficiently large values of &lambda;, (say &lambda; &gt; 1000), the normal
 * distribution with mean &lambda; and variance &lambda;, is an excellent
 * approximation to the Poisson distribution. If &lambda; is greater than about
 * 10, then the normal distribution is a good approximation if an appropriate
 * continuity correction is performed, i.e., P(X &le; x), where (lower-case) x
 * is a non-negative integer, is replaced by P(X &le; x + 0.5).
 * <p>
 * When a variable is Poisson distributed, its square root is approximately
 * normally distributed with expected value of about &lambda;<sup>1/2</sup>
 * and variance of about 1/4.
 *
 * @author Haifeng Li
 */
public class PoissonDistribution extends DiscreteDistribution implements DiscreteExponentialFamily {

    private double lambda;
    private double entropy;
    private RandomNumberGenerator rng;

    /**
     * Constructor.
     */
    public PoissonDistribution(double lambda) {
        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid lambda: " + lambda);
        }

        this.lambda = lambda;
        entropy = (Math.log(2 * Math.PI * Math.E) + Math.log(lambda)) / 2 - 1 / (12 * lambda) - 1 / (24 * lambda * lambda) - 19 / (360 * lambda * lambda * lambda);
    }

    /**
     * Constructor. Parameter will be estimated from the data by MLE.
     */
    public PoissonDistribution(int[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < 0) {
                throw new IllegalArgumentException("Samples contain negative values.");
            }
        }

        lambda = Math.mean(data);
        entropy = (Math.log(2 * Math.PI * Math.E) + Math.log(lambda)) / 2 - 1 / (12 * lambda) - 1 / (24 * lambda * lambda) - 19 / (360 * lambda * lambda * lambda);
    }

    /**
     * Returns the rate parameter, the expected number of occurrences in a time unit.
     */
    public double getLambda() {
        return lambda;
    }

    @Override
    public int npara() {
        return 1;
    }

    @Override
    public double mean() {
        return lambda;
    }

    @Override
    public double var() {
        return lambda;
    }

    @Override
    public double sd() {
        return Math.sqrt(lambda);
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Poisson Distribution(%.4f)", lambda);
    }

    @Override
    public double p(int k) {
        if (k < 0) {
            return 0.0;
        } else {
            return Math.pow(lambda, k) * Math.exp(-lambda) / Math.factorial(k);
        }
    }

    @Override
    public double logp(int k) {
        if (k < 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return k * Math.log(lambda) - lambda - Math.logFactorial(k);
        }
    }

    @Override
    public double cdf(double k) {
        if (lambda == 0) {
            if (k >= 0) {
                return 1.0;
            } else {
                return 0.0;
            }
        }

        if (k < 0) {
            return 0.0;
        } else {
            return Gamma.regularizedUpperIncompleteGamma(Math.floor(k + 1), lambda);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        if (p < Math.exp(-lambda)) {
            return 0;
        }

        int n = (int) Math.max(Math.sqrt(lambda), 5.0);
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

        DiscreteMixture.Component c = new DiscreteMixture.Component();
        c.priori = alpha;
        c.distribution = new PoissonDistribution(mean);

        return c;
    }

    /**
     * This function generates a random variate with the poisson distribution.
     * <p>
     * Uses down/up search from the mode by chop-down technique for &lambda; &lt; 20,
     * and patchwork rejection method for &lambda; &ge; 20.
     * <p>
     * For &lambda; &lt; 1.E-6 numerical inaccuracy is avoided by direct calculation.
     */
    @Override
    public double rand() {
        if (lambda > 2E9) {
            throw new IllegalArgumentException("Too large lambda for random number generator.");
        }

        if (lambda == 0) {
            return 0;
        }

        // For extremely small L we calculate the probabilities of x = 1
        // and x = 2 (ignoring higher x). The reason for using this
        // method is to prevent numerical inaccuracies in other methods.
        if (lambda < 1.E-6) {
            return tinyLambdaRand(lambda);
        }

        if (rng == null) {
            if (lambda < 20) {
                // down/up search from mode
                // The computation time for this method grows with sqrt(L).
                // Gives overflow for L > 60
                rng = new ModeSearch();
            } else {
                // patchword rejection method
                // The computation time for this method does not depend on L.
                // Use where other methods would be slower.
                rng = new Patchwork();
            }
        }

        return rng.rand();
    }

    interface RandomNumberGenerator {

        public int rand();
    }

    class ModeSearch implements RandomNumberGenerator {

        /**
         * value at x=0 or at mode
         */
        private double f0Mode;
        /**
         * upper bound
         */
        private int upperBound;

        /**
         * Initialize the Poisson random number generator.
         */
        ModeSearch() {
            int mode = (int) lambda;

            // safety-bound
            upperBound = (int) Math.floor(lambda + 0.5 + 7.0 * (Math.sqrt(lambda + lambda + 1.) + 1.5));

            // probability of x=mode
            f0Mode = Math.exp(mode * Math.log(lambda) - lambda - Math.logFactorial(mode));
        }

        /**
         * This method is based on down/up search from the mode, using the
         * chop-down technique (PMDU).
         * <p>
         * Execution time grows with sqrt(L). Gives overflow for L > 60.
         */
        @Override
        public int rand() {
            double r, c, d;
            int x, mode;

            mode = (int) lambda;

            while (true) {
                r = Math.random();
                if ((r -= f0Mode) <= 0) {
                    return mode;
                }
                c = d = f0Mode;

                // alternating down/up search from the mode
                for (int i = 1; i <= mode; i++) {
                    // down
                    x = mode - i;
                    c *= x + 1;
                    r *= lambda;
                    d *= lambda;
                    if ((r -= c) <= 0) {
                        return x;
                    }
                    // up
                    x = mode + i;
                    d *= lambda;
                    r *= x;
                    c *= x;
                    if ((r -= d) <= 0) {
                        return x;
                    }
                }
                // continue upward search from 2*mode+1 to bound
                for (x = mode + mode + 1; x <= upperBound; x++) {
                    d *= lambda;
                    r *= x;
                    if ((r -= d) <= 0) {
                        return x;
                    }
                }
            }
        }
    }

    class Patchwork implements RandomNumberGenerator {

        private int k1,  k2,  k4,  k5;
        private double dl,  dr,  r1,  r2,  r4,  r5,  ll,  rr,  l_my,  c_pm,  f1,  f2,  f4,  f5,  p1,  p2,  p3,  p4,  p5,  p6;

        /**
         * Initialize the Poisson random number generator.
         */
        Patchwork() {
            int mode = (int) lambda;

            // approximate deviation of reflection points k2, k4 from L - 1/2
            double Ds = Math.sqrt(lambda + 0.25);

            // mode, reflection points k2 and k4, and points k1 and k5, which
            // delimit the centre region of h(x)
            k2 = (int) Math.ceil(lambda - 0.5 - Ds);
            k4 = (int) (lambda - 0.5 + Ds);
            k1 = k2 + k2 - mode + 1;
            k5 = k4 + k4 - mode;

            // range width of the critical left and right centre region
            dl = (double) (k2 - k1);
            dr = (double) (k5 - k4);

            // recurrence constants r(k) = p(k)/p(k-1) at k = k1, k2, k4+1, k5+1
            r1 = lambda / (double) k1;
            r2 = lambda / (double) k2;
            r4 = lambda / (double) (k4 + 1);
            r5 = lambda / (double) (k5 + 1);

            // reciprocal values of the scale parameters of expon. tail envelopes
            ll = Math.log(r1);                                     // expon. tail left
            rr = -Math.log(r5);                                     // expon. tail right

            // Poisson constants, necessary for computing function values f(k)
            l_my = Math.log(lambda);
            c_pm = mode * l_my - Math.logFactorial(mode);

            // function values f(k) = p(k)/p(mode) at k = k2, k4, k1, k5
            f2 = f(k2, l_my, c_pm);
            f4 = f(k4, l_my, c_pm);
            f1 = f(k1, l_my, c_pm);
            f5 = f(k5, l_my, c_pm);

            // area of the two centre and the two exponential tail regions
            // area of the two immediate acceptance regions between k2, k4
            p1 = f2 * (dl + 1.);                    // immed. left
            p2 = f2 * dl + p1;         // centre left
            p3 = f4 * (dr + 1.) + p2;          // immed. right
            p4 = f4 * dr + p3;         // centre right
            p5 = f1 / ll + p4;         // expon. tail left
            p6 = f5 / rr + p5;         // expon. tail right
        }

        /**
         * This method is based on Patchwork Rejection method (PPRS):
         * The area below the histogram function f(x) is rearranged in
         * its body by two point reflections. Within a large center
         * interval variates are sampled efficiently by rejection from
         * uniform hats. Rectangular immediate acceptance regions speed
         * up the generation. The remaining tails are covered by
         * exponential functions.
         * <p>
         * For detailed explanation, see:
         * Stadlober, E & Zechner, H: "The Patchwork Rejection Technique for
         * Sampling from Unimodal Distributions". ACM Transactions on Modeling
         * and Computer Simulation, vol. 9, no. 1, 1999, p. 59-83.
         * <p>
         * This method is valid for &lambda; >= 10.
         * <p>
         * The computation time hardly depends on &lambda;, except that it matters
         * a lot whether &lambda; is within the range where the LnFac function is
         * tabulated.
         */
        @Override
        public int rand() {
            int Dk, X, Y;
            double U, V, W;

            for (;;) {
                // generate uniform number U -- U(0, p6)
                // case distinction corresponding to U
                if ((U = Math.random() * p6) < p2) {              // centre left

                    // immediate acceptance region R2 = [k2, mode) *[0, f2),  X = k2, ... mode -1
                    if ((V = U - p1) < 0.0) {
                        return (k2 + (int) (U / f2));
                    }
                    // immediate acceptance region R1 = [k1, k2)*[0, f1),  X = k1, ... k2-1
                    if ((W = V / dl) < f1) {
                        return (k1 + (int) (V / f1));
                    }

                    // computation of candidate X < k2, and its counterpart Y > k2
                    // either squeeze-acceptance of X or acceptance-rejection of Y
                    Dk = (int) (dl * Math.random()) + 1;
                    if (W <= f2 - Dk * (f2 - f2 / r2)) {           // quick accept of
                        return (k2 - Dk);                                // X = k2 - Dk
                    }

                    if ((V = f2 + f2 - W) < 1.0) {                   // quick reject of Y
                        Y = k2 + Dk;
                        if (V <= f2 + Dk * (1.0 - f2) / (dl + 1.0)) {  // quick accept of
                            return Y;                                      // Y = k2 + Dk
                        }
                        if (V <= f(Y, l_my, c_pm)) {
                            return Y;  // final accept of Y
                        }
                    }
                    X = k2 - Dk;
                } else if (U < p4) {                                   // centre right
                    //  immediate acceptance region R3 = [mode, k4+1)*[0, f4), X = mode, ... k4
                    if ((V = U - p3) < 0.) {
                        return (k4 - (int) ((U - p2) / f4));
                    }
                    // immediate acceptance region R4 = [k4+1, k5+1)*[0, f5)
                    if ((W = V / dr) < f5) {
                        return (k5 - (int) (V / f5));
                    }

                    // computation of candidate X > k4, and its counterpart Y < k4
                    // either squeeze-acceptance of X or acceptance-rejection of Y
                    Dk = (int) (dr * Math.random()) + 1;
                    if (W <= f4 - Dk * (f4 - f4 * r4)) {           // quick accept of
                        return (k4 + Dk);                                // X = k4 + Dk
                    }
                    if ((V = f4 + f4 - W) < 1.0) {                   // quick reject of Y
                        Y = k4 - Dk;
                        if (V <= f4 + Dk * (1.0 - f4) / dr) {         // quick accept of
                            return Y;                                      // Y = k4 - Dk
                        }
                        if (V <= f(Y, l_my, c_pm)) {
                            return Y; // final accept of Y
                        }
                    }
                    X = k4 + Dk;
                } else {
                    W = Math.random();
                    if (U < p5) {                                      // expon. tail left
                        Dk = (int) (1.0 - Math.log(W) / ll);
                        if ((X = k1 - Dk) < 0L) {
                            continue;               // 0 <= X <= k1 - 1
                        }
                        W *= (U - p4) * ll;                            // W -- U(0, h(x))
                        if (W <= f1 - Dk * (f1 - f1 / r1)) {
                            return X;   // quick accept of X
                        }
                    } else {                                               // expon. tail right
                        Dk = (int) (1.0 - Math.log(W) / rr);
                        X = k5 + Dk;                                    // X >= k5 + 1
                        W *= (U - p5) * rr;                            // W -- U(0, h(x))
                        if (W <= f5 - Dk * (f5 - f5 * r5)) {
                            return X;  // quick accept of X
                        }
                    }
                }

                // acceptance-rejection test of candidate X from the original area
                // test, whether  W <= f(k),    with  W = U*h(x)  and  U -- U(0, 1)
                // log f(X) = (X - mode)*log(L) - log X! + log mode!
                if (Math.log(W) <= X * l_my - Math.logFactorial(X) - c_pm) {
                    return X;
                }
            }
        }

        /**
         * used by Patchwork
         */
        private double f(int k, double l_nu, double c_pm) {
            return Math.exp(k * l_nu - Math.logFactorial(k) - c_pm);
        }
    }

    /**
     * For extremely low values of lambda, the method is a simple
     * calculation of the probabilities of x = 1 and x = 2. Higher values
     * are ignored.
     * <p>
     * The reason for using this method is to avoid the numerical inaccuracies
     * in other methods.
     */
    static int tinyLambdaRand(double lambda) {
        double d, r;
        d = Math.sqrt(lambda);
        if (Math.random() >= d) {
            return 0;
        }

        r = Math.random() * d;
        if (r > lambda * (1.0 - lambda)) {
            return 0;
        }

        if (r > 0.5 * lambda * lambda * (1. - lambda)) {
            return 1;
        }

        return 2;
    }
}
