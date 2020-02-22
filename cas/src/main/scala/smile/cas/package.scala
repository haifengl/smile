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

import scala.language.implicitConversions

/** Computer algebra system.
  *
  * @author Haifeng Li
  */
package object cas {
  implicit def pimpString(x: String) = Var(x)
  implicit def pimpDouble(x: Double) = Val(x)
  implicit def pimpInt(x: Int) = IntVal(x)

  def exp(x: Scalar): Scalar = Exp(x).simplify
  def log(x: Scalar): Scalar = Log(x).simplify
  def sin(x: Scalar): Scalar = Sin(x).simplify
  def cos(x: Scalar): Scalar = Cos(x).simplify
  def tan(x: Scalar): Scalar = Tan(x).simplify
  def cot(x: Scalar): Scalar = Cot(x).simplify
/*
  def floor(x: Scalar) = Floor(x)
  def round(x: Scalar) = Round(x)
  def abs(x: Scalar) = Abs(x)
  def acos(x: Scalar) = Acos(x)
  def asin(x: Scalar) = Asin(x)
  def atan(x: Scalar) = Atan(x)
  def cbrt(x: Scalar) = Cbrt(x)
  def ceil(x: Scalar) = Ceil(x)
  def sqrt(x: Scalar) = Sqrt(x)
  def tan(x: Scalar) = Tan(x)
  def tanh(x: Scalar) = Tanh(x)
 */
}
