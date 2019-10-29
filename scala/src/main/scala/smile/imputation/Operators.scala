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

package smile.imputation

import smile.util._

/** High level missing value imputation operators.
  * The NaN values in the input data are treated as
  * missing values and will be replaced with imputed
  * values after the processing.
  *
  * @author Haifeng Li
  */
trait Operators {

  /** Impute missing values with the average of other attributes in the instance.
    * Assume the attributes of the dataset are of same kind, e.g. microarray gene
    * expression data, the missing values can be estimated as the average of
    * non-missing attributes in the same instance. Note that this is not the
    * average of same attribute across different instances.
    *
    * @param data the data set with missing values.
    */
  def avgimpute(data: Array[Array[Double]]): Unit = time("Missing value imputation by average") {
    new AverageImputation().impute(data)
  }

  /** Missing value imputation by k-nearest neighbors. The KNN-based method
    * selects instances similar to the instance of interest to impute
    * missing values. If we consider instance A that has one missing value on
    * attribute i, this method would find K other instances, which have a value
    * present on attribute i, with values most similar (in term of some distance,
    * e.g. Euclidean distance) to A on other attributes without missing values.
    * The average of values on attribute i from the K nearest
    * neighbors is then used as an estimate for the missing value in instance A.
    *
    * @param data the data set with missing values.
    * @param k the number of neighbors.
    */
  def knnimpute(data: Array[Array[Double]], k: Int): Unit = time("Missing value imputation by k-nn") {
    new KNNImputation(k).impute(data)
  }

  /** Missing value imputation by K-Means clustering. First cluster data by K-Means
    * with missing values and then impute missing values with the average value of each attribute
    * in the clusters.
    *
    * @param data the data set.
    * @param k the number of clusters.
    * @param runs the number of runs of K-Means algorithm.
    */
  def impute(data: Array[Array[Double]], k: Int, runs: Int = 8): Unit = time("Missing value imputation by k-means") {
    new KMeansImputation(k, runs).impute(data)
  }

  /** Local least squares missing value imputation. The local least squares
    * imputation method represents a target instance that has missing values as
    * a linear combination of similar instances, which are selected by k-nearest
    * neighbors method.
    *
    * @param data the data set.
    * @param k the number of similar rows used for imputation.
    */
  def llsimpute(data: Array[Array[Double]], k: Int): Unit = time("Missing value imputation by local least squares") {
    new LLSImputation(k).impute(data)
  }

  /** Missing value imputation with singular value decomposition. Given SVD
    * A = U &Sigma; V<sup>T</sup>, we use the most significant eigenvectors of
    * V<sup>T</sup> to linearly estimate missing values. Although it has been
    * shown that several significant eigenvectors are sufficient to describe
    * the data with small errors, the exact fraction of eigenvectors best for
    * estimation needs to be determined empirically. Once k most significant
    * eigenvectors from V<sup>T</sup> are selected, we estimate a missing value j
    * in row i by first regressing this row against the k eigenvectors and then use
    * the coefficients of the regression to reconstruct j from a linear combination
    * of the k eigenvectors. The j th value of row i and the j th values of the k
    * eigenvectors are not used in determining these regression coefficients.
    * It should be noted that SVD can only be performed on complete matrices;
    * therefore we originally fill all missing values by other methods in
    * matrix A, obtaining A'. We then utilize an expectation maximization method to
    * arrive at the final estimate, as follows. Each missing value in A is estimated
    * using the above algorithm, and then the procedure is repeated on the newly
    * obtained matrix, until the total change in the matrix falls below the
    * empirically determined threshold (say 0.01).
    *
    * @param data the data set.
    * @param k the number of eigenvectors used for imputation.
    * @param maxIter the maximum number of iterations.
    */
  def svdimpute(data: Array[Array[Double]], k: Int, maxIter: Int = 10): Unit = time("Missing value imputation by SVD") {
    new SVDImputation(k).impute(data)
  }
}