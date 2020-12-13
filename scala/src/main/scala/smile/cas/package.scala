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

/** Computer algebra system.
  * A computer algebra system (CAS) has the ability to manipulate mathematical
  * expressions in a way similar to the traditional manual computations of
  * mathematicians and scientists.
  *
  * The symbolic manipulations supported include:
  *
  *   - simplification to a smaller expression or some standard form,
  * including automatic simplification with assumptions and
  * simplification with constraints
  *
  *   - substitution of symbols or numeric values for certain expressions
  *
  *   - change of form of expressions: expanding products and powers, partial
  * and full factorization, rewriting as partial fractions, constraint
  * satisfaction, rewriting trigonometric functions as exponentials,
  * transforming logic expressions, etc.
  *
  *   - partial and total differentiation
  *
  *   - matrix operations including products, inverses, etc.
  *
  * @author Haifeng Li
  */
package object cas {
  implicit def pimpString(x: String): Var = Var(x)
  implicit def pimpDouble(x: Double): Val = Val(x)
  implicit def pimpInt(x: Int): IntVal = IntVal(x)

  def exp(x: Scalar): Scalar = Exp(x).simplify
  def log(x: Scalar): Scalar = Log(x).simplify

  def sqrt(x: Scalar): Scalar = x ** 0.5
  def logistic(x: Scalar): Scalar = 1.0 / exp(-x)

  def sin(x: Scalar): Scalar = Sin(x).simplify
  def cos(x: Scalar): Scalar = Cos(x).simplify
  def tan(x: Scalar): Scalar = Tan(x).simplify
  def cot(x: Scalar): Scalar = Cot(x).simplify

  def sinh(x: Scalar): Scalar = exp(x) - exp(-x)
  def cosh(x: Scalar): Scalar = exp(x) + exp(-x)
  def tanh(x: Scalar): Scalar = sinh(x) / cosh(x)

  def asin(x: Scalar): Scalar = ArcSin(x).simplify
  def acos(x: Scalar): Scalar = ArcCos(x).simplify
  def atan(x: Scalar): Scalar = ArcTan(x).simplify
  def acot(x: Scalar): Scalar = ArcCot(x).simplify

  def abs(x: Scalar): Scalar = Abs(x).simplify

  def ceil(x: Scalar): IntScalar = Ceil(x).simplify
  def floor(x: Scalar): IntScalar = Floor(x).simplify
  def round(x: Scalar): IntScalar = Round(x).simplify
}
