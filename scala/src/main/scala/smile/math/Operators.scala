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

package smile.math

import scala.language.implicitConversions
import smile.math.matrix.{DenseMatrix, ColumnMajorMatrix}
import smile.math.special._
import smile.stat.hypothesis._
import smile.stat.distribution.Distribution

import scala.reflect.ClassTag

/** High level feature selection operators.
  *
  * @author Haifeng Li
  */
trait Operators {
  implicit def pimpDouble(x: Double) = new PimpedDouble(x)
  implicit def pimpIntArray(data: Array[Int]) = new PimpedArray[Int](data)
  implicit def pimpDoubleArray(data: Array[Double]) = new PimpedDoubleArray(data)
  implicit def pimpArray2D(data: Array[Array[Double]]) = new PimpedArray2D(data)
  implicit def pimpMatrix(matrix: DenseMatrix) = new PimpedMatrix(matrix)

  implicit def Ax2Array(ax: Ax) = ax.toArray
  implicit def Atx2Array(atx: Atx) = atx.toArray
  implicit def By2Array(by: By) = by.toArray

  implicit def Ax2PimpedArray(ax: Ax) = new PimpedDoubleArray(ax.toArray)
  implicit def AtxPimped2Array(atx: Atx) = new PimpedDoubleArray(atx.toArray)
  implicit def By2PimpedArray(by: By) = new PimpedDoubleArray(by.toArray)

  /** The beta function, also called the Euler integral of the first kind.
    *
    * B(x, y) = <i><big>&#8747;</big><sub><small>0</small></sub><sup><small>1</small></sup> t<sup>x-1</sup> (1-t)<sup>y-1</sup>dt</i>
    *
    * for x, y &gt; 0 and the integration is over [0,1].The beta function is symmetric, i.e. B(x,y) = B(y,x).
    */
  def beta(x: Double, y: Double): Double = Beta.beta(x, y)

  /** The error function (also called the Gauss error function) is a special
    * function of sigmoid shape which occurs in probability, statistics, materials
    * science, and partial differential equations. It is defined as:
    *
    * erf(x) = <i><big>&#8747;</big><sub><small>0</small></sub><sup><small>x</small></sup> e<sup>-t<sup>2</sup></sup>dt</i>
    *
    * The complementary error function, denoted erfc, is defined as erfc(x) = 1 - erf(x).
    * The error function and complementary error function are special cases of the
    * incomplete gamma function.
    */
  def erf(x: Double): Double = Erf.erf(x)

  /** The complementary error function. */
  def erfc(x: Double): Double = Erf.erfc(x)

  /** The complementary error function with fractional error everywhere less
    * than 1.2 &times; 10<sup>-7</sup>. This concise routine is faster than erfc.
    */
  def erfcc(x: Double): Double = Erf.erfcc(x)

  /** The inverse error function. */
  def inverf(p: Double): Double = Erf.inverf(p)

  /** The inverse complementary error function. */
  def inverfc(p: Double): Double = Erf.inverfc(p)

  /** Gamma function. Lanczos approximation (6 terms). */
  def gamma(x: Double): Double = Gamma.gamma(x)

  /** log of the Gamma function. Lanczos approximation (6 terms) */
  def lgamma(x: Double): Double = Gamma.lgamma(x)

  /** The digamma function is defined as the logarithmic derivative of the gamma function. */
  def digamma(x: Double): Double = Gamma.digamma(x)

  /** One-sample chisq test. Given the array x containing the observed numbers of events,
    * and an array prob containing the expected probabilities of events, and given
    * the number of constraints (normally one), a small value of p-value
    * indicates a significant difference between the distributions.
    */
  def chisqtest(x: Array[Int], prob: Array[Double], constraints: Int = 1): ChiSqTest = ChiSqTest.test(x, prob, constraints)

  /** Two-sample chisq test. Given the arrays x and y, containing two
    * sets of binned data, and given one constraint, a small value of
    * p-value indicates a significant difference between two distributions.
    */
  def chisqtest2(x: Array[Int], y: Array[Int], constraints: Int = 1): ChiSqTest = ChiSqTest.test(x, y, constraints)

  /** Test if the arrays x and y have significantly different variances.
    * Small values of p-value indicate that the two arrays have significantly
    * different variances.
    */
  def ftest(x: Array[Double], y: Array[Double]): FTest = FTest.test(x, y)

