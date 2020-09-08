/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.glm.model;

import java.io.Serializable;
import java.util.stream.IntStream;

/**
 * The GLM model specification. The GLM consists of three elements:
 * <ol>
 * <li>An exponential family of probability distributions.</li>
 * <li>A linear predictor.</li>
 * <li>A link function provides the relationship between the linear
 * predictor and the mean of the distribution function.</li>
 * </ol>
 * This class specifies the distribution and link function in the model.
 * <p>
 * An overdispersed exponential family of distributions is a generalization
 * of an exponential family and the exponential dispersion model of
 * distributions and includes those families of probability distributions,
 * parameterized by &theta; and &tau;. The parameter &theta; is related
 * to the mean of the distribution. The dispersion parameter &tau; typically
 * is known and is usually related to the variance of the distribution.
 * <p>
 * There are many commonly used link functions, and their choice is informed
 * by several considerations. There is always a well-defined canonical link
 * function which is derived from the exponential of the response's density
 * function. However, in some cases it makes sense to try to match the domain
 * of the link function to the range of the distribution function's mean,
 * or use a non-canonical link function for algorithmic purposes.
 *
 * @author Haifeng Li
 */
public interface Model extends Serializable {
    /** The link function. */
    double link(double mu);
    /** The inverse of link function. */
    double invlink(double eta);
    /** The derivative of link function. */
    double dlink(double mu);
    /** The variance function. */
    double variance(double mu);
    /** The deviance function. */
    double deviance(double[] y, double[] mu, double[] residuals);
    /** The NULL deviance function. */
    double nullDeviance(double[] y, double mu);
    /** The log-likelihood function. */
    double loglikelihood(double[] y, double[] mu);
    /** The function to estimates the tarting values of means given y. */
    double mustart(double y);
}
