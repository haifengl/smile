/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile

import scala.language.implicitConversions
import smile.math.matrix._
import smile.math.special.{Beta, Erf, Gamma}
import smile.stat.distribution.{Distribution, GaussianDistribution}
import smile.stat.hypothesis.{ChiSqTest, CorTest, FTest, KSTest, TTest}

/** Mathematical and statistical functions.
  *
  * @author Haifeng Li
  */
package object math {
  implicit def pimpDouble(x: Double): PimpedDouble = PimpedDouble(x)
  implicit def pimpIntArray(data: Array[Int]): PimpedArray[Int] = new PimpedArray[Int](data)
  implicit def pimpDoubleArray(data: Array[Double]): PimpedDoubleArray = new PimpedDoubleArray(data)
  implicit def pimpArray2D(data: Array[Array[Double]]): PimpedArray2D = new PimpedArray2D(data)
  implicit def pimpMatrix(matrix: Matrix): PimpedMatrix = new PimpedMatrix(matrix)

  implicit def array2VectorExpression(x: Array[Double]): VectorLift = VectorLift(x)
  implicit def vectorExpression2Array(exp: VectorExpression): Array[Double] = exp.toArray

  implicit def matrix2MatrixExpression(x: Matrix): MatrixLift = MatrixLift(x)
  implicit def matrixExpression2Array(exp: MatrixExpression): Matrix = exp.toMatrix

  def abs(x: VectorExpression): AbsVector = AbsVector(x)
  def acos(x: VectorExpression): AcosVector = AcosVector(x)
  def asin(x: VectorExpression): AsinVector = AsinVector(x)
  def atan(x: VectorExpression): AtanVector = AtanVector(x)
  def cbrt(x: VectorExpression): CbrtVector = CbrtVector(x)
  def ceil(x: VectorExpression): CeilVector = CeilVector(x)
  def exp(x: VectorExpression): ExpVector = ExpVector(x)
  def expm1(x: VectorExpression): Expm1Vector = Expm1Vector(x)
  def floor(x: VectorExpression): FloorVector = FloorVector(x)
  def log(x: VectorExpression): LogVector = LogVector(x)
  def log2(x: VectorExpression): Log2Vector = Log2Vector(x)
  def log10(x: VectorExpression): Log10Vector = Log10Vector(x)
  def log1p(x: VectorExpression): Log1pVector = Log1pVector(x)
  def round(x: VectorExpression): RoundVector = RoundVector(x)
  def sin(x: VectorExpression): SinVector = SinVector(x)
  def sqrt(x: VectorExpression): SqrtVector = SqrtVector(x)
  def tan(x: VectorExpression): TanVector = TanVector(x)
  def tanh(x: VectorExpression): TanhVector = TanhVector(x)

  def abs(x: MatrixExpression): AbsMatrix = AbsMatrix(x)
  def acos(x: MatrixExpression): AcosMatrix = AcosMatrix(x)
  def asin(x: MatrixExpression): AsinMatrix = AsinMatrix(x)
  def atan(x: MatrixExpression): AtanMatrix = AtanMatrix(x)
  def cbrt(x: MatrixExpression): CbrtMatrix = CbrtMatrix(x)
  def ceil(x: MatrixExpression): CeilMatrix = CeilMatrix(x)
  def exp(x: MatrixExpression): ExpMatrix = ExpMatrix(x)
  def expm1(x: MatrixExpression): Expm1Matrix = Expm1Matrix(x)
  def floor(x: MatrixExpression): FloorMatrix = FloorMatrix(x)
  def log(x: MatrixExpression): LogMatrix = LogMatrix(x)
  def log2(x: MatrixExpression): Log2Matrix = Log2Matrix(x)
  def log10(x: MatrixExpression): Log10Matrix = Log10Matrix(x)
  def log1p(x: MatrixExpression): Log1pMatrix = Log1pMatrix(x)
  def round(x: MatrixExpression): RoundMatrix = RoundMatrix(x)
  def sin(x: MatrixExpression): SinMatrix = SinMatrix(x)
  def sqrt(x: MatrixExpression): SqrtMatrix = SqrtMatrix(x)
  def tan(x: MatrixExpression): TanMatrix = TanMatrix(x)
  def tanh(x: MatrixExpression): TanhMatrix = TanhMatrix(x)

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
  def ttest(x: Array[Double], y: Array[Double]): TTest = TTest.testPaired(x, y)

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
  def chisqtest(table: Array[Array[Int]]): ChiSqTest = ChiSqTest.test(table)

  /** Returns an n-by-n zero matrix. */
  def zeros(n: Int) = new Matrix(n, n)
  /** Returns an m-by-n zero matrix. */
  def zeros(m: Int, n: Int) = new Matrix(m, n)
  /** Returns an n-by-n matrix of all ones. */
  def ones(n: Int) = new Matrix(n, n, 1.0)
  /** Returns an m-by-n matrix of all ones. */
  def ones(m: Int, n: Int) = new Matrix(m, n, 1.0)
  /** Returns an n-by-n identity matrix. */
  def eye(n: Int): Matrix = Matrix.eye(n)
  /** Returns an m-by-n identity matrix. */
  def eye(m: Int, n: Int): Matrix = Matrix.eye(m, n)
  /** Returns an m-by-n matrix of normally distributed random numbers. */
  def randn(m: Int, n: Int, mu: Double = 0.0, sigma: Double = 1.0): Matrix = Matrix.rand(m, n, new GaussianDistribution(mu, sigma))
  /** Returns the trace of matrix. */
  def trace(A: Matrix): Double = A.trace()
  /** Returns the diagonal elements of matrix. */
  def diag(A: Matrix): Array[Double] = A.diag()

  /** LU decomposition. */
  def lu(A: Array[Array[Double]]): Matrix.LU = new Matrix(A).lu(true)
  /** LU decomposition. */
  def lu(A: Matrix): Matrix.LU = A.lu(false)
  /** LU decomposition. */
  def lu(A: MatrixExpression): Matrix.LU = A.toMatrix.lu(true)

  /** QR decomposition. */
  def qr(A: Array[Array[Double]]): Matrix.QR = new Matrix(A).qr(true)
  /** QR decomposition. */
  def qr(A: Matrix): Matrix.QR = A.qr(false)
  /** QR decomposition. */
  def qr(A: MatrixExpression): Matrix.QR = A.toMatrix.qr(true)

  /** Cholesky decomposition. */
  def cholesky(A: Array[Array[Double]]): Matrix.Cholesky =  new Matrix(A).cholesky(true)
  /** Cholesky decomposition. */
  def cholesky(A: Matrix): Matrix.Cholesky = A.cholesky(false)
  /** Cholesky decomposition. */
  def cholesky(A: MatrixExpression): Matrix.Cholesky = A.toMatrix.cholesky(true)

  /** Returns eigen values. */
  def eig(A: Array[Array[Double]]): Matrix.EVD = new Matrix(A).eigen(false, false, true)
  /** Returns eigen values. */
  def eig(A: Matrix): Matrix.EVD = A.eigen(false, false, false)
  /** Returns eigen values. */
  def eig(A: MatrixExpression): Matrix.EVD = A.toMatrix.eigen(false, false, true)

  /** Eigen decomposition. */
  def eigen(A: Array[Array[Double]]): Matrix.EVD = new Matrix(A).eigen(false, true, true)
  /** Eigen decomposition. */
  def eigen(A: Matrix): Matrix.EVD = A.eigen(false, true, false)
  /** Eigen decomposition. */
  def eigen(A: MatrixExpression): Matrix.EVD = A.toMatrix.eigen(false, true, true)
  /** Returns k largest eigenvectors. */
  def eigen(A: DMatrix, k: Int): Matrix.EVD = A match {
    case a: Matrix =>
      if (a.isSymmetric) ARPACK.syev(a, ARPACK.SymmOption.LA, k)
      else ARPACK.eigen(A, ARPACK.AsymmOption.LM, k)
    case a: BandMatrix =>
      if (a.isSymmetric) ARPACK.syev(a, ARPACK.SymmOption.LA, k)
      else ARPACK.eigen(A, ARPACK.AsymmOption.LM, k)
    case a: SymmMatrix => ARPACK.syev(a, ARPACK.SymmOption.LA, k)
    case a => ARPACK.eigen(a, ARPACK.AsymmOption.LM, k)
  }

  /** Returns k largest eigenvectors. */
  def eigen(A: SMatrix, k: Int): FloatMatrix.EVD = A match {
    case a: FloatMatrix =>
      if (a.isSymmetric) ARPACK.syev(a, ARPACK.SymmOption.LA, k)
      else ARPACK.eigen(A, ARPACK.AsymmOption.LM, k)
    case a: FloatBandMatrix =>
      if (a.isSymmetric) ARPACK.syev(a, ARPACK.SymmOption.LA, k)
      else ARPACK.eigen(A, ARPACK.AsymmOption.LM, k)
    case a: FloatSymmMatrix => ARPACK.syev(a, ARPACK.SymmOption.LA, k)
    case a => ARPACK.eigen(a, ARPACK.AsymmOption.LM, k)
  }

  /** SVD decomposition. */
  def svd(A: Array[Array[Double]]): Matrix.SVD = new Matrix(A).svd(true, true)
  /** SVD decomposition. */
  def svd(A: Matrix): Matrix.SVD = A.svd(true, false)
  /** SVD decomposition. */
  def svd(A: MatrixExpression): Matrix.SVD = A.toMatrix.svd(true, true)
  /** Returns k largest singular vectors. */
  def svd(A: DMatrix, k: Int): Matrix.SVD = ARPACK.svd(A, k)
  /** Returns k largest singular vectors. */
  def svd(A: SMatrix, k: Int): FloatMatrix.SVD = ARPACK.svd(A, k)

  /** Returns the determinant of matrix. */
  def det(A: Matrix): Double = lu(A).det()
  /** Returns the determinant of matrix. */
  def det(A: MatrixExpression): Double = lu(A).det()
  /** Returns the rank of matrix. */
  def rank(A: Matrix): Int = svd(A).rank()
  /** Returns the rank of matrix. */
  def rank(A: MatrixExpression): Int = svd(A).rank()
  /** Returns the inverse of matrix. */
  def inv(A: Matrix): Matrix = A.inverse()
  /** Returns the inverse of matrix. */
  def inv(A: MatrixExpression): Matrix = A.toMatrix.inverse()
}