  /** Independent one-sample t-test whether the mean of a normally distributed
    * population has a value specified in a null hypothesis. Small values of
    * p-value indicate that the array has significantly different mean.
    */
  def ttest(x: Array[Double], mean: Double): TTest = TTest.test(x, mean)

  /** Given the paired arrays x and y, test if they have significantly
    * different means. Small values of p-value indicate that the two arrays
    * have significantly different means.
    */
  def ttest(x: Array[Double], y: Array[Double]): TTest = TTest.pairedTest(x, y)

  /** Test if the arrays x and y have significantly different means.  Small
    * values of p-value indicate that the two arrays have significantly
    * different means.
    * @param equalVariance true if the data arrays are assumed to be
    *                      drawn from populations with the same true variance. Otherwise, The data
    *                      arrays are allowed to be drawn from populations with unequal variances.
    */
  def ttest2(x: Array[Double], y: Array[Double], equalVariance: Boolean = false): TTest = TTest.test(x, y, equalVariance)

  /** The one-sample KS test for the null hypothesis that the data set x
    * is drawn from the given distribution. Small values of p-value show that
    * the cumulative distribution function of x is significantly different from
    * the given distribution. The array x is modified by being sorted into
    * ascending order.
    */
  def kstest(x: Array[Double], y: Distribution): KSTest = KSTest.test(x, y)

  /** The two-sample KS test for the null hypothesis that the data sets
    * are drawn from the same distribution. Small values of p-value show that
    * the cumulative distribution function of x is significantly different from
    * that of y. The arrays x and y are modified by being sorted into
    * ascending order.
    */
  def kstest(x: Array[Double], y: Array[Double]): KSTest = KSTest.test(x, y)

  /** Pearson correlation coefficient test. */
  def pearsontest(x: Array[Double], y: Array[Double]): CorTest = CorTest.pearson(x, y)

  /** Spearman rank correlation coefficient test. The Spearman Rank Correlation
    * Coefficient is a form of the Pearson coefficient with the data converted
    * to rankings (ie. when variables are ordinal). It can be used when there
    * is non-parametric data and hence Pearson cannot be used.
    *
    * The raw scores are converted to ranks and the differences between
    * the ranks of each observation on the two variables are calculated.
    *
    * The p-value is calculated by approximation, which is good for n &gt; 10.
    */
  def spearmantest(x: Array[Double], y: Array[Double]): CorTest = CorTest.spearman(x, y)

  /** Kendall rank correlation test. The Kendall Tau Rank Correlation
    * Coefficient is used to measure the degree of correspondence
    * between sets of rankings where the measures are not equidistant.
    * It is used with non-parametric data. The p-value is calculated by
    * approximation, which is good for n &gt; 10.
    */
  def kendalltest(x: Array[Double], y: Array[Double]): CorTest = CorTest.kendall(x, y)

  /** Given a two-dimensional contingency table in the form of an array of
    * integers, returns Chi-square test for independence. The rows of contingency table
    * are labels by the values of one nominal variable, the columns are labels
    * by the values of the other nominal variable, and whose entries are
    * non-negative integers giving the number of observed events for each
    * combination of row and column. Continuity correction
    * will be applied when computing the test statistic for 2x2 tables: one half
    * is subtracted from all |O-E| differences. The correlation coefficient is
    * calculated as Cramer's V.
    */
  def chisqtest(table: Array[Array[Int]]): CorTest = CorTest.chisq(table)
}


private[math] abstract class PimpedArrayLike[T: ClassTag] {

  val a: Array[T]

  /** Get an element */
  def apply(rows: Int*): Array[T] = rows.map(row => a(row)).toArray

  /** Get a range of array */
  def apply(rows: Range): Array[T] = rows.map(row => a(row)).toArray

  /** Sampling the data.
    * @param n the number of samples.
    * @return samples
    */
  def sample(n: Int): Array[T] = {
    val perm = a.indices.toArray
    Math.permutate(perm)
    (0 until n).map(i => a(perm(i))).toArray
  }

  /** Sampling the data.
    * @param f the fraction of samples.
    * @return samples
    */
  def sample(f: Double): Array[T] = sample(Math.round(a.length * f).toInt)
}

