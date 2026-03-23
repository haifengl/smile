/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.stat.distribution;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;
import smile.math.MathEx;
import smile.tensor.DenseMatrix;
import static smile.tensor.ScalarType.*;

/**
 * The finite mixture of multivariate distributions.
 *
 * @author Haifeng Li
 */
public class MultivariateMixture implements MultivariateDistribution {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * A component in the mixture distribution is defined by a distribution
     * and its weight in the mixture.
     * @param priori the priori probability of component.
     * @param distribution the distribution of component.
     */
    public record Component(double priori, MultivariateDistribution distribution) implements Serializable {
    }

    /** The components of finite mixture model. */
    public final Component[] components;

    /**
     * Constructor.
     * @param components a list of multivariate distributions.
     */
    public MultivariateMixture(Component... components) {
        if (components.length == 0) {
            throw new IllegalStateException("Empty mixture!");
        }

        this.components = components;
    }

    /**
     * Returns the posteriori probabilities.
     * @param x a real vector.
     * @return the posteriori probabilities.
     */
    public double[] posteriori(double[] x) {
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

    /**
     * Returns the index of component with maximum a posteriori probability.
     * @param x a real vector.
     * @return the index of component with maximum a posteriori probability.
     */
    public int map(double[] x) {
        int k = components.length;
        double[] prob = new double[k];
        for (int i = 0; i < k; i++) {
            Component c = components[i];
            prob[i] = c.priori * c.distribution.p(x);
        }

        return MathEx.whichMax(prob);
    }

    @Override
    public double[] mean() {
        double w = components[0].priori;
        double[] m = components[0].distribution.mean();
        double[] mu = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            mu[i] = w * m[i];
        }

        for (int k = 1; k < components.length; k++) {
            w = components[k].priori;
            m = components[k].distribution.mean();
            for (int i = 0; i < m.length; i++) {
                mu[i] += w * m[i];
            }
        }

        return mu;
    }

    @Override
    public DenseMatrix cov() {
        double w = components[0].priori;
        DenseMatrix v = components[0].distribution.cov();

        int m = v.nrow();
        int n = v.ncol();
        DenseMatrix cov = DenseMatrix.zeros(Float64, m, n);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                cov.set(i, j, w * w * v.get(i, j));
            }
        }

        for (int k = 1; k < components.length; k++) {
            w = components[k].priori;
            v = components[k].distribution.cov();
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    cov.add(i, j, w * w * v.get(i, j));
                }
            }
        }

        return cov;
    }

    /**
     * Shannon entropy. Not supported.
     */
    @Override
    public double entropy() {
        throw new UnsupportedOperationException("Mixture does not support entropy()");
    }

    @Override
    public double p(double[] x) {
        double p = 0.0;

        for (Component c : components) {
            p += c.priori * c.distribution.p(x);
        }

        return p;
    }

    @Override
    public double logp(double[] x) {
        return Math.log(p(x));
    }

    @Override
    public double cdf(double[] x) {
        double p = 0.0;

        for (Component c : components) {
            p += c.priori * c.distribution.cdf(x);
        }

        return p;
    }

    @Override
    public int length() {
        int f = components.length - 1; // independent priori parameters
        for (Component component : components) {
            f += component.distribution.length();
        }

        return f;
    }

    /**
     * Returns the number of components in the mixture.
     * @return the number of components.
     */
    public int size() {
        return components.length;
    }

    /**
     * Returns the BIC score.
     * @param data the data to calculate likelihood.
     * @return the BIC score.
     */
    public double bic(double[][] data) {
        int n = data.length;

        double logLikelihood = 0.0;
        for (double[] x : data) {
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
                .collect(Collectors.joining(" + ", String.format("MultivariateMixture(%d)[", components.length), "]"));
    }
}
