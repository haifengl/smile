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

package smile.data.formula

import scala.collection.mutable.ListBuffer
import Terms._

/** DSL to build a formula in R style. */
case class FormulaBuilder(y: Option[Term], x: ListBuffer[HyperTerm]) {
  def + (term: HyperTerm): FormulaBuilder = {
    x.append(term)
    this
  }

  def + (variable: String): FormulaBuilder = {
    x.append($(variable))
    this
  }

  def - (term: Term): FormulaBuilder = {
    x.append(delete(term))
    this
  }

  def - (variable: String): FormulaBuilder = {
    x.append(delete(variable))
    this
  }

  /** Builds the formula. */
  def toFormula: Formula = y match {
    case None => x.toList match {
      case Nil => Formula.rhs(all)
      case x => Formula.rhs(x: _*)
    }

    case Some(y) => x.toList match {
      case Nil => Formula.lhs(y)
      case x => Formula.of(y, x: _*)
    }
  }
}

/** smile.nlp has pimpString function and PimpedString class.
  * Use a different name to avoid clash.
  */
private[formula] case class PimpedFormulaString(a: String) {
  def ~ (b: String) = FormulaBuilder(Option($(a)), ListBuffer(if (b.equals(".")) all else $(b)))
  def ~ (b: HyperTerm) = FormulaBuilder(Option($(a)), ListBuffer(b))
  def ~ () = FormulaBuilder(Option($(a)), ListBuffer())
  def unary_~ = FormulaBuilder(Option.empty, ListBuffer($(a)))

  def :: (b: String) = FactorInteractionBuilder(ListBuffer(a, b))

  def && (b: String) = FactorCrossingBuilder(ListBuffer(a, b))
}

private[formula] case class FactorInteractionBuilder(x: ListBuffer[String]) {
  def :: (b: String): FactorInteractionBuilder = {
    x.append(b)
    this
  }

  def toFactorInteraction: FactorInteraction = interact(x.reverse.toList: _*)
}

private[formula] case class FactorCrossingBuilder(x: ListBuffer[String]) {
  def && (b: String): FactorCrossingBuilder = {
    x.append(b)
    this
  }

  /** Customized degree. */
  def ^ (degree: Int): FactorCrossing = cross(degree, x.toList: _*)

  def toFactorCrossing: FactorCrossing = cross(x.toList: _*)
}

private[formula] case class PimpedHyperTerm(a: HyperTerm) {
  def unary_~ = FormulaBuilder(Option.empty, ListBuffer(a))
}

private[formula] case class PimpedTerm(a: Term) {
  def unary_~ = FormulaBuilder(Option.empty, ListBuffer(a))

  def + (b: Term) = add(a, b)
  def - (b: Term) = sub(a, b)
  def * (b: Term) = mul(a, b)
  def / (b: Term) = div(a, b)

  def + (b: Int) = add(a, `val`(b))
  def - (b: Int) = sub(a, `val`(b))
  def * (b: Int) = mul(a, `val`(b))
  def / (b: Int) = div(a, `val`(b))

  def + (b: Double) = add(a, `val`(b))
  def - (b: Double) = sub(a, `val`(b))
  def * (b: Double) = mul(a, `val`(b))
  def / (b: Double) = div(a, `val`(b))
}