private[math] class PimpedArray[T](override val a: Array[T])(implicit val tag: ClassTag[T])
  extends PimpedArrayLike[T]

private[math] class PimpedArray2D(override val a: Array[Array[Double]])(implicit val tag: ClassTag[Array[Double]])
  extends PimpedArrayLike[Array[Double]] {

  def unary_~ = new ColumnMajorMatrix(a)

  def nrows: Int = a.length

  def ncols: Int = a(0).length

  /** Returns a submatrix. */
  def apply(rows: Range, cols: Range): Array[Array[Double]] = rows.map { row =>
    val x = a(row)
    cols.map { col => x(col) }.toArray
  }.toArray

  /** Returns a column. */
  def $(col: Int): Array[Double] = a.map(_(col))

  /** Returns multiple rows. */
  def row(i: Int*): Array[Array[Double]] = apply(i: _*)

  /** Returns a range of rows. */
  def row(i: Range): Array[Array[Double]] = apply(i)

  /** Returns multiple columns. */
  def col(j: Int*): Array[Array[Double]] = a.map { x =>
    j.map { col => x(col) }.toArray
  }

  /** Returns a range of columns. */
  def col(j: Range): Array[Array[Double]] = a.map { x =>
    j.map { col => x(col) }.toArray
  }
}

private[math] case class By(b: Double, y: Array[Double]) {
  override def toString = runtime.ScalaRunTime.stringOf(toArray())
  def toArray(): Array[Double] = y.map(_ * b)
}

private[math] case class PimpedDouble(a: Double) {
  def * (y: Array[Double]): By = By(a, y)

  def + (b: Array[Double]): Array[Double] = b.map(a + _)
  def - (b: Array[Double]): Array[Double] = b.map(a - _)
  //def * (b: Array[Double]): Array[Double] = b.map(a * _)
  def / (b: Array[Double]): Array[Double] = b.map(a / _)
  def ^ (b: Array[Double]): Array[Double] = b.map(math.pow(a, _))

  def + (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(b.nrows, b.ncols)
    for (j <- 0 until b.ncols)
      for (i <- 0 until b.nrows)
        c(i, j) = a + b(i, j)
    c
  }

  def - (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(b.nrows, b.ncols)
    for (j <- 0 until b.ncols)
      for (i <- 0 until b.nrows)
        c(i, j) = a - b(i, j)
    c
  }

  def * (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(b.nrows, b.ncols)
    for (j <- 0 until b.ncols)
      for (i <- 0 until b.nrows)
        c(i, j) = a * b(i, j)
    c
  }

  def / (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(b.nrows, b.ncols)
    for (j <- 0 until b.ncols)
      for (i <- 0 until b.nrows)
        c(i, j) = a / b(i, j)
    c
  }

  def ^ (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(b.nrows, b.ncols)
    for (j <- 0 until b.ncols)
      for (i <- 0 until b.nrows)
        c(i, j) = math.pow(a, b(i, j))
    c
  }
}

private[math] class PimpedDoubleArray(override val a: Array[Double]) extends PimpedArray[Double](a) {
  def * (b: Double): By = By(b, a)

  def + (b: Double): Array[Double] = a.map(_ + b)
  def - (b: Double): Array[Double] = a.map(_ - b)
  //def * (b: Double): Array[Double] = a.map(_ * b)
  def / (b: Double): Array[Double] = a.map(_ / b)
  def ^ (b: Double): Array[Double] = a.map(math.pow(_, b))

  def += (b: Double): Array[Double] = { a.transform(_ + b); a }
  def -= (b: Double): Array[Double] = { a.transform(_ - b); a }
  def *= (b: Double): Array[Double] = { a.transform(_ * b); a }
  def /= (b: Double): Array[Double] = { a.transform(_ / b); a }
  def ^= (b: Double): Array[Double] = { a.transform(math.pow(_, b)); a }

  def + (b: Array[Double]): Array[Double] = a.zip(b).map(x => x._1 + x._2)
  def - (b: Array[Double]): Array[Double] = a.zip(b).map(x => x._1 - x._2)
  def * (b: Array[Double]): Array[Double] = a.zip(b).map(x => x._1 * x._2)
  def / (b: Array[Double]): Array[Double] = a.zip(b).map(x => x._1 / x._2)
  def ^ (b: Array[Double]): Array[Double] = a.zip(b).map(x => math.pow(x._1, x._2))

  def += (b: Array[Double]): Array[Double] = {
    for (i <- 0 until a.length) a(i) += b(i)
    a
  }
  def -= (b: Array[Double]): Array[Double] = {
    for (i <- 0 until a.length) a(i) -= b(i)
    a
  }
  def *= (b: Array[Double]): Array[Double] = {
    for (i <- 0 until a.length) a(i) *= b(i)
    a
  }
  def /= (b: Array[Double]): Array[Double] = {
    for (i <- 0 until a.length) a(i) /= b(i)
    a
  }
  def ^= (b: Array[Double]): Array[Double] = {
    for (i <- 0 until a.length) a(i) = math.pow(a(i), b(i))
    a
  }
}

