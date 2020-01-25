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

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;
import smile.math.MathEx;

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
    private static final long serialVersionUID = 2L;

    /**
     * A component in the mixture distribution is defined by a distribution
     * and its weight in the mixture.
     */
    public static class Component implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * The priori probability of component.
         */
        public final double priori;

        /**
         * The distribution of component.
         */
        public final Distribution distribution;

        /**
         * Constructor.
         * @param priori the priori probability of component.
         * @param distribution the distribution of component.
         */
        public Component(double priori, Distribution distribution) {
            this.priori = priori;
            this.distribution = distribution;
        }
    }

    /** The components of finite mixture model. */
    public final Component[] components;

    /**
     * Constructor.
     * @param components a list of distributions.
     */
    public Mixture(Component... components) {
        if (components.length == 0) {
            throw new IllegalStateException("Empty mixture!");
        }

        double sum = 0.0;
        for (Component component : components) {
            sum += component.priori;
        }

        if (Math.abs(sum - 1.0) > 1E-3) {
            throw new IllegalArgumentException("The sum of priori is not equal to 1.");
        }

        this.components = components;
    }

    /** Returns the posteriori probabilities. */
    public double[] posteriori(double x) {
        int k = components.length;
        double[] prob = new double[k];
        for (int i = 0; i < k; i++) {
            Component c = components[i];
            prob[i] = c.priori * c.distribution.p(x);
        }

        double p = MathEx.sum(prob);
        for (int i = 0; i < k; i++) {
            prob[i] /= p;
        }
        return prob;
    }

    /** Returns the index of component with maximum a posteriori probability. */
    public int map(double x) {
        int k = components.length;
        double[] prob = new double[k];
        for (int i = 0; i < k; i++) {
            Component c = components[i];
            prob[i] = c.priori * c.distribution.p(x);
        }

        return MathEx.whichMax(prob);
    }

    @Override
    public double mean() {
        double mu = 0.0;

        for (Component c : components)
            mu += c.priori * c.distribution.mean();

        return mu;
    }

    @Override
    public double variance() {
        double variance = 0.0;

        for (Component c : components)
            variance += c.priori * c.priori * c.distribution.variance();

        return variance;
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
        double p = 0.0;

        for (Component c : components)
            p += c.priori * c.distribution.p(x);

        return p;
    }

    @Override
    public double logp(double x) {
        return Math.log(p(x));
    }

    @Override
    public double cdf(double x) {
        double p = 0.0;

        for (Component c : components)
            p += c.priori * c.distribution.cdf(x);

        return p;
    }

    @Override
    public double rand() {
        double r = MathEx.random();

        double p = 0.0;
        for (Component g : components) {
            p += g.priori;
            if (r <= p)
                return g.distribution.rand();
        }

        // we should not arrive here.
        throw new IllegalStateException();
    }

    @Override
    public double quantile(double p) {
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
    public int length() {
        int length = components.length - 1; // independent priori parameters
        for (Component component : components)
            length += component.distribution.length();

        return length;
    }

    /**
     * Returns the number of components in the mixture.
     */
    public int size() {
        return components.length;
    }

    /**
     * The BIC score of the mixture for given data.
     */
    public double bic(double[] data) {
        int n = data.length;

        double logLikelihood = 0.0;
        for (double x : data) {
            double p = p(x);
            if (p > 0) logLikelihood += Math.log(p);
        }

        return logLikelihood - 0.5 * length() * Math.log(n);
    }

    @Override
    public String toString() {
        return Arrays.stream(components)
                .map(component -> String.format("%.2f x %s", component.priori, component.distribution))
                .collect(Collectors.joining(" + ", String.format("Mixture(%d)[", components.length), "]"));
    }
}
