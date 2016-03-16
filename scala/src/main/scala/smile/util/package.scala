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

import smile.math.distance.{EuclideanDistance, Distance, Metric}
import smile.math.rbf.GaussianRadialBasis

/** Utility functions.
  *
  * @author Haifeng Li
  */
package object util extends Logging {
  /** Measure running time of a function/block */
  object time {
    /** Print out switch. */
    var echo = true

    /** Turn on printing out running time. */
    def on = {
      echo = true
    }

    /** Turn on printing out running time. */
    def off = {
      echo = false
    }

    /** Executes a code block and measure the running time.
      * @param f a code block to measure the running time.
      * @tparam A The output type of code block.
      * @return the code block expression result.
      */
    def apply[A](f: => A) = {
      val s = System.nanoTime
      val ret = f
      if (echo) logger.info("runtime: {} ms", (System.nanoTime - s)/1e6)
      ret
    }
  }

  /** Returns the proximity matrix of a dataset for given distance function.
    *
    * @param data the data set.
    * @param dist the distance function.
    * @param half if true, only the lower half of matrix is allocated to save space.
    * @return the lower half of proximity matrix.
    */
  def proximity[T](data: Array[T], dist: Distance[T], half: Boolean = true): Array[Array[Double]] = {
    val n = data.length

    if (half) {
      val d = new Array[Array[Double]](n)
      for (i <- 0 until n) {
        d(i) = new Array[Double](i + 1)
        for (j <- 0 until i)
          d(i)(j) = dist.d(data(i), data(j))
      }
      d
    } else {
      val d = Array.ofDim[Double](n, n)
      for (i <- 0 until n) {
        for (j <- 0 until i) {
          d(i)(j) = dist.d(data(i), data(j))
          d(j)(i) = d(i)(j)
        }
      }
      d
    }
  }

  /** Returns the pairwise Euclidean distance matrix.
    *
    * @param data the data set.
    * @param half if true, only the lower half of matrix is allocated to save space.
    * @return the lower half of proximity matrix.
    */
  def pdist(data: Array[Array[Double]], half: Boolean = true): Array[Array[Double]] = {
    proximity(data, new EuclideanDistance, half)
  }

  /** Learns Gaussian RBF function and centers from data. The centers are
    * chosen as the centroids of K-Means. Let d<sub>max</sub> be the maximum
    * distance between the chosen centers, the standard deviation (i.e. width)
    * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
    * k is number of centers. This choice would be close to the optimal
    * solution if the data were uniformly distributed in the input space,
    * leading to a uniform distribution of centroids.
    * @param x the training dataset.
    * @param centers an array to store centers on output. Its length is used as k of k-means.
    * @return a Gaussian RBF function with parameter learned from data.
    */
  def gaussrbf(x: Array[Array[Double]], centers: Array[Array[Double]]): GaussianRadialBasis = {
    SmileUtils.learnGaussianRadialBasis(x, centers)
  }

  /** Learns Gaussian RBF function and centers from data. The centers are
    * chosen as the centroids of K-Means. The standard deviation (i.e. width)
    * of Gaussian radial basis function is estimated by the p-nearest neighbors
    * (among centers, not all samples) heuristic. A suggested value for
    * p is 2.
    * @param x the training dataset.
    * @param centers an array to store centers on output. Its length is used as k of k-means.
    * @param p the number of nearest neighbors of centers to estimate the width
    *          of Gaussian RBF functions.
    * @return Gaussian RBF functions with parameter learned from data.
    */
  def gaussrbf(x: Array[Array[Double]], centers: Array[Array[Double]], p: Int): Array[GaussianRadialBasis] = {
    SmileUtils.learnGaussianRadialBasis(x, centers, p)
  }

  /** Learns Gaussian RBF function and centers from data. The centers are
    * chosen as the centroids of K-Means. The standard deviation (i.e. width)
    * of Gaussian radial basis function is estimated as the width of each
    * cluster multiplied with a given scaling parameter r.
    * @param x the training dataset.
    * @param centers an array to store centers on output. Its length is used as k of k-means.
    * @param r the scaling parameter.
    * @return Gaussian RBF functions with parameter learned from data.
    */
  def gaussrbf(x: Array[Array[Double]], centers: Array[Array[Double]], r: Double): Array[GaussianRadialBasis] = {
    SmileUtils.learnGaussianRadialBasis(x, centers, r)
  }

  /** Learns Gaussian RBF function and centers from data. The centers are
    * chosen as the medoids of CLARANS. Let d<sub>max</sub> be the maximum
    * distance between the chosen centers, the standard deviation (i.e. width)
    * of Gaussian radial basis function is d<sub>max</sub> / sqrt(2*k), where
    * k is number of centers. In this way, the radial basis functions are not
    * too peaked or too flat. This choice would be close to the optimal
    * solution if the data were uniformly distributed in the input space,
    * leading to a uniform distribution of medoids.
    * @param x the training dataset.
    * @param centers an array to store centers on output. Its length is used as k of CLARANS.
    * @param distance the distance functor.
    * @return a Gaussian RBF function with parameter learned from data.
    */
  def gaussrbf[T <: AnyRef](x: Array[T], centers: Array[T], distance: Metric[T]): GaussianRadialBasis = {
    SmileUtils.learnGaussianRadialBasis(x, centers, distance)
  }

  /** Learns Gaussian RBF function and centers from data. The centers are
    * chosen as the medoids of CLARANS. The standard deviation (i.e. width)
    * of Gaussian radial basis function is estimated by the p-nearest neighbors
    * (among centers, not all samples) heuristic. A suggested value for
    * p is 2.
    * @param x the training dataset.
    * @param centers an array to store centers on output. Its length is used as k of CLARANS.
    * @param distance the distance functor.
    * @param p the number of nearest neighbors of centers to estimate the width
    *          of Gaussian RBF functions.
    * @return Gaussian RBF functions with parameter learned from data.
    */
  def gaussrbf[T <: AnyRef](x: Array[T], centers: Array[T], distance: Metric[T], p: Int): Array[GaussianRadialBasis] = {
    SmileUtils.learnGaussianRadialBasis(x, centers, distance, p)
  }

  /** Learns Gaussian RBF function and centers from data. The centers are
    * chosen as the medoids of CLARANS. The standard deviation (i.e. width)
    * of Gaussian radial basis function is estimated as the width of each
    * cluster multiplied with a given scaling parameter r.
    * @param x the training dataset.
    * @param centers an array to store centers on output. Its length is used as k of CLARANS.
    * @param distance the distance functor.
    * @param r the scaling parameter.
    * @return Gaussian RBF functions with parameter learned from data.
    */
  def gaussrbf[T <: AnyRef](x: Array[T], centers: Array[T], distance: Metric[T], r: Double): Array[GaussianRadialBasis] = {
    SmileUtils.learnGaussianRadialBasis(x, centers, distance, r)
  }
}
