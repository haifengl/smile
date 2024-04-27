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

import smile.math.MathEx;
import smile.math.special.Beta;
import smile.math.special.Gamma;

import java.io.Serial;

/**
 * The beta distribution is defined on the interval [0, 1] parameterized by
 * two positive shape parameters, typically denoted by &alpha; and &beta;.
 * It is the special case of the Dirichlet distribution with only two parameters.
 * The beta distribution is used as a prior distribution for binomial
 * proportions in Bayesian analysis. In Bayesian statistics, it can be seen as
 * the posterior distribution of the parameter &alpha; of a binomial distribution
 * after observing &alpha; - 1 independent events with probability &alpha; and &beta; - 1 with
 * probability 1 - &alpha;, if the prior distribution of &alpha; was uniform.
 * If &alpha; = 1 and &beta; =1, the Beta distribution is the uniform [0, 1] distribution.
 * The probability density function of the beta distribution is
 * f(x;&alpha;,&beta;) = x<sup>&alpha;-1</sup>(1-x)<sup>&beta;-1</sup> / B(&alpha;,&beta;)
 * where B(&alpha;,&beta;) is the beta function.
 * @author Haifeng Li
 */
public class BetaDistribution implements ExponentialFamily {
    @Serial
    private static final long serialVersionUID = 2L;

    /** The shape parameter. */
    public final double alpha;
    /** The shape parameter. */
    public final double beta;
    /** The mean. */
    private final double mean;
    /** The variance. */
    private final double variance;
    /** The entropy. */
    private final double entropy;
    /** The random number generator. */
    private RejectionLogLogistic rng;

    /**
     * Constructor.
     * @param alpha shape parameter.
     * @param beta shape parameter.
     */
    public BetaDistribution(double alpha, double beta) {
        if (alpha <= 0) {
            throw new IllegalArgumentException("Invalid alpha: " + alpha);
        }

        if (beta <= 0) {
            throw new IllegalArgumentException("Invalid beta: " + beta);
        }

        this.alpha = alpha;
        this.beta = beta;

        mean = alpha / (alpha + beta);
        variance = alpha * beta / ((alpha + beta) * (alpha + beta) * (alpha + beta + 1));
        entropy = Math.log(Beta.beta(alpha, beta)) - (alpha - 1) * Gamma.digamma(alpha) - (beta - 1) * Gamma.digamma(beta) + (alpha + beta - 2) * Gamma.digamma(alpha + beta);
    }

    /**
     * Estimates the distribution parameters by the moment method.
     * @param data the samples.
     * @return the distribution.
     */
    public static BetaDistribution fit(double[] data) {
        for (double datum : data) {
            if (datum < 0 || datum > 1) {
                throw new IllegalArgumentException("Samples are not in range [0, 1].");
            }
        }

        double mean = MathEx.mean(data);
        double var = MathEx.var(data);

        double alpha = mean * (mean * (1 - mean) / var - 1);
        double beta = (1 - mean) * (mean * (1 - mean) / var - 1);
        if (alpha <= 0 || beta <= 0) {
            throw new IllegalArgumentException("Samples don't follow Beta Distribution.");
        }

        return new BetaDistribution(alpha, beta);
    }

    /**
     * Returns the shape parameter alpha.
     * @return the shape parameter alpha
     */
    public double alpha() {
        return alpha;
    }

    /**
     * Returns the shape parameter beta.
     * @return the shape parameter beta
     */
    public double beta() {
        return beta;
    }

    @Override
    public int length() {
        return 2;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double variance() {
        return variance;
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Beta Distribution(%.4f, %.4f)", alpha, beta);
    }

    @Override
    public double p(double x) {
        if (x < 0 || x > 1) {
            return 0.0;
        } else {
            return Math.pow(x, alpha - 1) * Math.pow(1 - x, beta - 1) / Beta.beta(alpha, beta);
        }
    }

    @Override
    public double logp(double x) {
        if (x < 0 || x > 1) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return (alpha - 1) * Math.log(x) + (beta - 1) * Math.log(1 - x) - Math.log(Beta.beta(alpha, beta));
        }
    }

