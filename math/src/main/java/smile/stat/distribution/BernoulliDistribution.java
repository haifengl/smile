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

    /**
     * Probability of success.
     */
    private double p;
    /**
     * Probability of failure.
     */
    private double q;
    /**
     * Shannon entropy.
     */
    private double entropy;

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

        entropy = -p * Math.log2(p) - q * Math.log2(q);
    }

    /**
     * Constructor. Parameter will be estimated from the data by MLE.
     * @param data data[i] == 1 if the i-<i>th</i> trail is success. Otherwise 0.
     */
    public BernoulliDistribution(int[] data) {
        int k = 0;
        for (int i : data) {
            if (i == 1) {
                k++;
            } else if (i != 0) {
                throw new IllegalArgumentException("Invalid value " + i);
            }
        }

        p = (double) k / data.length;
        q = 1 - p;

        entropy = -p * Math.log2(p) - q * Math.log2(q);
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

        entropy = -p * Math.log2(p) - q * Math.log2(q);
    }

    /**
     * Returns the probability of success.
     * @return the probability of success
     */
    public double getProb() {
        return p;
    }

    @Override
    public int npara() {
        return 1;
    }

    @Override
    public double mean() {
        return p;
    }

    @Override
    public double var() {
        return p * q;
    }

    @Override
    public double sd() {
        return Math.sqrt(p * q);
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
        if (Math.random() < q) {
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
