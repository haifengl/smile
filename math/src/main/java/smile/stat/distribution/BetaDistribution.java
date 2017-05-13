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
import smile.math.special.Beta;
import smile.math.special.Gamma;

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
public class BetaDistribution extends AbstractDistribution implements ExponentialFamily {

    private double alpha;
    private double beta;
    private double mean;
    private double var;
    private double entropy;
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
        var = alpha * beta / ((alpha + beta) * (alpha + beta) * (alpha + beta + 1));
        entropy = Math.log(Beta.beta(alpha, beta)) - (alpha - 1) * Gamma.digamma(alpha) - (beta - 1) * Gamma.digamma(beta) + (alpha + beta - 2) * Gamma.digamma(alpha + beta);
    }

    /**
     * Construct an Beta from the given samples. Parameter
     * will be estimated from the data by the moment method.
     */
    public BetaDistribution(double[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < 0 || data[i] > 1) {
                throw new IllegalArgumentException("Samples are not in range [0, 1].");
            }
        }

        mean = Math.mean(data);
        var = Math.var(data);

        alpha = mean * (mean * (1 - mean) / var - 1);
        beta = (1 - mean) * (mean * (1 - mean) / var - 1);
        if (alpha <= 0 || beta <= 0) {
            throw new IllegalArgumentException("Samples don't follow Beta Distribution.");
        }

        mean = alpha / (alpha + beta);
        var = alpha * beta / ((alpha + beta) * (alpha + beta) * (alpha + beta + 1));
        entropy = Math.log(Beta.beta(alpha, beta)) - (alpha - 1) * Gamma.digamma(alpha) - (beta - 1) * Gamma.digamma(beta) + (alpha + beta - 2) * Gamma.digamma(alpha + beta);
    }

    /**
     * Returns the shape parameter alpha.
     * @return the shape parameter alpha
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Returns the shape parameter beta.
     * @return the shape parameter beta
     */
    public double getBeta() {
        return beta;
    }

    @Override
    public int npara() {
        return 2;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double var() {
        return var;
    }

    @Override
    public double sd() {
        return Math.sqrt(var);
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

        Mixture.Component c = new Mixture.Component();
        c.priori = weight;
        c.distribution = new BetaDistribution(a, b);

        return c;
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
        private int method;
        private double am;
        private double bm;
        private double al;
        private double alnam;
        private double be;
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
                am = (alpha < beta) ? alpha : beta;
                bm = (alpha > beta) ? alpha : beta;
                al = am + bm;
                be = Math.sqrt((al - 2.0) / (2.0 * alpha * beta - al));
                ga = am + 1.0 / be;
            } else {
                method = BC;
                am = (alpha > beta) ? alpha : beta;
                bm = (alpha < beta) ? alpha : beta;
                al = am + bm;
                alnam = al * Math.log(al / am) - 1.386294361;
                be = 1.0 / bm;
                si = 1.0 + am - bm;
                rk1 = si * (0.013888889 + 0.041666667 * bm) / (am * be - 0.77777778);
                rk2 = 0.25 + (0.5 + 0.25 / si) * bm;
            }
        }

        public double rand() {
            double X = 0.0;
            double u1, u2, v, w, y, z, r, s, t;
            switch (method) {
                case BB:
                    /* -X- generator code -X- */
                    while (true) {
                        /* Step 1 */
                        u1 = Math.random();
                        u2 = Math.random();
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
                        X = Math.equals(am, alpha) ? w / (bm + w) : bm / (bm + w);
                        break;
                    }
                    /* -X- end of generator code -X- */
                    break;

                case BC:
                    while (true) {
                        /* Step 1 */
                        u1 = Math.random();
                        u2 = Math.random();

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
                                X = Math.equals(am, alpha) ? 1.0 : 0.0;
                                break;
                            } else {
                                w = am * Math.exp(v);
                                if ((al * (Math.log(al / (bm + w)) + v) - 1.386294361) <
                                        Math.log(z)) {
                                    continue;  /* goto 1 */
                                }

                                /* Step 6_a */
                                X = !Math.equals(am, alpha) ? bm / (bm + w) : w / (bm + w);
                                break;
                            }
                        } else {
                            /* Step 3 */
                            z = u1 * u1 * u2;
                            if (z < 0.25) {
                                /* Step 5 */
                                v = be * Math.log(u1 / (1.0 - u1));
                                if (v > 80.0) {
                                    X = Math.equals(am, alpha) ? 1.0 : 0.0;
                                    break;
                                }

                                w = am * Math.exp(v);
                                X = !Math.equals(am, alpha) ? bm / (bm + w) : w / (bm + w);
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
                                    X = Math.equals(am, alpha) ? 1.0 : 0.0;
                                    break;
                                }
                                w = am * Math.exp(v);
                                if ((al * (Math.log(al / (bm + w)) + v) - 1.386294361) <
                                        Math.log(z)) {
                                    continue;  /* goto 1 */
                                }

                                /* Step 6_b */
                                X = !Math.equals(am, alpha) ? bm / (bm + w) : w / (bm + w);
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

