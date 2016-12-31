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
import scala.reflect.ClassTag

/** Data manipulation functions.
  *
  * @author Haifeng Li
  */
package object data {

  implicit def pimpDataset(data: AttributeDataset): PimpedDataset = new PimpedDataset(data)
  implicit def pimpSparseDataset(data: SparseDataset): PimpedSparseDataset = new PimpedSparseDataset(data)
  implicit def pimpDIntArray(data: Array[Int]): PimpedArray[Int] = new PimpedArray[Int](data)
  implicit def pimpDoubleArray(data: Array[Double]): PimpedArray[Double] = new PimpedArray[Double](data)
  implicit def pimpArray2D(data: Array[Array[Double]]): PimpedArray2D = new PimpedArray2D(data)

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
        val x = matrix \ i
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

  abstract class PimpedArrayLike[T: ClassTag] {

    val data: Array[T]

    /** Get an element */
    def apply(rows: Int*): Array[T] = rows.map(row => data(row)).toArray

    /** Get a range of array */
    def apply(rows: Range): Array[T] = rows.map(row => data(row)).toArray

    /** Sampling the data.
      * @param n the number of samples.
      * @return samples
      */
    def sample(n: Int): Array[T] = {
      val perm = data.indices.toArray
      Math.permutate(perm)
      (0 until n).map(i => data(perm(i))).toArray
    }

    /** Sampling the data.
      * @param f the fraction of samples.
      * @return samples
      */
    def sample(f: Double): Array[T] = sample(Math.round(data.length * f).toInt)

  }

  private[data] class PimpedArray[T](val data: Array[T])(implicit val tag: ClassTag[T])
    extends PimpedArrayLike[T]

  private[data] class PimpedArray2D(val data: Array[Array[Double]])(implicit val tag: ClassTag[Array[Double]])
    extends PimpedArrayLike[Array[Double]] {

    def nrows: Int = data.length

    def ncols: Int = data(0).length

    /** Returns a submatrix. */
    def apply(rows: Range, cols: Range): Array[Array[Double]] = rows.map { row =>
      val x = data(row)
      cols.map { col => x(col) }.toArray
    }.toArray

    /** Returns a column. */
    def \(col: Int): Array[Double] = data.map(_(col))

    /** Returns multiple rows. */
    def row(i: Int*): Array[Array[Double]] = apply(i: _*)

    /** Returns a range of rows. */
    def row(i: Range): Array[Array[Double]] = apply(i)

    /** Returns multiple columns. */
    def col(j: Int*): Array[Array[Double]] = data.map { x =>
      j.map { col => x(col) }.toArray
    }

    /** Returns a range of columns. */
    def col(j: Range): Array[Array[Double]] = data.map { x =>
      j.map { col => x(col) }.toArray
    }

  }
}
