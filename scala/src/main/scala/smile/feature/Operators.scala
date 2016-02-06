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

package smile.feature

/** High level feature selection operators.
  *
  * @author Haifeng Li
  */
trait Operators {

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
  def signalNoiseRatio(x: Array[Array[Double]], y: Array[Int]): Array[Double] = new SignalNoiseRatio().rank(x, y)

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
  def sumSquaresRatio(x: Array[Array[Double]], y: Array[Int]): Array[Double] = new SumSquaresRatio().rank(x, y)
}