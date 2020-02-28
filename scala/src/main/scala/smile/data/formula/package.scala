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

package smile.data

import scala.language.implicitConversions
import smile.data.formula.Terms.$

/** Formula DSL. */
package object formula {
  implicit def buildFormula(x: FormulaBuilder) = x.toFormula
  implicit def buildFactorInteraction(x: FactorInteractionBuilder) = x.toFactorInteraction
  implicit def buildFactorCrossing(x: FactorCrossingBuilder) = x.toFactorCrossing
  implicit def pimpFormulaString(x: String) = PimpedFormulaString(x)
  implicit def pimpHyperTerm(x: HyperTerm) = PimpedHyperTerm(x)
  implicit def pimpTerm(x: Term) = PimpedTerm(x)

  def all: HyperTerm = Terms.all()
  def onehot(factors: String*): HyperTerm = Terms.onehot(factors: _*)
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
}
