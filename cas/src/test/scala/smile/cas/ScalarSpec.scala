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

package smile.cas

import org.specs2.mutable._

class ScalarSpec extends Specification {

  "Scalar" should {
    "sin(x) / 5 * x" in {
      val x = Var("x")
      val e = sin(x) / 5 * x
      e.toString mustEqual "(sin(x) * x) / 5.0"
    }
    "sin(x) / (5 * x)" in {
      val x = Var("x")
      val e = sin(x) / (5 * x)
      e.toString mustEqual "sin(x) / (5.0 * x)"
    }
    "sin(x) / 5 + x" in {
      val x = Var("x")
      val e = sin(x) / 5 + x
      e.toString mustEqual "sin(x) / 5.0 + x"
    }
    "sin(x) / (5 + x)" in {
      val x = Var("x")
      val e = sin(x) / (5 + x)
      e.toString mustEqual "sin(x) / (5.0 + x)"
    }
    "0*x**2+4*1*x**(2-1)+1*x**(3-1)*cot(x**3)" in {
      val x = Var("x")
      val e = 0*x**2+4*1*x**(2-1)+1*x**(3-1)*cot(x**3)
      e.toString mustEqual "4.0 * x + (x ** 2.0) * cot(x ** 3.0)"

      e.d(x).toString mustEqual "4.0 + cot(x ** 3.0) * 2.0 * x - (x ** 2.0) * 3.0 * (x ** 2.0) * (sin(x ** 3.0) ** -2.0)"
    }
    "4*x*5" in {
      val x = Var("x")
      val e = 4*x*5
      e.toString mustEqual "20.0 * x"

      e.d(x).toString mustEqual "20.0"
    }
    "4*x**2" in {
      val x = Var("x")
      val e = 4.0*(x**2.0)
      e.toString mustEqual "4.0 * (x ** 2.0)"

      e.d(x).toString mustEqual "8.0 * x"
    }
    "1/(x+2)" in {
      val x = Var("x")
      val e = 1.0 / (x + 2.0)
      e.toString mustEqual "1.0 / (x + 2.0)"

      e.d(x).toString mustEqual "-((x + 2.0) ** -2.0)"
    }
    "1/x**2" in {
      val x = Var("x")
      val e = 1.0 / (x ** 2.0)
      e.toString mustEqual "x ** -2.0"

      e.d(x).toString mustEqual "-2.0 * (x ** -3.0)"
    }
  }
}
