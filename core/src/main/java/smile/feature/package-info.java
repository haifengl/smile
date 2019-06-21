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

/**
 * Feature generation, normalization and selection.
 * <p>
 * Feature generation (or constructive induction) studies methods that modify
 * or enhance the representation of data objects. Feature generation techniques
 * search for new features that describe the objects better than the attributes
 * supplied with the training instances.
 * <p>
 * Many machine learning methods such as Neural Networks and SVM with Gaussian
 * kernel also require the features properly scaled/standardized. For example,
 * each variable is scaled into interval [0, 1] or to have mean 0 and standard
 * deviation 1. Although some method such as decision trees can handle nominal
 * variable directly, other methods generally require nominal variables converted
 * to multiple binary dummy variables to indicate the presence or absence of a
 * characteristic.
 * <p>
 * Feature selection is the technique of selecting a subset of relevant
 * features for building robust learning models. By removing most irrelevant
 * and redundant features from the data, feature selection helps improve the
 * performance of learning models by alleviating the effect of the curse of
 * dimensionality, enhancing generalization capability, speeding up learning
 * process, etc. More importantly, feature selection also helps researchers
 * to acquire better understanding about the data.
 * <p>
 * Feature selection algorithms typically fall into two categories: feature
 * ranking and subset selection. Feature ranking ranks the features by a
 * metric and eliminates all features that do not achieve an adequate score.
 * Subset selection searches the set of possible features for the optimal subset.
 * Clearly, an exhaustive search of optimal subset is impractical if large
 * numbers of features are available. Commonly, heuristic methods such as
 * genetic algorithms are employed for subset selection.
 * 
 * @author Haifeng Li
 */
package smile.feature;