private[math] case class Ax(A: DenseMatrix, x: Array[Double]) {
  override def toString = runtime.ScalaRunTime.stringOf(toArray())

  def toArray(): Array[Double] = {
    val y = new Array[Double](x.length)
    A.ax(x, y)
  }

  def + (y: Array[Double]): Array[Double] = A.axpy(x, y)

  def + (by: By): Array[Double] = A.axpy(x, by.y, by.b)
}

private[math] case class Atx(A: DenseMatrix, x: Array[Double]) {
  override def toString = runtime.ScalaRunTime.stringOf(toArray())

  def toArray(): Array[Double] = {
    val y = new Array[Double](x.length)
    A.atx(x, y)
  }

  def + (y: Array[Double]): Array[Double] = A.atxpy(x, y)

  def + (by: By): Array[Double] = A.axpy(x, by.y, by.b)
}

private[math] case class Transpose(a: DenseMatrix) {
  override def toString = runtime.ScalaRunTime.stringOf(a.transpose())

  def * (x: Array[Double]): Atx = Atx(a, x)
  def * (b: DenseMatrix): DenseMatrix = a.atbmm(b)
}

private[math] class PimpedMatrix(a: DenseMatrix) {
  def += (i: Int, j: Int, x: Double): Double = a.add(i, j, x)
  def -= (i: Int, j: Int, x: Double): Double = a.sub(i, j, x)
  def *= (i: Int, j: Int, x: Double): Double = a.mul(i, j, x)
  def /= (i: Int, j: Int, x: Double): Double = a.div(i, j, x)

  def += (b: Double): DenseMatrix = a.add(b)
  def -= (b: Double): DenseMatrix = a.sub(b)
  def *= (b: Double): DenseMatrix = a.mul(b)
  def /= (b: Double): DenseMatrix = a.div(b)

  def += (b: DenseMatrix): DenseMatrix = a.add(b)
  def -= (b: DenseMatrix): DenseMatrix = a.sub(b)
  /** Element-wise multiplication */
  def *= (b: DenseMatrix): DenseMatrix = a.mul(b)
  /** Element-wise division */
  def /= (b: DenseMatrix): DenseMatrix = a.div(b)

  def * (b: DenseMatrix): DenseMatrix = a.abmm(b)
  def * (b: Transpose): DenseMatrix = a.abtmm(b.a)
  def / (b: DenseMatrix): DenseMatrix = a.abmm(b.inverse())

  def * (x: Array[Double]): Ax = Ax(a, x)

  def t: Transpose = new Transpose(a)

  /** Solves A * x = b */
  def \ (b: Array[Double]): Array[Double] = {
    val x = new Array[Double](b.length)
    a.solve(b, x)
  }

  def + (b: Double): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) + b
    c
  }

  def - (b: Double): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) - b
    c
  }

  def * (b: Double): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) * b
    c
  }

  def / (b: Double): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) / b
    c
  }

  def + (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) + b(i, j)
    c
  }

  def - (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) - b(i, j)
    c
  }

  /** Element-wise multiplication */
  def *~ (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) * b(i, j)
    c
  }

  /** Element-wise division */
  def /~ (b: DenseMatrix): ColumnMajorMatrix = {
    val c = new ColumnMajorMatrix(a.nrows, a.ncols)
    for (j <- 0 until a.ncols)
      for (i <- 0 until a.nrows)
        c(i, j) = a(i, j) / b(i, j)
    c
  }
}