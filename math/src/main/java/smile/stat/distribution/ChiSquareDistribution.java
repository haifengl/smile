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
    /**
     * degrees of freedom.
     */
    private int nu;
    private double fac;
    private double entropy;

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

    /**
     * Returns the parameter nu, the degrees of freedom.
     */
    public int getNu() {
        return nu;
    }

    @Override
    public int npara() {
        return 1;
    }

    @Override
    public double mean() {
        return nu;
    }

    @Override
    public double var() {
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

        Mixture.Component c = new Mixture.Component();
        c.priori = alpha;
        c.distribution = new ChiSquareDistribution((int) Math.round(mean));

        return c;
    }
}
