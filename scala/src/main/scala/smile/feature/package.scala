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

package smile

import smile.data.DataFrame
import smile.data.formula.Formula

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
package object feature {
  /** The signal-to-noise (S2N) metric ratio is a univariate feature ranking metric,
    * which can be used as a feature selection criterion for binary classification
    * problems. S2N is defined as |&mu;<sub>1</sub> - &mu;<sub>2</sub>| / (&sigma;<sub>1</sub> + &sigma;<sub>2</sub>),
    * where &mu;<sub>1</sub> and &mu;<sub>2</sub> are the mean value of the variable
    * in classes 1 and 2, respectively, and &sigma;<sub>1</sub> and &sigma;<sub>2</sub>
    * are the standard deviations of the variable in classes 1 and 2, respectively.
    * Clearly, features with larger S2N ratios are better for classification.
    *
    * ====References:====
    *  - M. Shipp, et al. Diffuse large B-cell lymphoma outcome prediction by gene-expression profiling and supervised machine learning. Nature Medicine, 2002.
    */
  def s2n(x: Array[Array[Double]], y: Array[Int]): Array[Double] = SignalNoiseRatio.of(x, y)

  /** The signal-to-noise (S2N) metric ratio is a univariate feature ranking metric,
    * which can be used as a feature selection criterion for binary classification
    * problems. S2N is defined as |&mu;<sub>1</sub> - &mu;<sub>2</sub>| / (&sigma;<sub>1</sub> + &sigma;<sub>2</sub>),
    * where &mu;<sub>1</sub> and &mu;<sub>2</sub> are the mean value of the variable
    * in classes 1 and 2, respectively, and &sigma;<sub>1</sub> and &sigma;<sub>2</sub>
    * are the standard deviations of the variable in classes 1 and 2, respectively.
    * Clearly, features with larger S2N ratios are better for classification.
    *
    * ====References:====
    *  - M. Shipp, et al. Diffuse large B-cell lymphoma outcome prediction by gene-expression profiling and supervised machine learning. Nature Medicine, 2002.
    */
  def s2n(formula: Formula, data: DataFrame): Array[Double] = {
    val x = formula.x(data).toArray
    val y = formula.y(data).toIntArray
    SignalNoiseRatio.of(x, y)
  }

  /** The ratio of between-groups to within-groups sum of squares is a univariate
    * feature ranking metric, which can be used as a feature selection criterion
    * for multi-class classification problems. For each variable j, this ratio is
    * BSS(j) / WSS(j) = &Sigma;I(y<sub>i</sub> = k)(x<sub>kj</sub> - x<sub>&middot;j</sub>)<sup>2</sup> / &Sigma;I(y<sub>i</sub> = k)(x<sub>ij</sub> - x<sub>kj</sub>)<sup>2</sup>;
    * where x<sub>&middot;j</sub> denotes the average of variable j across all
    * samples, x<sub>kj</sub> denotes the average of variable j across samples
    * belonging to class k, and x<sub>ij</sub> is the value of variable j of sample i.
    * Clearly, features with larger sum squares ratios are better for classification.
    *
    * ====References:====
    *  - S. Dudoit, J. Fridlyand and T. Speed. Comparison of discrimination methods for the classification of tumors using gene expression data. J Am Stat Assoc, 97:77-87, 2002.
    */
  def ssr(x: Array[Array[Double]], y: Array[Int]): Array[Double] = SumSquaresRatio.of(x, y)

  /** The ratio of between-groups to within-groups sum of squares is a univariate
    * feature ranking metric, which can be used as a feature selection criterion
    * for multi-class classification problems. For each variable j, this ratio is
    * BSS(j) / WSS(j) = &Sigma;I(y<sub>i</sub> = k)(x<sub>kj</sub> - x<sub>&middot;j</sub>)<sup>2</sup> / &Sigma;I(y<sub>i</sub> = k)(x<sub>ij</sub> - x<sub>kj</sub>)<sup>2</sup>;
    * where x<sub>&middot;j</sub> denotes the average of variable j across all
    * samples, x<sub>kj</sub> denotes the average of variable j across samples
    * belonging to class k, and x<sub>ij</sub> is the value of variable j of sample i.
    * Clearly, features with larger sum squares ratios are better for classification.
    *
    * ====References:====
    *  - S. Dudoit, J. Fridlyand and T. Speed. Comparison of discrimination methods for the classification of tumors using gene expression data. J Am Stat Assoc, 97:77-87, 2002.
    */
  def ssr(formula: Formula, data: DataFrame): Array[Double] = {
    val x = formula.x(data).toArray
    val y = formula.y(data).toIntArray
    SumSquaresRatio.of(x, y)
  }
}
