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
 * The hypergeometric distribution is a discrete probability distribution that
 * describes the number of successes in a sequence of n draws from a finite
 * population without replacement, just as the binomial distribution describes
 * the number of successes for draws with replacement.
 * <p>
 * Suppose you are to draw "n" balls without replacement from an urn containing
 * "N" balls in total, "m" of which are white. The hypergeometric distribution
 * describes the distribution of the number of white balls drawn from the urn.
 * A random variable X follows the hypergeometric distribution with parameters
 * N, m and n if the probability is given by
 * <pre>
 *              <sub>m</sub>C<sub>k</sub> <sub>(N-m)</sub>C<sub>(n-k)</sub>
 * P(X = k) = ----------------
 *                   <sub>N</sub>C<sub>n</sub>
 * </pre>
 * where <sub>n</sub>C<sub>k</sub> is n choose k.
 * 
 * @author Haifeng Li
 */
public class HyperGeometricDistribution extends DiscreteDistribution {

    private int N;
    private int m;
    private int n;
    private RandomNumberGenerator rng;

    /**
     * Constructor.
     * @param N the number of total samples.
     * @param m the number of defects.
     * @param n the number of draws.
     */
    public HyperGeometricDistribution(int N, int m, int n) {
        if (N < 0) {
            throw new IllegalArgumentException("Invalid N: " + N);
        }

        if (m < 0 || m > N) {
            throw new IllegalArgumentException("Invalid m: " + m);
        }

        if (n < 0 || n > N) {
            throw new IllegalArgumentException("Invalid n: " + n);
        }

        this.N = N;
        this.m = m;
        this.n = n;
    }

    @Override
    public int npara() {
        return 3;
    }

    @Override
    public double mean() {
        return (double) m * n / N;
    }

    @Override
    public double var() {
        double r = (double) m / N;
        return n * (N - n) * r * (1 - r) / (N - 1);
    }

    @Override
    public double sd() {
        return Math.sqrt(var());
    }

    @Override
    public double entropy() {
        throw new UnsupportedOperationException("Hypergeometric distribution does not support entropy()");
    }

    @Override
    public String toString() {
        return String.format("Hypergeometric Distribution(%d, %d, %d)", N, m, n);
    }

    @Override
    public double p(int k) {
        if (k < Math.max(0, m + n - N) || k > Math.min(m, n)) {
            return 0.0;
        } else {
            return Math.exp(logp(k));
        }
    }

