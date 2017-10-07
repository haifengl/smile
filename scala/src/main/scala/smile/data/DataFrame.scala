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

/**
  * Immutable data frame.
  * @param data underlying attribute dataset.
  */
case class DataFrame(data: AttributeDataset) extends Dynamic {
  type Row = data.Row
  lazy val rows = data.data().asScala.map(_.asInstanceOf[Row])

  /** Returns the attribute list. */
  def attributes = data.attributes()
  /** Returns the response variable. */
  def response = data.response()
  /** Returns the response attribute. */
  def responseAttribute = data.responseAttribute()

  /** Returns the size of data frame. */
  def size: Int = data.size
  /** Finds the first row satisfying a predicate. */
  def find(p: (Row) => Boolean): Option[Row] = rows.find(p)
  /** Tests if a predicate holds for at least one row of data frame. */
  def exists(p: (Row) => Boolean): Boolean = rows.exists(p)
  /** Tests if a predicate holds for all rows of data frame. */
  def forall(p: (Row) => Boolean): Boolean = rows.forall(p)
  /** Applies a function for its side-effect to every row. */
  def foreach[U](p: (Row) => U): Unit = rows.foreach(p)
  /** Tests if the data frame is empty. */
  def isEmpty: Boolean = rows.isEmpty

  /** Builds a new data collection by applying a function to all rows. */
  def map[U](p: (Row) => U): Traversable[U] = rows.map(p)
  /** Builds a new data frame by applying a function to all rows. */
  def map(attributes: Array[Attribute])(p: (Row) => Array[Double]): DataFrame = {
    val newData = new AttributeDataset(data.name, attributes)
    rows.foreach { row =>
      val newRow = newData.add(p(row))
      newRow.name = row.name
      newRow.description = row.description
      newRow.weight = row.weight
      newRow.timestamp = row.timestamp
    }
    DataFrame(newData)
  }

  /** Builds a new data frame by applying a function to all rows. */
  def map(attributes: Array[Attribute], response: Attribute)(p: (Row) => (Array[Double], Double)): DataFrame = {
    val newData = new AttributeDataset(data.name, attributes, response)
    rows.foreach { row =>
      val (x, y) = p(row)
      val newRow = newData.add(x, y)
      newRow.name = row.name
      newRow.description = row.description
      newRow.weight = row.weight
      newRow.timestamp = row.timestamp
    }
    DataFrame(newData)
  }

  /** Selects all rows which satisfy a predicate. */
  def filter(p: (Row) => Boolean): DataFrame = {
    val dataset = new AttributeDataset(data.name, data.attributes, data.response)
    rows.foreach { row =>
      if (p(row)) dataset.add(row)
    }
    DataFrame(dataset)
  }

  /** Partitions this DataFrame in two according to a predicate.
    *
    *  @param p the predicate on which to partition.
    *  @return  a pair of DataFrames: the first DataFrame consists of all elements that
    *           satisfy the predicate `p` and the second DataFrame consists of all elements
    *           that don't. The relative order of the elements in the resulting DataFramess
    *           is the same as in the original DataFrame.
    */
  def partition(p: (Row) => Boolean): (DataFrame, DataFrame) = {
    val l = new AttributeDataset(data.name, data.attributes, data.response)
    val r = new AttributeDataset(data.name, data.attributes, data.response)
    rows.foreach { row =>
      if (p(row)) l.add(row) else r.add(row)
    }
    (l, r)
  }

  /** Partitions the DataFrame into a map of DataFrames according to
    * some discriminator function.
    *
    * @param f the discriminator function.
    * @tparam K the type of keys returned by the discriminator function.
    * @return A map from keys to DataFrames
    */
  def groupBy[K](f: (Row) => K): scala.collection.immutable.Map[K, DataFrame] = {
    val groups = rows.groupBy(f)
    groups.mapValues { rows =>
      val group = new AttributeDataset(data.name, data.attributes, data.response)
      group.description = data.description
      rows.foreach { row => group.add(row) }
      DataFrame(group)
    }
  }

  /** Partitions the DataFrame into a map of DataFrames according to
    * the value of a column.
    *
    * @param col the column for grouping.
    * @return A map from keys to DataFrames
    */
  def groupBy(col: String): scala.collection.immutable.Map[Double, DataFrame] = {
    val i = colnames.indexOf(col)
    val f = (row: Row) => row.x(i)
    groupBy(f)
  }

  /** Partitions the DataFrame into a map of DataFrames according to
    * the value of a pair of columns.
    *
    * @param c1 the first column for grouping.
    * @param c2 the second column for grouping.
    * @return A map from keys to DataFrames
    */
  def groupBy(c1: String, c2: String): scala.collection.immutable.Map[(Double, Double), DataFrame] = {
    val i = colnames.indexOf(c1)
    val j = colnames.indexOf(c2)
    val f = (row: Row) => (row.x(i), row.x(j))
    groupBy(f)
  }

  /** Partitions the DataFrame into a map of DataFrames according to
    * the value of a pair of columns.
    *
    * @param c1 the first column for grouping.
    * @param c2 the second column for grouping.
    * @return A map from keys to DataFrames
    */
  def groupBy(c1: String, c2: String, c3: String): scala.collection.immutable.Map[(Double, Double, Double), DataFrame] = {
    val i = colnames.indexOf(c1)
    val j = colnames.indexOf(c2)
    val k = colnames.indexOf(c3)
    val f = (row: Row) => (row.x(i), row.x(j), row.x(k))
    groupBy(f)
  }

  /** Returns the row names. */
  val rownames: Array[String] = map(_.name).toArray

  /** Returns the columns names. */
  val colnames: Array[String] = data.attributes().map(_.getName)

  /** Returns the columns names. */
  def names: Array[String] = colnames

  /** Returns a row. */
  def apply(row: Int): Datum[Array[Double]] = data.get(row)

  /** Returns a new data frame of given row range. */
  def apply(from: Int, to: Int): DataFrame = DataFrame(data.range(from, to))

  /** Returns a column. */
  def apply(col: String): AttributeVector = data.column(col)

  /** Returns a new data frame of selected columns. */
  def apply(cols: String*): DataFrame = DataFrame(data.columns(cols: _*))

  /** Returns a new data frame without given columns. */
  def remove(cols: String*): DataFrame = DataFrame(data.remove(cols: _*))

  /** Returns a column. */
  def applyDynamic(col: String): AttributeVector = apply(col)

  /** Returns a column. */
  def selectDynamic(col: String): AttributeVector = apply(col)

  /** Unzip the data. If the data contains a response variable, it won't be copied. */
  def unzip: Array[Array[Double]] = data.x

  /** Split the data into x and y of Int */
  def unzipInt: (Array[Array[Double]], Array[Int]) = (data.x, data.labels)

  /** Split the data into x and y of Double */
  def unzipDouble: (Array[Array[Double]], Array[Double]) = (data.x, data.y)
}
