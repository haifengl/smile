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

import java.util.Arrays;
import java.util.stream.Collectors;
import smile.math.MathEx;

/**
 * The finite mixture of discrete distributions.
 *
 * @author Haifeng Li
 */
public class DiscreteMixture extends DiscreteDistribution {
    private static final long serialVersionUID = 2L;

    /**
     * A component in the mixture distribution is defined by a distribution
     * and its weight in the mixture.
     */
    public static class Component {
        /**
         * The priori probability of component.
         */
        public final double priori;

        /**
         * The distribution of component.
         */
        public final DiscreteDistribution distribution;

        /**
         * Constructor.
         * @param priori the priori probability of component.
         * @param distribution the distribution of component.
         */
        public Component(double priori, DiscreteDistribution distribution) {
            this.priori = priori;
            this.distribution = distribution;
        }
    }

    /** The components of finite mixture model. */
    public final Component[] components;

    /**
     * Constructor.
     * @param components a list of discrete distributions.
     */
    public DiscreteMixture(Component... components) {
        if (components.length == 0) {
            throw new IllegalStateException("Empty mixture!");
        }

        this.components = components;

        double sum = 0.0;
        for (Component component : components) {
            sum += component.priori;
            if (component.distribution instanceof DiscreteDistribution == false) {
                throw new IllegalArgumentException("Component " + component + " is not a discrete distribution.");
            }
        }

        if (Math.abs(sum - 1.0) > 1E-3) {
            throw new IllegalArgumentException("The sum of priori is not equal to 1.");
        }
    }

    /** Returns the posteriori probabilities. */
    public double[] posteriori(int x) {
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
    public int map(int x) {
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

        for (Component c : components) {
            mu += c.priori * c.distribution.mean();
        }

        return mu;
    }

    @Override
    public double variance() {
        double variance = 0.0;

        for (Component c : components) {
            variance += c.priori * c.priori * c.distribution.variance();
        }

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
    public double p(int x) {
        double p = 0.0;

        for (Component c : components) {
            p += c.priori * c.distribution.p(x);
        }

        return p;
    }

    @Override
    public double logp(int x) {
        return Math.log(p(x));
    }

    @Override
    public double cdf(double x) {
        double p = 0.0;

        for (Component c : components) {
            p += c.priori * c.distribution.cdf(x);
        }

        return p;
    }

    @Override
    public double rand() {
        double r = MathEx.random();

        double p = 0.0;
        for (Component g : components) {
            p += g.priori;
            if (r <= p) {
                return g.distribution.rand();
            }
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
        int xl, xu, inc = 1;
        int x = (int) mean();
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
        int length = 0;
        for (Component component : components) {
            length += component.distribution.length();
        }

        return length;
    }

    /**
     * Returns the number of components in the mixture.
     */
    public int size() {
        return components.length;
    }

    /**
     * BIC score of the mixture for given data.
     */
    public double bic(double[] data) {
        int n = data.length;

        double logLikelihood = 0.0;
        for (double x : data) {
            double p = p(x);
            if (p > 0) {
                logLikelihood += Math.log(p);
            }
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