    @Override
    public double cdf(double x) {
        if (x <= 0) {
            return 0.0;
        } else if (x >= 1) {
            return 1.0;
        } else {
            return Beta.regularizedIncompleteBetaFunction(alpha, beta, x);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        return Beta.inverseRegularizedIncompleteBetaFunction(alpha, beta, p);
    }

    @Override
    public Mixture.Component M(double[] x, double[] posteriori) {
        double weight = 0.0;
        double mu = 0.0;
        double v = 0.0;

        for (int i = 0; i < x.length; i++) {
            weight += posteriori[i];
            mu += x[i] * posteriori[i];
        }

        mu /= weight;

        for (int i = 0; i < x.length; i++) {
            double d = x[i] - mu;
            v += d * d * posteriori[i];
        }

        v = v / weight;

        double a = mu * (mu * (1 - mu) / v - 1);
        double b = (1 - mu) * (mu * (1 - mu) / v - 1);

        return new Mixture.Component(weight, new BetaDistribution(a, b));
    }

    @Override
    public double rand() {
        if (rng == null) {
            rng = new RejectionLogLogistic();
        }

        return rng.rand();
    }

    /**
     * Implements <EM>Beta</EM> random variate generators using
     * the rejection method with log-logistic envelopes.
     * The method draws the first two uniforms from the main stream
     * and uses the auxiliary stream for the remaining uniforms,
     * when more than two are needed (i.e., when rejection occurs).
     *
     */
    class RejectionLogLogistic {

        private static final int BB = 0;
        private static final int BC = 1;
        private final int method;
        private final double am;
        private final double bm;
        private final double al;
        private double alnam;
        private final double be;
        private double ga;
        private double si;
        private double rk1;
        private double rk2;

        /**
         * Creates a beta random variate generator.
         */
        public RejectionLogLogistic() {
            if (alpha > 1.0 && beta > 1.0) {
                method = BB;
                am = Math.min(alpha, beta);
                bm = Math.max(alpha, beta);
                al = am + bm;
                be = Math.sqrt((al - 2.0) / (2.0 * alpha * beta - al));
                ga = am + 1.0 / be;
            } else {
                method = BC;
                am = Math.max(alpha, beta);
                bm = Math.min(alpha, beta);
                al = am + bm;
                alnam = al * Math.log(al / am) - 1.386294361;
                be = 1.0 / bm;
                si = 1.0 + am - bm;
                rk1 = si * (0.013888889 + 0.041666667 * bm) / (am * be - 0.77777778);
                rk2 = 0.25 + (0.5 + 0.25 / si) * bm;
            }
        }

        public double rand() {
            double X;
            double u1, u2, v, w, y, z, r, s, t;
            switch (method) {
                case BB:
                    /* -X- generator code -X- */
                    while (true) {
                        /* Step 1 */
                        u1 = MathEx.random();
                        u2 = MathEx.random();
                        v = be * Math.log(u1 / (1.0 - u1));
                        w = am * Math.exp(v);
                        z = u1 * u1 * u2;
                        r = ga * v - 1.386294361;
                        s = am + r - w;

                        /* Step 2 */
                        if (s + 2.609437912 < 5.0 * z) {
                            /* Step 3 */
                            t = Math.log(z);
                            if (s < t) /* Step 4 */ {
                                if (r + al * Math.log(al / (bm + w)) < t) {
                                    continue;
                                }
                            }
                        }

                        /* Step 5 */
                        X = MathEx.equals(am, alpha) ? w / (bm + w) : bm / (bm + w);
                        break;
                    }
                    /* -X- end of generator code -X- */
                    break;

                case BC:
                    while (true) {
                        /* Step 1 */
                        u1 = MathEx.random();
                        u2 = MathEx.random();

                        if (u1 < 0.5) {
                            /* Step 2 */
                            y = u1 * u2;
                            z = u1 * y;

                            if ((0.25 * u2 - y + z) >= rk1) {
                                continue;  /* goto 1 */
                            }

                            /* Step 5 */
                            v = be * Math.log(u1 / (1.0 - u1));
                            if (v > 80.0) {
                                if (alnam < Math.log(z)) {
                                    continue;
                                }
                                X = MathEx.equals(am, alpha) ? 1.0 : 0.0;
                                break;
                            } else {
                                w = am * Math.exp(v);
                                if ((al * (Math.log(al / (bm + w)) + v) - 1.386294361) < Math.log(z)) {
                                    continue;  /* goto 1 */
                                }

                                /* Step 6_a */
                                X = !MathEx.equals(am, alpha) ? bm / (bm + w) : w / (bm + w);
                                break;
                            }
                        } else {
                            /* Step 3 */
                            z = u1 * u1 * u2;
                            if (z < 0.25) {
                                /* Step 5 */
                                v = be * Math.log(u1 / (1.0 - u1));
                                if (v > 80.0) {
                                    X = MathEx.equals(am, alpha) ? 1.0 : 0.0;
                                    break;
                                }

                                w = am * Math.exp(v);
                                X = !MathEx.equals(am, alpha) ? bm / (bm + w) : w / (bm + w);
                                break;
                            } else {
                                if (z >= rk2) {
                                    continue;
                                }
                                v = be * Math.log(u1 / (1.0 - u1));
                                if (v > 80.0) {
                                    if (alnam < Math.log(z)) {
                                        continue;
                                    }
                                    X = MathEx.equals(am, alpha) ? 1.0 : 0.0;
                                    break;
                                }
                                w = am * Math.exp(v);
                                if ((al * (Math.log(al / (bm + w)) + v) - 1.386294361) <
                                        Math.log(z)) {
                                    continue;  /* goto 1 */
                                }

                                /* Step 6_b */
                                X = !MathEx.equals(am, alpha) ? bm / (bm + w) : w / (bm + w);
                                break;
                            }
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException();
            }

            return X;
        }
    }
}

