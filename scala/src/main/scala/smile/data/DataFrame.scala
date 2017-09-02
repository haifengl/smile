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

  def size: Int = data.size
  def find(p: (Row) => Boolean): Option[Row] = rows.find(p)
  def exists(p: (Row) => Boolean): Boolean = rows.exists(p)
  def forall(p: (Row) => Boolean): Boolean = rows.forall(p)
  def map[U](p: (Row) => U): Traversable[U] = rows.map(p)
  def foreach[U](p: (Row) => U): Unit = rows.foreach(p)
  def isEmpty: Boolean = rows.isEmpty

  def filter(p: (Row) => Boolean): DataFrame = {
    val dataset = new AttributeDataset(data.name, data.attributes, data.response)
    rows.foreach { row =>
      if (p(row)) dataset.add(row)
    }
    DataFrame(dataset)
  }

  /** Returns the row names. */
  val rownames: Array[String] = map(_.name).toArray

  /** Returns the columns names. */
  val colnames: Array[String] = data.attributes().map(_.getName)

  /** Returns the columns names. */
  def names: Array[String] = colnames

  /** Returns a column. */
  def apply(col: String): AttributeVector = data.column(col)

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
