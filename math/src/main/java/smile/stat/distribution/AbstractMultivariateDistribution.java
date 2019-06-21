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

/**
 * This is the base class of multivariate distributions. Likelihood and
 * log likelihood functions are implemented here.
 *
 * @author Haifeng Li
 */
public abstract class AbstractMultivariateDistribution implements MultivariateDistribution {

    /**
     * The likelihood given a sample set following the distribution.
     */
    @Override
    public double likelihood(double[][] x) {
        return Math.exp(logLikelihood(x));
    }

    /**
     * The likelihood given a sample set following the distribution.
     */
    @Override
    public double logLikelihood(double[][] x) {
        double L = 0.0;

        for (double[] xi : x)
            L += logp(xi);

        return L;
    }
}
