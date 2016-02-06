/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
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

package smile

/** Feature generation, normalization and selection.
  *
  * Feature generation (or constructive induction) studies methods that modify
  * or enhance the representation of data objects. Feature generation techniques
  * search for new features that describe the objects better than the attributes
  * supplied with the training instances.
  *
  * Many machine learning methods such as Neural Networks and SVM with Gaussian
  * kernel also require the features properly scaled/standardized. For example,
  * each variable is scaled into interval [0, 1] or to have mean 0 and standard
  * deviation 1. Although some method such as decision trees can handle nominal
  * variable directly, other methods generally require nominal variables converted
  * to multiple binary dummy variables to indicate the presence or absence of a
  * characteristic.
  *
  * Feature selection is the technique of selecting a subset of relevant
  * features for building robust learning models. By removing most irrelevant
  * and redundant features from the data, feature selection helps improve the
  * performance of learning models by alleviating the effect of the curse of
  * dimensionality, enhancing generalization capability, speeding up learning
  * process, etc. More importantly, feature selection also helps researchers
  * to acquire better understanding about the data.
  *
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
package object feature extends Operators {

}
