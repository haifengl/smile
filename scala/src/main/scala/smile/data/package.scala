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

import scala.language.implicitConversions
import scala.collection.JavaConverters._

/** Data manipulation functions.
  *
  * @author Haifeng Li
  */
package object data {

  implicit def pimpDataset(data: AttributeDataset) = new PimpedDataset(data)
  implicit def pimpSparseDataset(data: SparseDataset) = new PimpedSparseDataset(data)
  implicit def pimpArray(data: Array[Double]) = new PimpedArray(data)
  implicit def pimpArray2D(data: Array[Array[Double]]) = new PimpedArray2D(data)
}

package data {

import smile.math.{SparseArray, Math}

private[data] class PimpedDataset(data: AttributeDataset) extends Iterable[Datum[Array[Double]]] {
  override def iterator : Iterator[Datum[Array[Double]]] = data.iterator.asScala

  /** Returns the row names. */
  def rownames: Array[String] = {
    map(_.name).toArray
  }

  /** Returns the columns names. */
  def colnames: Array[String] = {
    data.attributes().map(_.getName).toArray
  }

  /** Unzip the data. If the data contains a response variable, it won't be copied. */
  def unzip: Array[Array[Double]] = {
    data.toArray(new Array[Array[Double]](data.size))
  }

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
  def rownames: Array[String] = {
    map(_.name).toArray
  }

  /** Unzip the data. If the data contains a response variable, it won't be copied. */
  def unzip: Array[Array[Double]] = {
    data.toArray
  }

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

private[data] class PimpedArray(data: Array[Double]) {
    /** Get an element */
    def apply(rows: Int*): Array[Double] = {
      rows.map { row => data(row) }.toArray
    }

    /** Get a range of array */
    def apply(rows: Range): Array[Double] = {
      rows.map { row => data(row) }.toArray
    }

    /** Sampling the data.
      * @param n the number of samples.
      * @return samples
      */
    def sample(n: Int): Array[Double] = {
      val perm = (0 until data.length).toArray
      Math.permutate(perm)
      (0 until n).map{ i => data(perm(i)) }.toArray
    }

    /** Sampling the data.
      * @param f the fraction of samples.
      * @return samples
      */
    def sample(f: Double): Array[Double] = {
      val perm = (0 until data.length).toArray
      Math.permutate(perm)
      val n = Math.round(data.length * f).toInt
      (0 until n).map{ i => data(perm(i)) }.toArray
    }
  }

  private[data] class PimpedArray2D(data: Array[Array[Double]]) {
    def nrows: Int = data.length

    def ncols: Int = data(0).length

    /** Returns multiple rows. */
    def apply(rows: Int*): Array[Array[Double]] = {
      rows.map { row => data(row) }.toArray
    }

    /** Returns a range of rows. */
    def apply(rows: Range): Array[Array[Double]] = {
      rows.map { row => data(row) }.toArray
    }

    /** Returns a submatrix. */
    def apply(rows: Range, cols: Range): Array[Array[Double]] = {
      rows.map { row =>
        val x = data(row)
        cols.map { col => x(col) }.toArray
      }.toArray
    }

    /** Returns a column. */
    def ::(col: Int): Array[Double] = {
      data.map(_(col)).toArray
    }

    /** Returns multiple rows. */
    def row(i: Int*): Array[Array[Double]] = apply(i: _*)

    /** Returns a range of rows. */
    def row(i: Range): Array[Array[Double]] = apply(i)

    /** Returns multiple columns. */
    def col(j: Int*): Array[Array[Double]] = {
      data.map { x =>
        j.map { col => x(col) }.toArray
      }.toArray
    }

    /** Returns a range of columns. */
    def col(j: Range): Array[Array[Double]] = {
      data.map { x =>
        j.map { col => x(col) }.toArray
      }.toArray
    }

    /** Sampling the data.
      * @param n the number of samples.
      * @return samples
      */
    def sample(n: Int): Array[Array[Double]] = {
      val perm = (0 to data.length).toArray
      Math.permutate(perm)
      (0 until n).map{ i => data(perm(i)) }.toArray
    }

    /** Sampling the data.
      * @param f the fraction of samples.
      * @return samples
      */
    def sample(f: Double): Array[Array[Double]] = {
      val perm = (0 to data.length).toArray
      Math.permutate(perm)
      val n = Math.round(nrows * f).toInt
      (0 until n).map{ i => data(perm(i)) }.toArray
    }
  }
}
