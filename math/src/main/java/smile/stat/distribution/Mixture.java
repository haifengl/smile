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

import java.util.List;
import java.util.ArrayList;
import smile.math.Math;

/**
 * A finite mixture model is a probabilistic model for density estimation using a
 * mixture distribution. A mixture model can be regarded as a type of
 * unsupervised learning or clustering.
 * <p>
 * The Expectation-maximization algorithm can be used to compute the
 * parameters of a parametric mixture model distribution. The EM algorithm is
 * a method for finding maximum likelihood estimates of parameters, where the
 * model depends on unobserved latent variables. EM is an iterative method which
 * alternates between performing an expectation (E) step, which computes the
 * expectation of the log-likelihood evaluated using the current estimate for
 * the latent variables, and a maximization (M) step, which computes parameters
 * maximizing the expected log-likelihood found on the E step. These parameter
 * estimates are then used to determine the distribution of the latent variables
 * in the next E step.
 *
 * @author Haifeng Li
 */
public class Mixture extends AbstractDistribution {

    /**
     * A component in the mixture distribution is defined by a distribution
     * and its weight in the mixture.
     */
    public static class Component {
        /**
         * The distribution of component.
         */
        public Distribution distribution;
        /**
         * The priori probability of component.
         */
        public double priori;

        public Component() {
        }

        public Component(double priori, Distribution distribution) {
            this.priori = priori;
            this.distribution = distribution;
        }
    }

    List<Component> components;

    /**
     * Constructor.
     */
    Mixture() {
        components = new ArrayList<>();
    }

    /**
     * Constructor.
     * @param mixture a list of distributions.
     */
    public Mixture(List<Component> mixture) {
        components = new ArrayList<>();
        components.addAll(mixture);

        double sum = 0.0;
        for (Component component : mixture) {
            sum += component.priori;
        }

        if (Math.abs(sum - 1.0) > 1E-3)
            throw new IllegalArgumentException("The sum of priori is not equal to 1.");
    }

    @Override
    public double mean() {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        double mu = 0.0;

        for (Component c : components)
            mu += c.priori * c.distribution.mean();

        return mu;
    }

    @Override
    public double var() {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        double variance = 0.0;

        for (Component c : components)
            variance += c.priori * c.priori * c.distribution.var();

        return variance;
    }

    @Override
    public double sd() {
        return Math.sqrt(var());
    }

    /**
     * Shannon entropy. Not supported.
     */
    @Override
    public double entropy() {
        throw new UnsupportedOperationException("Mixture does not support entropy()");
    }
    
    @Override
    public double p(double x) {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        double p = 0.0;

        for (Component c : components)
            p += c.priori * c.distribution.p(x);

        return p;
    }

    @Override
    public double logp(double x) {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        return Math.log(p(x));
    }

    @Override
    public double cdf(double x) {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        double p = 0.0;

        for (Component c : components)
            p += c.priori * c.distribution.cdf(x);

        return p;
    }

    @Override
    public double rand() {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        double r = Math.random();

        double p = 0.0;
        for (Component g : components) {
            p += g.priori;
            if (r <= p)
                return g.distribution.rand();
        }

        // we should not arrive here.
        return components.get(components.size()-1).distribution.rand();
    }

    @Override
    public double quantile(double p) {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        // Starting guess near peak of density.
        // Expand interval until we bracket.
        double xl, xu, inc = 1;
        double x = (int) mean();
        if (p < cdf(x)) {
            do {
                x -= inc;
                inc *= 2;
            } while (p < cdf(x));
            xl = x;
            xu = x + inc / 2;
        } else {
            do {
                x += inc;
                inc *= 2;
            } while (p > cdf(x));
            xu = x;
            xl = x - inc / 2;
        }

        return quantile(p, xl, xu);
    }

    @Override
    public int npara() {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        int f = components.size() - 1; // independent priori parameters
        for (int i = 0; i < components.size(); i++)
            f += components.get(i).distribution.npara();

        return f;
    }

    /**
     * Returns the number of components in the mixture.
     */
    public int size() {
        return components.size();
    }

    /**
     * BIC score of the mixture for given data.
     */
    public double bic(double[] data) {
        if (components.isEmpty())
            throw new IllegalStateException("Mixture is empty!");

        int n = data.length;

        double logLikelihood = 0.0;
        for (double x : data) {
            double p = p(x);
            if (p > 0) logLikelihood += Math.log(p);
        }

        return logLikelihood - 0.5 * npara() * Math.log(n);
    }

    /**
     * Returns the list of components in the mixture.
     */
    public List<Component> getComponents() {
        return components;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Mixture[");
        builder.append(components.size());
        builder.append("]:{");
        for (Component c : components) {
            builder.append(" (");
            builder.append(c.distribution);
            builder.append(':');
            builder.append(String.format("%.4f", c.priori));
            builder.append(')');
        }
        builder.append("}");
        return builder.toString();
    }
}
