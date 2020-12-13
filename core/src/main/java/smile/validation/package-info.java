/*
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
 */

/**
 * Model validation and selection.
 * <p>
 * Model validation is the task of confirming that the outputs of a statistical
 * model are acceptable with respect to the real data-generating process.
 * A model can be validated only relative to some application area. A model
 * that is valid for one application might be invalid for some other
 * applications.
 * <p>
 * Model validation can be based on two types of data: data that was used
 * in the construction of the model and data that was not used in the
 * construction. Validation based on the first type usually involves
 * analyzing the goodness of fit of the model or analyzing whether the
 * residuals seem to be random (i.e. residual diagnostics). Validation
 * based on only the first type is often inadequate.
 * Validation based on the second type usually involves analyzing whether
 * the model's predictive performance deteriorates non-negligibly when
 * applied to pertinent new data.
 * <p>
 * Model selection is the task of selecting a statistical model from
 * a set of candidate models, given data. In the simplest cases,
 * a pre-existing set of data is considered. However, the task can also
 * involve the design of experiments such that the data collected is
 * well-suited to the problem of model selection.
 * <p>
 * Once the set of candidate models has been chosen, the statistical analysis
 * allows us to select the best of these models. What is meant by best is
 * controversial. A good model selection technique will balance goodness
 * of fit with simplicity. More complex models will be better able to adapt
 * their shape to fit the data, but the additional parameters may not
 * represent anything useful. Goodness of fit is generally determined
 * using a likelihood ratio approach, or an approximation of this,
 * leading to a chi-squared test. The complexity is generally measured
 * by counting the number of parameters in the model. Given candidate models
 * of similar predictive or explanatory power, the simplest model is most
 * likely to be the best choice (Occam's razor).
 *
 * @author Haifeng Li
 */
package smile.validation;
