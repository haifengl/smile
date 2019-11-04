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
 
package smile.clustering

import scala.util.Random
import scala.util.control.Breaks._
import smile.math.distance.{Distance, Hamming, HammingDistance}

/** K-Modes clustering.
  *
  * @author Beck Gaël
  */
class KModes() {//extends CentroidClustering[Array[Int], Array[Int]] {

}

/** K-Modes clustering.
  *
  * @author Beck Gaël
  */
object KModes {

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
  def apply(data: Array[Array[Int]], k: Int, epsilon: Double, maxIter: Int = 100, metric: Distance[Array[Int]] = new Hamming) : KModes = {
    val n = data.length
    val p = data.head.size

    val modes = Array.fill(k) {
      Array.fill(p) { Random.nextInt(2) }
    }

    val y = Array.ofDim[Int](n)
    val size = Array.ofDim[Int](k)
    val dist = Array.ofDim[Double](n)

    var distortion = Double.MaxValue

    breakable {
      for (iter <- 0 until maxIter) {
        var wcss = 0.0

        for (i <- 0 until n) {
          val (d, c) = nearest(data(i), modes)
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
          dist.zipWithIndex.zip(y).groupBy { case (_, cluster) => cluster }.foreach { case (cluster, aggregates) =>
            import Ordering.Double.TotalOrdering
            val mode = aggregates.minBy(_._1)._2
            modes(cluster) = data(mode)
          }
        }

        if (distortion <= wcss) break else distortion = wcss
      }
    }

    return new KModes()
  }

  /**
    * Return the nearest mode and distance for a specific point.
    */
  def nearest(x: Array[Int], modes: Array[Array[Int]]): (Int, Int) = {
    modes.map { mode => HammingDistance.d(mode, x) }.zipWithIndex.minBy(_._1)
  }
}
