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
 * An exponential distribution describes the times between events in a Poisson
 * process, in which events occur continuously and independently at a constant
 * average rate. Exponential variables can also be used to model situations
 * where certain events occur with a constant probability per unit length,
 * such as the distance between mutations on a DNA strand. In real world
 * scenarios, the assumption of a constant rate is rarely satisfied. But if
 * we focus on a time interval during which the rate is roughly constant, the
 * exponential distribution can be used as a good approximate model.
 * <p>
 * The exponential distribution may be viewed as a continuous counterpart of
 * the geometric distribution, which describes the number of Bernoulli trials
 * necessary for a discrete process to change state. In contrast,
 * the exponential distribution describes the time for a continuous process to
 * change state.
 * <p>
 * The probability density function of an exponential distribution is
 * f(x; &lambda;) = &lambda;e<sup>-&lambda;x</sup> for x &ge; 0. The cumulative
 * distribution function is given by F(x; &lambda;) = 1 - e<sup>-&lambda; x</sup>
 * for x &ge; 0. An important property of the exponential distribution is that
 * it is memoryless. This means that if a random variable T is exponentially
 * distributed, its conditional probability obeys
 * Pr(T &gt; s + t | T &gt; s) = Pr(T &gt; t) for all s, t &ge; 0.
 * <p>
 * In queuing theory, the service times of agents in a system are often modeled as
 * exponentially distributed variables. Reliability theory and reliability
 * engineering also make extensive use of the exponential distribution. Because
 * of the memoryless property of this distribution, it is well-suited to model
 * the constant hazard rate portion of the bathtub curve used in reliability
 * theory. The exponential distribution is however not appropriate to model
 * the overall lifetime of organisms or technical devices, because the "failure
 * rates" here are not constant: more failures occur for very young and for
 * very old systems.
 * 
 * @author Haifeng Li
 */
public class ExponentialDistribution extends AbstractDistribution implements ExponentialFamily {
    private double lambda;

    /**
     * Constructor.
     * @param lambda rate parameter.
     */
    public ExponentialDistribution(double lambda) {
        if (lambda <= 0) {
            throw new IllegalArgumentException("Invalid lambda: " + lambda);
        }

        this.lambda = lambda;
    }

    /**
     * Constructor. Parameter will be estimated from the data by MLE.
     */
    public ExponentialDistribution(double[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < 0) {
                throw new IllegalArgumentException("Samples contain negative values.");
            }
        }

        double mean = Math.mean(data);
        if (mean == 0) {
            throw new IllegalArgumentException("Samples are all zeros.");
        }

        lambda = 1 / mean;
    }

    /**
     * Returns the rate parameter.
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
        return 1 / lambda;
    }

    @Override
    public double var() {
        return 1 / (lambda * lambda);
    }

    @Override
    public double sd() {
        return 1 / lambda;
    }

    @Override
    public double entropy() {
        return 1 - Math.log(lambda);
    }

    @Override
    public String toString() {
        return String.format("Exponential Distribution(%.4f)", lambda);
    }

    @Override
    public double rand() {
        return -1 / lambda * Math.log(Math.random());
    }

    @Override
    public double p(double x) {
        if (x < 0) {
            return 0.0;
        } else {
            return lambda * Math.exp(-lambda * x);
        }
    }

    @Override
    public double logp(double x) {
        if (x < 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return Math.log(lambda) - lambda * x;
        }
    }

    @Override
    public double cdf(double x) {
        if (x < 0) {
            return 0.0;
        } else {
            return 1 - Math.exp(-lambda * x);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        return -Math.log(1 - p) / lambda;
    }

    @Override
    public Mixture.Component M(double[] x, double[] posteriori) {
        double alpha = 0.0;
        double mean = 0.0;

        for (int i = 0; i < x.length; i++) {
            alpha += posteriori[i];
            mean += x[i] * posteriori[i];
        }

        mean /= alpha;

        Mixture.Component c = new Mixture.Component();
        c.priori = alpha;
        c.distribution = new ExponentialDistribution(1 / mean);

        return c;
    }
}
