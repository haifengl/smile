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

package smile.data

import scala.language.implicitConversions
import smile.data.formula.Terms.$

/** Formula DSL. */
package object formula {
  implicit def buildFormula(x: FormulaBuilder): Formula = x.toFormula
  implicit def buildFactorInteraction(x: FactorInteractionBuilder): FactorInteraction = x.toFactorInteraction
  implicit def buildFactorCrossing(x: FactorCrossingBuilder): FactorCrossing = x.toFactorCrossing
  implicit def pimpFormulaBuilder(x: String): PimpedFormulaBuilder = PimpedFormulaBuilder($(x))
  implicit def pimpFormulaBuilder(x: Term): PimpedFormulaBuilder = PimpedFormulaBuilder(x)
  implicit def pimpFormulaString(x: String): PimpedFormulaString = PimpedFormulaString(x)
  implicit def pimpHyperTerm(x: Term): PimpedHyperTerm = PimpedHyperTerm(x)
  implicit def pimpTerm(x: Term): PimpedTerm = PimpedTerm(x)

  def abs(x: String): Term = Terms.abs($(x))
  def ceil(x: String): Term = Terms.ceil($(x))
  def floor(x: String): Term = Terms.floor($(x))
  def round(x: String): Term = Terms.round($(x))
  def rint(x: String): Term = Terms.rint($(x))
  def exp(x: String): Term = Terms.exp($(x))
  def expm1(x: String): Term = Terms.expm1($(x))
  def log(x: String): Term = Terms.log($(x))
  def log1p(x: String): Term = Terms.log1p($(x))
  def log10(x: String): Term = Terms.log10($(x))
  def log2(x: String): Term = Terms.log2($(x))
  def signum(x: String): Term = Terms.signum($(x))
  def sign(x: String): Term = Terms.sign($(x))
  def sqrt(x: String): Term = Terms.sqrt($(x))
  def cbrt(x: String): Term = Terms.cbrt($(x))
  def sin(x: String): Term = Terms.sin($(x))
  def cos(x: String): Term = Terms.cos($(x))
  def tan(x: String): Term = Terms.tan($(x))
  def sinh(x: String): Term = Terms.sinh($(x))
  def cosh(x: String): Term = Terms.cosh($(x))
  def tanh(x: String): Term = Terms.tanh($(x))
  def asin(x: String): Term = Terms.asin($(x))
  def acos(x: String): Term = Terms.acos($(x))
  def atan(x: String): Term = Terms.atan($(x))
  def ulp(x: String): Term = Terms.ulp($(x))

  def abs(x: Term): Term = Terms.abs(x)
  def ceil(x: Term): Term = Terms.ceil(x)
  def floor(x: Term): Term = Terms.floor(x)
  def round(x: Term): Term = Terms.round(x)
  def rint(x: Term): Term = Terms.rint(x)
  def exp(x: Term): Term = Terms.exp(x)
  def expm1(x: Term): Term = Terms.expm1(x)
  def log(x: Term): Term = Terms.log(x)
  def log1p(x: Term): Term = Terms.log1p(x)
  def log10(x: Term): Term = Terms.log10(x)
  def log2(x: Term): Term = Terms.log2(x)
  def signum(x: Term): Term = Terms.signum(x)
  def sign(x: Term): Term = Terms.sign(x)
  def sqrt(x: Term): Term = Terms.sqrt(x)
  def cbrt(x: Term): Term = Terms.cbrt(x)
  def sin(x: Term): Term = Terms.sin(x)
  def cos(x: Term): Term = Terms.cos(x)
  def tan(x: Term): Term = Terms.tan(x)
  def sinh(x: Term): Term = Terms.sinh(x)
  def cosh(x: Term): Term = Terms.cosh(x)
  def tanh(x: Term): Term = Terms.tanh(x)
  def asin(x: Term): Term = Terms.asin(x)
  def acos(x: Term): Term = Terms.acos(x)
  def atan(x: Term): Term = Terms.atan(x)
  def ulp(x: Term): Term = Terms.ulp(x)
}
