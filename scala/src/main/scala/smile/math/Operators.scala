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
import smile.math.matrix.RowMajorMatrix
import smile.math.special._
import smile.stat.hypothesis._
import smile.stat.distribution.Distribution

/** High level feature selection operators.
  *
  * @author Haifeng Li
  */
trait Operators {
  implicit def array2Matrix(matrix: Array[Array[Double]]) = new RowMajorMatrix(matrix)

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