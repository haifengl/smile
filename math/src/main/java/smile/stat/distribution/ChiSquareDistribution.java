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

import smile.math.special.Gamma;

/**
 * Chi-square (or chi-squared) distribution with k degrees of freedom is the
 * distribution of a sum of the squares of k independent standard normal
 * random variables. It's mean and variance are k and 2k, respectively. The
 * chi-square distribution is a special case of the gamma
 * distribution. It follows from the definition of the chi-square distribution
 * that the sum of independent chi-square variables is also chi-square
 * distributed. Specifically, if X<sub>i</sub> are independent chi-square
 * variables with k<sub>i</sub> degrees of freedom, respectively, then
 * Y = &Sigma; X<sub>i</sub> is chi-square distributed with &Sigma; k<sub>i</sub>
 * degrees of freedom.
 * <p>
 * The chi-square distribution has numerous applications in inferential
 * statistics, for instance in chi-square tests and in estimating variances.
 * Many other statistical tests also lead to a use of this distribution,
 * like Friedman's analysis of variance by ranks.
 *
 * @author Haifeng Li
 */
public class ChiSquareDistribution extends AbstractDistribution implements ExponentialFamily {
    private static final long serialVersionUID = 2L;

    /**
     * The degrees of freedom.
     */
    public final int nu;
    /** The constant part of log-probability function. */
    private final double fac;
    /** The entropy. */
    private final double entropy;

    /**
     * Constructor.
     * @param nu the degree of freedom.
     */
    public ChiSquareDistribution(int nu) {
        if (nu <= 0) {
            throw new IllegalArgumentException("Invalid nu: " + nu);
        }

        this.nu = nu;
        fac = 0.693147180559945309 * (0.5 * nu) + Gamma.lgamma(0.5 * nu);
        entropy = nu / 2.0 + Math.log(2) + Gamma.lgamma(nu / 2.0) + (1 - nu / 2.0) * Gamma.digamma(nu / 2.0);
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public double mean() {
        return nu;
    }

    @Override
    public double variance() {
        return 2 * nu;
    }

    @Override
    public double sd() {
        return Math.sqrt(2 * nu);
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("ChiSquare Distribution(%d)", nu);
    }

    @Override
    public double rand() {
        double x = 0.0;
        for (int i = 0; i < nu; i++) {
            double norm = GaussianDistribution.getInstance().rand();
            x += norm * norm;
        }
        return x;
    }

    @Override
    public double p(double x) {
        if (x <= 0) {
            return 0.0;
        } else {
            return Math.exp(logp(x));
        }
    }

    @Override
    public double logp(double x) {
        if (x <= 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return -0.5 * (x - (nu - 2.0) * Math.log(x)) - fac;
        }
    }

    @Override
    public double cdf(double x) {
        if (x < 0) {
            return 0.0;
        } else {
            return Gamma.regularizedIncompleteGamma(nu / 2.0, x / 2.0);
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        return 2 * Gamma.inverseRegularizedIncompleteGamma(0.5 * nu, p);
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

        return new Mixture.Component(alpha, new ChiSquareDistribution((int) Math.round(mean)));
    }
}
