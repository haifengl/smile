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
import smile.data._

/** Data manipulation functions.
  *
  * @author Haifeng Li
  */
package object data {

  implicit def pimpDataset(data: Dataset[Array[Double]]) = new PimpedDataset(data)
  implicit def pimpSparseDataset(data: SparseDataset) = new PimpedSparseDataset(data)
  implicit def pimpArray(data: Array[Double]) = new PimpedArray(data)
  implicit def pimpArray2D(data: Array[Array[Double]]) = new PimpedArray2D(data)
}

package data {

import smile.math.Math

private[data] class PimpedDataset(data: Dataset[Array[Double]]) {
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

private[data] class PimpedSparseDataset(data: SparseDataset) {
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
      val perm = (0 to n).toArray
      Math.permutate(perm)
      (0 until n).map{ i => data(perm(i)) }.toArray
    }

    /** Sampling the data.
      * @param f the fraction of samples.
      * @return samples
      */
    def sample(f: Double): Array[Double] = {
      val n = Math.round(data.length * f).toInt
      val perm = (0 to n).toArray
      Math.permutate(perm)
      (0 until n).map{ i => data(perm(i)) }.toArray
    }
  }

  private[data] class PimpedArray2D(data: Array[Array[Double]]) {
    /** Get an element */
    def apply(rows: Int*): Array[Array[Double]] = {
      rows.map { row => data(row) }.toArray
    }

    /** Get a range of array */
    def apply(rows: Range): Array[Array[Double]] = {
      rows.map { row => data(row) }.toArray
    }

    /** Get a range of array */
    def apply(rows: Range, cols: Range): Array[Array[Double]] = {
      rows.map { row =>
        val x = data(row)
        cols.map { col => x(col) }.toArray
      }.toArray
    }

    def nrows: Int = data.length

    def ncols: Int = data(0).length

    /** Get a row of array */
    def row(i: Int*): Array[Array[Double]] = apply(i: _*)

    /** Get rows of array */
    def row(i: Range): Array[Array[Double]] = apply(i)

    /** Get a column of array */
    def col(j: Int*): Array[Array[Double]] = {
      data.map { x =>
        j.map { col => x(col) }.toArray
      }.toArray
    }

    /** Get columns of array */
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
      val perm = (0 to n).toArray
      Math.permutate(perm)
      (0 until n).map{ i => data(perm(i)) }.toArray
    }

    /** Sampling the data.
      * @param f the fraction of samples.
      * @return samples
      */
    def sample(f: Double): Array[Array[Double]] = {
      val n = Math.round(nrows * f).toInt
      val perm = (0 to n).toArray
      Math.permutate(perm)
      (0 until n).map{ i => data(perm(i)) }.toArray
    }
  }
}
