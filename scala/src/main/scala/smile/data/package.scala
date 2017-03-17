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

import smile.math.Math

import scala.language.implicitConversions
import scala.collection.JavaConverters._

/** Data manipulation functions.
  *
  * @author Haifeng Li
  */
package object data {

  implicit def pimpDataset(data: AttributeDataset): PimpedDataset = new PimpedDataset(data)
  implicit def pimpSparseDataset(data: SparseDataset): PimpedSparseDataset = new PimpedSparseDataset(data)

  def summary(x: Array[Double]): Unit = {
    println("min\tq1\tmedian\tmean\tq3\tmax")
    val min = Math.min(x)
    val q1 = Math.q1(x)
    val median = Math.median(x)
    val mean = Math.mean(x)
    val q3 = Math.q3(x)
    val max = Math.max(x)
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

  import smile.math.{SparseArray, Math}

  private[data] class PimpedDataset(data: AttributeDataset) extends Iterable[Datum[Array[Double]]] {
    override def iterator : Iterator[Datum[Array[Double]]] = data.iterator.asScala

    /** Returns the row names. */
    def rownames: Array[String] = map(_.name).toArray

    /** Returns the columns names. */
    def colnames: Array[String] = data.attributes().map(_.getName)

    /** Returns the columns names. */
    def names: Array[String] = colnames

    def summary(): Unit = {
      println(" \tmin\tq1\tmedian\tmean\tq3\tmax")
      val matrix = unzip
      for (i <- colnames.indices) {
        val x = matrix.map(_(i))
        val min = Math.min(x)
        val q1 = Math.q1(x)
        val median = Math.median(x)
        val mean = Math.mean(x)
        val q3 = Math.q3(x)
        val max = Math.max(x)
        println(f"${colnames(i)}\t$min%1.5f\t$q1%1.5f\t$median%1.5f\t$mean%1.5f\t$q3%1.5f\t$max%1.5f")
      }
    }

    /** Shows the first few rows.
      * Cannot use default parameter value, otherwise it confuses with iterator.head.
      */
    def head(n: Int): Unit = {
      println(colnames.mkString("\t"))
      for (i <- 0 until Math.min(data.size, n)) {
        val x = data.get(i).x
        println(x.map{xi => f"$xi%1.4f"}.mkString("\t"))
      }
    }

    /** Shows the last few rows.
      * Cannot use default parameter value, otherwise it confuses with iterator.tail.
      */
    def tail(n: Int): Unit = {
      println(colnames.mkString("\t"))
      for (i <- Math.max(0, data.size - n) until data.size) {
        val x = data.get(i).x
        println(x.map{xi => f"$xi%1.4f"}.mkString("\t"))
      }
    }

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
