/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.stat.distribution;

/**
 * The purpose of this interface is mainly to define the method M that is
 * the Maximization step in the EM algorithm. Note that distributions of exponential
 * family has the close-form solutions in the EM algorithm. With this interface,
 * we may allow the mixture contains distributions of different form as long as
 * it is from exponential family.
 *
 * @see ExponentialFamilyMixture
 *
 * @author Haifeng Li
 */
public interface MultivariateExponentialFamily {

    /**
     * The M step in the EM algorithm, which depends on the specific distribution.
     *
     * @param x the input data for estimation
     * @param posteriori the posteriori probability.
     * @return the (unnormalized) weight of this distribution in the mixture.
     */
    MultivariateMixture.Component M(double[][] x , double[] posteriori);
}
