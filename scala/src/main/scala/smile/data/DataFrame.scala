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

package smile.data

import scala.language.dynamics
import scala.language.implicitConversions
import scala.collection.JavaConverters._
import smile.math.Math

/**
  * Immutable data frame.
  * @param data underlying attribute dataset.
  */
case class DataFrame(data: AttributeDataset) extends Dynamic with Traversable[Row] {
  val rows: List[Row] = data.data().asScala.toList

  override def copyToArray[B >: Row](xs: Array[B], start: Int, len: Int): Unit = rows.copyToArray(xs, start, len)
  override def find(p: (Row) => Boolean): Option[Row] = rows.find(p)
  override def exists(p: (Row) => Boolean): Boolean = rows.exists(p)
  override def forall(p: (Row) => Boolean): Boolean = rows.forall(p)
  override def foreach[U](p: (Row) => U): Unit = rows.foreach(p)
  override def hasDefiniteSize: Boolean = rows.hasDefiniteSize
  override def isEmpty: Boolean = rows.isEmpty
  override def seq: Traversable[Row] = rows.seq
  override def toIterator: Iterator[Row] = rows.toIterator
  override def toStream: Stream[Row] = rows.toStream

  override def size: Int = data.size
  def iterator : Iterator[Row] = rows.iterator

  /** Returns the row names. */
  val rownames: Array[String] = map(_.name).toArray

  /** Returns the columns names. */
  val colnames: Array[String] = data.attributes().map(_.getName)

  /** Returns the columns names. */
  def names: Array[String] = colnames

  /** Returns a column. */
  def apply(col: String): Array[Double] = data.column(col)

  /** Returns a column. */
  def applyDynamic(col: String): Array[Double] = apply(col)

  /** Returns a column. */
  def selectDynamic(col: String): Array[Double] = apply(col)

  /** Unzip the data. If the data contains a response variable, it won't be copied. */
  def unzip: Array[Array[Double]] = data.toArray(new Array[Array[Double]](data.size))

  /** Split the data into x and y of Int */
  def unzipInt: (Array[Array[Double]], Array[Int]) = {
    val x = data.toArray(new Array[Array[Double]](data.size))
    val y = data.toArray(new Array[Int](data.size))
    (x, y)
  }

  /** Split the data into x and y of Double */
  def unzipDouble: (Array[Array[Double]], Array[Double]) = {
    val x = data.toArray(new Array[Array[Double]](data.size))
    val y = data.toArray(new Array[Double](data.size))
    (x, y)
  }
}