    @Override
    public double logp(int k) {
        if (k < Math.max(0, m + n - N) || k > Math.min(m, n)) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return Math.logChoose(m, k) + Math.logChoose(N - m, n - k) - Math.logChoose(N, n);
        }
    }

    @Override
    public double cdf(double k) {
        if (k < Math.max(0, m + n - N)) {
            return 0.0;
        } else if (k >= Math.min(m, n)) {
            return 1.0;
        }

        double p = 0.0;
        for (int i = Math.max(0, m + n - N); i <= k; i++) {
            p += p(i);
        }

        return p;
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        if (p == 0.0) {
            return Math.max(0, m+n-N);
        }

        if (p == 1.0) {
            return Math.min(m,n);
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
     * Uses inversion by chop-down search from the mode when the mean &lt; 20
     * and the patchwork-rejection method when the mean &gt; 20.
     */
    @Override
    public double rand() {
        int mm = m;
        int nn = n;

        if (mm > N / 2) {
            // invert mm
            mm = N - mm;
        }

        if (nn > N / 2) {
            // invert nn
            nn = N - nn;
        }

        if (nn > mm) {
            // swap nn and mm
            int swap = nn;
            nn = mm;
            mm = swap;
        }

        if (rng == null) {
            if ((double) nn * mm >= 20 * N) {
                // use ratio-of-uniforms method
                rng = new Patchwork(N, mm, nn);
            } else {
                // inversion method, using chop-down search from mode
                rng = new Inversion(N, mm, nn);
            }
        }

        return rng.rand();
    }

    abstract class RandomNumberGenerator {
        protected int N, m, n;
        protected int fak;
        protected int addd;

        RandomNumberGenerator(int N, int m, int n) {
            this.N = N;
            this.m = m;
            this.n = n;

            // transformations
            fak = 1; // used for undoing transformations
            addd = 0;

            if (m > N / 2) {
                // invert mm
                m = N - m;
                fak = -1;
                addd = n;
            }

            if (n > N / 2) {
                // invert nn
                n = N - n;
                addd += fak * m;
                fak = -fak;
            }

            if (n > m) {
                // swap nn and mm
                int swap = n;
                n = m;
                m = swap;
            }
        }

        public int rand() {
            // cases with only one possible result end here
            if (n == 0) {
                return addd;
            }

            int x = random();

            // undo transformations
            return x * fak + addd;
        }

        protected abstract int random();
    }

    class Patchwork extends RandomNumberGenerator {
        private int L,  k1,  k2,  k4,  k5;
        private double dl,  dr,  r1,  r2,  r4,  r5,  ll,  lr,  cPm,  f1,  f2,  f4,  f5,  p1,  p2,  p3,  p4,  p5,  p6;

        /**
         * Initialize random number generator.
         */
        Patchwork(int N, int m, int n) {
            super(N, m, n);

            double Mp, np, p, modef, U;                 // (X, Y) <-> (V, W)

            Mp = (double) (m + 1);
            np = (double) (n + 1);
            L = N - m - n;

            p = Mp / (N + 2.);
            modef = np * p;

            // approximate deviation of reflection points k2, k4 from modef - 1/2
            U = Math.sqrt(modef * (1. - p) * (1. - (n + 2.) / (N + 3.)) + 0.25);

            // mode, reflection points k2 and k4, and points k1 and k5, which
            // delimit the centre region of h(x)
            // k2 = ceil (modef - 1/2 - U),    k1 = 2*k2 - (mode - 1 + delta_ml)
            // k4 = floor(modef - 1/2 + U),    k5 = 2*k4 - (mode + 1 - delta_mr)
            int mode = (int) modef;
            k2 = (int) Math.ceil(modef - 0.5 - U);
            if (k2 >= mode) {
                k2 = mode - 1;
            }
            k4 = (int) (modef - 0.5 + U);
            k1 = k2 + k2 - mode + 1;                         // delta_ml = 0
            k5 = k4 + k4 - mode;                             // delta_mr = 1

            // range width of the critical left and right centre region
            dl = (double) (k2 - k1);
            dr = (double) (k5 - k4);

            // recurrence constants r(k) = p(k)/p(k-1) at k = k1, k2, k4+1, k5+1
            r1 = (np / (double) k1 - 1.) * (Mp - k1) / (double) (L + k1);
            r2 = (np / (double) k2 - 1.) * (Mp - k2) / (double) (L + k2);
            r4 = (np / (double) (k4 + 1) - 1.) * (m - k4) / (double) (L + k4 + 1);
            r5 = (np / (double) (k5 + 1) - 1.) * (m - k5) / (double) (L + k5 + 1);

            // reciprocal values of the scale parameters of expon. tail envelopes
            ll = Math.log(r1);                                     // expon. tail left
            lr = -Math.log(r5);                                     // expon. tail right

            // hypergeom. constant, necessary for computing function values f(k)
            cPm = lnpk(mode, L, m, n);

            // function values f(k) = p(k)/p(mode)  at  k = k2, k4, k1, k5
            f2 = Math.exp(cPm - lnpk(k2, L, m, n));
            f4 = Math.exp(cPm - lnpk(k4, L, m, n));
            f1 = Math.exp(cPm - lnpk(k1, L, m, n));
            f5 = Math.exp(cPm - lnpk(k5, L, m, n));

            // area of the two centre and the two exponential tail regions
            // area of the two immediate acceptance regions between k2, k4
            p1 = f2 * (dl + 1.);                               // immed. left
            p2 = f2 * dl + p1;                        // centre left
            p3 = f4 * (dr + 1.) + p2;                        // immed. right
            p4 = f4 * dr + p3;                        // centre right
            p5 = f1 / ll + p4;                        // expon. tail left
            p6 = f5 / lr + p5;                        // expon. tail right
        }

        /**
         * This method is valid only for mode &ge; 10 and 0 &le; nn &le; mm <&le; N/2.
         * <p>
         * This method is fast when called repeatedly with the same parameters, but
         * slow when the parameters change due to a high setup time. The computation
         * time hardly depends on the parameters, except that it matters a lot whether
         * parameters are within the range where the LnFac function is tabulated.
         * <p>
         * Uses the Patchwork Rejection method of Heinz Zechner (HPRS).
         * The area below the histogram function f(x) in its body is rearranged by
         * two point reflections. Within a large center interval variates are sampled
         * efficiently by rejection from uniform hats. Rectangular immediate acceptance
         * regions speed up the generation. The remaining tails are covered by
         * exponential functions.
         * <p>
         * For detailed explanation, see:
         * Stadlober, E & Zechner, H: "The Patchwork Rejection Technique for
         * Sampling from Unimodal Distributions". ACM Transactions on Modeling
         * and Computer Simulation, vol. 9, no. 1, 1999, p. 59-83.
         */
        @Override
        protected int random() {

            int Dk, X, V;
            double U, Y, W;                 // (X, Y) <-> (V, W)

            while (true) {
                // generate uniform number U -- U(0, p6)
                // case distinction corresponding to U
                if ((U = Math.random() * p6) < p2) {                  // centre left

                    // immediate acceptance region R2 = [k2, mode) *[0, f2),  X = k2, ... mode -1
                    if ((W = U - p1) < 0.) {
                        return (k2 + (int) (U / f2));
                    }
                    // immediate acceptance region R1 = [k1, k2)*[0, f1),  X = k1, ... k2-1
                    if ((Y = W / dl) < f1) {
                        return (k1 + (int) (W / f1));
                    }

                    // computation of candidate X < k2, and its reflected counterpart V > k2
                    // either squeeze-acceptance of X or acceptance-rejection of V
                    Dk = (int) (dl * Math.random()) + 1;
                    if (Y <= f2 - Dk * (f2 - f2 / r2)) {         // quick accept of
                        return (k2 - Dk);
                    }                              // X = k2 - Dk

                    if ((W = f2 + f2 - Y) < 1.) {                  // quick reject of V
                        V = k2 + Dk;
                        if (W <= f2 + Dk * (1. - f2) / (dl + 1.)) {  // quick accept of V
                            return (V);
                        }
                        if (Math.log(W) <= cPm - lnpk(V, L, m, n)) {
                            return (V);                                   // final accept of V
                        }
                    }
                    X = k2 - Dk;                                    // go to final accept/reject
                } else if (U < p4) {                                 // centre right

                    // immediate acceptance region R3 = [mode, k4+1)*[0, f4), X = mode, ... k4
                    if ((W = U - p3) < 0.) {
                        return (k4 - (int) ((U - p2) / f4));
                    }

                    // immediate acceptance region R4 = [k4+1, k5+1)*[0, f5)
                    if ((Y = W / dr) < f5) {
                        return (k5 - (int) (W / f5));
                    }

                    // computation of candidate X > k4, and its reflected counterpart V < k4
                    // either squeeze-acceptance of X or acceptance-rejection of V
                    Dk = (int) (dr * Math.random()) + 1;
                    if (Y <= f4 - Dk * (f4 - f4 * r4)) {         // quick accept of
                        return (k4 + Dk);                              // X = k4 + Dk
                    }
                    if ((W = f4 + f4 - Y) < 1.) {                  // quick reject of V
                        V = k4 - Dk;
                        if (W <= f4 + Dk * (1. - f4) / dr) {         // quick accept of
                            return V;                                    // V = k4 - Dk
                        }

                        if (Math.log(W) <= cPm - lnpk(V, L, m, n)) {
                            return (V);                                   // final accept of V
                        }
                    }
                    X = k4 + Dk;                                    // go to final accept/reject
                } else {
                    Y = Math.random();
                    if (U < p5) {                                    // expon. tail left
                        Dk = (int) (1. - Math.log(Y) / ll);
                        if ((X = k1 - Dk) < 0) {
                            continue;              // 0 <= X <= k1 - 1
                        }
                        Y *= (U - p4) * ll;                          // Y -- U(0, h(x))
                        if (Y <= f1 - Dk * (f1 - f1 / r1)) {
                            return X;                                   // quick accept of X
                        }
                    } else {                                             // expon. tail right
                        Dk = (int) (1. - Math.log(Y) / lr);
                        if ((X = k5 + Dk) > n) {
                            continue;             // k5 + 1 <= X <= nn
                        }
                        Y *= (U - p5) * lr;                          // Y -- U(0, h(x))
                        if (Y <= f5 - Dk * (f5 - f5 * r5)) {
                            return X;                                  // quick accept of X
                        }
                    }
                }

                // acceptance-rejection test of candidate X from the original area
                // test, whether  Y <= f(X),    with  Y = U*h(x)  and  U -- U(0, 1)
                // log f(X) = log( mode! (mm - mode)! (nn - mode)! (N - mm - nn + mode)! )
                //          - log( X! (mm - X)! (nn - X)! (N - mm - nn + X)! )
                if (Math.log(Y) <= cPm - lnpk(X, L, m, n)) {
                    return (X);
                }
            }
        }

        /**
         *  subfunction used by random number generator.
         */
        private double lnpk(int k, int L, int m, int n) {
            return Math.logFactorial(k) + Math.logFactorial(m - k) + Math.logFactorial(n - k) + Math.logFactorial(L + k);
        }
    }

    class Inversion extends RandomNumberGenerator {

        private int mode,  mp;                              // Mode, mode+1
        private int bound;                                      // Safety upper bound
        private double fm;                                      // Value at mode

        /**
         * Initialize random number generator.
         */
        Inversion(int N, int m, int n) {
            super(N, m, n);

            int L = N - m - n;        // Parameter
            double Mp = m + 1;
            double np = n + 1;

            double p = Mp / (N + 2.);
            double modef = np * p;                 // mode, real
            mode = (int) modef;            // mode, integer
            if (mode == modef && p == 0.5) {
                mp = mode--;
            } else {
                mp = mode + 1;
            }

            // mode probability, using log factorial function
            // (may read directly from fac_table if N < FAK_LEN)
            fm = Math.exp(Math.logFactorial(N - m) - Math.logFactorial(L + mode) - Math.logFactorial(n - mode) + Math.logFactorial(m) - Math.logFactorial(m - mode) - Math.logFactorial(mode) - Math.logFactorial(N) + Math.logFactorial(N - n) + Math.logFactorial(n));

            // safety bound - guarantees at least 17 significant decimal digits
            // bound = min(nn, (int)(modef + k*c'))
            bound = (int) (modef + 11. * Math.sqrt(modef * (1. - p) * (1. - n / (double) N) + 1.));
            if (bound > n) {
                bound = n;
            }
        }

        /**
         * Hypergeometric distribution by inversion method, using down-up
         * search starting at the mode using the chop-down technique.
         * <p>
         * Assumes 0 &le; n &le; m &le; N/2.
         * Overflow protection is needed when N > 680 or n > 75.
         * <p>
         * This method is faster than the rejection method when the variance is low.
         */
        @Override
        protected int random() {

            // Sampling
            int I;                    // Loop counter
            int L = N - m - n;        // Parameter
            double Mp, np;               // mm + 1, nn + 1
            double U;                    // uniform random
            double c, d;                 // factors in iteration
            double divisor;              // divisor, eliminated by scaling
            double k1, k2;               // float version of loop counter
            double L1 = L;               // float version of L

            Mp = (double) (m + 1);
            np = (double) (n + 1);

            // loop until accepted
            while (true) {
                U = Math.random();                    // uniform random number to be converted

                // start chop-down search at mode
                if ((U -= fm) <= 0.) {
                    return (mode);
                }
                c = d = fm;

                // alternating down- and upward search from the mode
                k1 = mp - 1;
                k2 = mode + 1;
                for (I = 1; I <= mode; I++, k1--, k2++) {
                    // Downward search from k1 = hyp_mp - 1
                    divisor = (np - k1) * (Mp - k1);
                    // Instead of dividing c with divisor, we multiply U and d because
                    // multiplication is faster. This will give overflow if N > 800
                    U *= divisor;
                    d *= divisor;
                    c *= k1 * (L1 + k1);
                    if ((U -= c) <= 0.) {
                        return (mp - I - 1); // = k1 - 1
                    }
                    // Upward search from k2 = hyp_mode + 1
                    divisor = k2 * (L1 + k2);
                    // re-scale parameters to avoid time-consuming division
                    U *= divisor;
                    c *= divisor;
                    d *= (np - k2) * (Mp - k2);
                    if ((U -= d) <= 0.) {
                        return (mode + I);  // = k2
                    }         // Values of nn > 75 or N > 680 may give overflow if you leave out this..

                // overflow protection
                // if (U > 1.E100) {U *= 1.E-100; c *= 1.E-100; d *= 1.E-100;}
                }

                // Upward search from k2 = 2*mode + 1 to bound
                for (k2 = I = mp + mode; I <= bound; I++, k2++) {
                    divisor = k2 * (L1 + k2);
                    U *= divisor;
                    d *= (np - k2) * (Mp - k2);
                    if ((U -= d) <= 0.) {
                        return (I);
                    }

                // more overflow protection
                // if (U > 1.E100) {U *= 1.E-100; d *= 1.E-100;}
                }
            }
        }
    }
}
