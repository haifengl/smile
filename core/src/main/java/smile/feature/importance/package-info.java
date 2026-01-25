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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Feature importance.
 * <p>
 * Global explanations tries to describe the model as whole, in terms of
 * which variables/features influenced the general model the most. Two
 * common methods for such an overall explanation is some kind of
 * permutation feature importance or partial dependence plots.
 * Permutation feature importance measures the increase in the prediction
 * error of the model after permuting the feature's values, which breaks
 * the relationship between the feature and the true outcome.
 * The partial dependence plot (PDP) shows the marginal effect one or
 * two features have on the predicted outcome of a machine learning model.
 * A partial dependence plot can show whether the relationship between
 * the target and a feature is linear, monotonic or more complex.
 * <p>
 * Local explanations tries to identify how the different input
 * variables/features influenced a specific prediction/output from the model,
 * and are often referred to as individual prediction explanation methods.
 * Such explanations are particularly useful for complex models which behave
 * rather different for different feature combinations, meaning that the
 * global explanation is not representative for the local behavior.
 * <p>
 * Local explanation methods may further be divided into two categories:
 * model-specific and model-agnostic (general) explanation methods.
 * The model-agnostic methods usually try to explain individual predictions
 * by learning simple, interpretable explanations of the model specifically
 * for a given prediction. Three examples are Explanation Vectors, LIME,
 * and Shapley values.
 *
 * @author Haifeng Li
 */
package smile.feature.importance;