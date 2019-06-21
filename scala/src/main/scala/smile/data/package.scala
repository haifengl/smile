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

import smile.math.MathEx

import scala.language.implicitConversions
import scala.collection.JavaConverters._

/** Data manipulation functions.
  *
  * @author Haifeng Li
  */
package object data {

  implicit def pimpDataset(data: AttributeDataset): DataFrame = DataFrame(data)
  implicit def pimpSparseDataset(data: SparseDataset): PimpedSparseDataset = new PimpedSparseDataset(data)
  implicit def attributeVector2Array(vector: AttributeVector): Array[Double] = vector.vector()

  def summary(x: Array[Double]): Unit = {
    println("min\tq1\tmedian\tmean\tq3\tmax")
    val min = MathEx.min(x)
    val q1 = MathEx.q1(x)
    val median = MathEx.median(x)
    val mean = MathEx.mean(x)
    val q3 = MathEx.q3(x)
    val max = MathEx.max(x)
    println(f"$min%1.5f\t$q1%1.5f\t$median%1.5f\t$mean%1.5f\t$q3%1.5f\t$max%1.5f")
  }

  /** Returns an array of NumericAttribute objects
    *
    * @param n the number of attributes.
    */
  def numericAttributes(n: Int): Array[Attribute] = {
    val attr = new Array[Attribute](n)
    for (i <- 0 until n)
      attr(i) = new NumericAttribute(s"V$i")
    attr
  }
}

package data {

  import smile.math.SparseArray

  private[data] class PimpedSparseDataset(data: SparseDataset) extends Iterable[Datum[SparseArray]] {
    override def iterator : Iterator[Datum[SparseArray]] = data.iterator.asScala

    /** Returns the row names. */
    def rownames: Array[String] = map(_.name).toArray

    /** Unzip the data. If the data contains a response variable, it won't be copied. */
    def unzip: Array[Array[Double]] = data.toArray

    /** Split the data into x and y of Int */
    def unzipInt: (Array[Array[Double]], Array[Int]) = {
      val x = data.toArray
      val y = data.toArray(new Array[Int](data.size))
      (x, y)
    }

    /** Split the data into x and y of Double */
    def unzipDouble: (Array[Array[Double]], Array[Double]) = {
      val x = data.toArray
      val y = data.toArray(new Array[Double](data.size))
      (x, y)
    }
  }
}
