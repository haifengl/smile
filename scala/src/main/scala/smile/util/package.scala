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

import scala.language.implicitConversions
import com.typesafe.scalalogging.LazyLogging
import smile.math.distance.{EuclideanDistance, Distance}

/** Utility functions.
  *
  * @author Haifeng Li
  */
package object util extends LazyLogging {
  /** Wraps Scala lambda as Java's. */
  implicit def toJavaFunction[T, R](f: Function1[T, R]): java.util.function.Function[T, R] = t => f(t)

  /** Wraps Scala lambda as Java's. */
  implicit def toJavaBiFunction[T, U, R](f: Function2[T, U, R]): java.util.function.BiFunction[T, U, R] = (t, u) => f(t, u)

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
    def apply[A](message: String)(f: => A) = {
      val s = System.nanoTime
      val ret = f
      if (echo) {
        val time = System.nanoTime - s
        val micron = (time % 1000000000) / 1000
        val seconds = time / 1000000000
        val duration = String.format("%d:%02d:%02d.%d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60), micron)
        logger.info("{} runtime: {}", message, duration)
      }
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
}
