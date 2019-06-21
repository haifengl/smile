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
 * The exponential family is a class of probability distributions sharing
 * a certain form. The normal, exponential, gamma, chi-square, beta, Weibull
 * (if the shape parameter is known), Dirichlet, Bernoulli, binomial,
 * multinomial, Poisson, negative binomial, and geometric distributions
 * are all exponential families. The family of Pareto distributions with
 * a fixed minimum bound form an exponential family.
 * <p>
 * The Cauchy, Laplace, and uniform families of distributions are not
 * exponential families. The Weibull distribution is not an exponential
 * family unless the shape parameter is known.
 * <p>
 * The purpose of this interface is mainly to define the method M that is
 * the Maximization step in the EM algorithm. Note that distributuions of exponential
 * family has the close-form solutions in the EM algorithm. With this interface,
 * we may allow the mixture contains distributions of different form as long as
 * it is from exponential family.
 *
 * @see ExponentialFamilyMixture
 * @see DiscreteExponentialFamily
 * @see DiscreteExponentialFamilyMixture
 *
 * @author Haifeng Li
 */
public interface ExponentialFamily {

    /**
     * The M step in the EM algorithm, which depends the specific distribution.
     *
     * @param x the input data for estimation
     * @param posteriori the posteriori probability.
     * @return the (unnormalized) weight of this distribution in the mixture.
     */
    Mixture.Component M(double[] x , double[] posteriori);
}
