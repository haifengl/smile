/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.clustering

import scala.util.Random
import scala.util.control.Breaks._
import smile.math.distance.{Distance, Hamming}

/** K-Modes clustering. K-Modes is the binary equivalent for K-Means.
  * The mean update for centroids is replace by the mode one which is
  * a majority vote among element of each cluster.
  *
  * Complexity is O(k.t.n) with Hamming distance, quadratic with other metrics.
  *
  * <h2>References</h2>
  *  - Joshua Zhexue Huang. Clustering Categorical Data with k-Modes.
  *    http://www.irma-international.org/viewtitle/10828/
  *  - Antara Prakash, et al. Review on K-Mode Clustering.
  *    https://www.ijecs.in/index.php/ijecs/article/download/3058/2834/
  *  - Python implementations of the k-modes and k-prototypes clustering algorithms for clustering categorical data.
  *    https://pypi.python.org/pypi/kmodes/
  *
  * @param data the dataset
  * @param k number of clusters
  * @param epsilon distance between ancient modes and update modes under which we consider than the algorithm have converged, if and only if all every mode have converged
  * @param maxIter number maximal of iteration
  * @param metric the dissimilarity measure used
  *
  * @author Beck Gaël
  */
class KModes(data: Array[Array[Int]], k: Int, epsilon: Double, maxIter: Int = 100, metric: Distance[Array[Int]] = new Hamming()) extends PartitionClustering[Array[Int]] with Serializable {

  /** Dimension */
  val p = data.head.size

  /**
    * The modes of each cluster.
    */
  val modes = Array.fill(k) {
    Array.fill(p) { Random.nextInt(2) }
  }

  /**
    * The cluster labels of data.
    */
  y = Array.ofDim[Int](data.length)

  private var distortion: Double = Double.MaxValue

  /**
    * The distance between data and mode.
    */
  private val dist: Array[Double] = Array.ofDim[Double](data.length)

  /** The size of each cluster. */
  size = Array.ofDim[Int](k)

  breakable { for (iter <- 0 until maxIter) {
    var wcss = 0.0

    for (i <- 0 until data.length) {
      val (d, c) = nearest(data(i))
      y(i) = c
      dist(i) = d
      wcss += d
    }

    modes.foreach { mode => java.util.Arrays.fill(mode, 0) }
    java.util.Arrays.fill(size, 0)

	  // Fast way if we use Hamming distance
    if (metric.isInstanceOf[Hamming]) {
      for (i <- 0 until data.length) {
        for (j <- 0 until p) modes(y(i))(j) += data(i)(j)
        size(y(i)) += 1
      }
      
      for (i <- 0 until modes.length) {
        val mode = modes(i)
        for (j <- 0 until mode.length) {
          if (mode(j) * 2 >= size(i)) mode(j) = 1 else mode(j) = 0
        }
      }
    } else {
      // search mode that is the data point closest to centroid
      dist.zipWithIndex.zip(y).groupBy{ case (_, cluster) => cluster }.foreach { case (cluster, aggregates) =>
        val mode = aggregates.minBy(_._1)._2
        modes(cluster) = data(mode)
      }
    }

    if (distortion <= wcss) break else distortion = wcss
  } }

  /**
    * Return the nearest mode and distance for a specific point.
    **/
  private def nearest(x: Array[Int]): (Double, Int) = {
    modes.map { mode => metric.d(mode, x) }.zipWithIndex.minBy(_._1)
  }

	/**
    * Return the nearest mode for a specific point.
    **/
  override def predict(x: Array[Int]): Int = {
    nearest(x)._2
  }
}
