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

package smile.data

import java.util.Optional
import java.util.stream.IntStream

/**
  * Pimped data frame with Scala style operations.
  * @param data underlying data frame.
  */
case class DataFrameOps(data: DataFrame) {
  /** Selects a new DataFrame with given column indices. */
  def select(range: Range): DataFrame = data.select(range.toArray: _*)

  /** Returns a new DataFrame without given column indices. */
  def drop(range: Range): DataFrame = data.drop(range.toArray: _*)

  /** Returns a new data frame with row indexing. */
  def of(range: Range): DataFrame = data.of(range.toArray: _*)

  /** Finds the first row satisfying a predicate. */
  def find(p: (Tuple) => Boolean): Optional[Tuple] = data.stream().filter(t => p(t)).findAny()
  /** Tests if a predicate holds for at least one row of data frame. */
  def exists(p: (Tuple) => Boolean): Boolean = data.stream.anyMatch(t => p(t))
  /** Tests if a predicate holds for all rows of data frame. */
  def forall(p: (Tuple) => Boolean): Boolean = data.stream.allMatch(t => p(t))
  /** Applies a function for its side-effect to every row. */
  def foreach[U](p: (Tuple) => U): Unit = data.stream.forEach(t => p(t))

  /** Builds a new data collection by applying a function to all rows. */
  def map[U](p: (Tuple) => U): Iterable[U] = (0 until data.size).map(i => p(data(i)))

  /** Selects all rows which satisfy a predicate. */
  def filter(p: (Tuple) => Boolean): DataFrame = {
    val index = IntStream.range(0, data.size).filter(i => p(data(i))).toArray
    data.of(index: _*)
  }

  /** Partitions this DataFrame in two according to a predicate.
    *
    *  @param p the predicate on which to partition.
    *  @return  a pair of DataFrames: the first DataFrame consists of all elements that
    *           satisfy the predicate `p` and the second DataFrame consists of all elements
    *           that don't. The relative order of the elements in the resulting DataFramess
    *           is the same as in the original DataFrame.
    */
  def partition(p: (Tuple) => Boolean): (DataFrame, DataFrame) = {
    val l = new scala.collection.mutable.ArrayBuffer[Int]
    val r = new scala.collection.mutable.ArrayBuffer[Int]
    IntStream.range(0, data.size).forEach { i =>
      if (p(data(i))) l :+ i else r :+ i
    }
    (data.of(l.toArray: _*), data.of(r.toArray: _*))
  }

  /** Partitions the DataFrame into a map of DataFrames according to
    * some discriminator function.
    *
    * @param f the discriminator function.
    * @tparam K the type of keys returned by the discriminator function.
    * @return A map from keys to DataFrames
    */
  def groupBy[K](f: (Tuple) => K): scala.collection.immutable.Map[K, DataFrame] = {
    val groups = (0 until data.size).groupBy(i => f(data(i)))
    groups.view.mapValues(index => data.of(index: _*)).toMap
  }
}
