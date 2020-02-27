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

class VectorSpec extends Specification {

  "Vector" should {
    "(3 + 5)" in {
      val e = VectorVal(Array(3)) + VectorVal(Array(5))
      e mustEqual VectorVal(Array(8))
      val x = Var("x")
      e.d(x) mustEqual ZeroVector(1)
    }
    "(x + 0)" in {
      val x = VectorVar("x")
      val e = x + ZeroVector()
      e mustEqual x
      e.d(x) mustEqual IdentityMatrix()
    }
    "(0 + x)" in {
      val x = VectorVar("x")
      val e = ZeroVector() + x
      e mustEqual x
    }
    "x + x" in {
      val x = VectorVar("x")
      val e = x + x
      e mustEqual ScalarVectorProduct(2, x)
      e.d(x) mustEqual ScalarMatrixProduct(2, IdentityMatrix())
    }
    "x - x" in {
      val x = VectorVar("x")
      val e = x - x
      e mustEqual ZeroVector()
    }
    "x + -x" in {
      val x = VectorVar("x")
      val e = x + (-x)
      e mustEqual ZeroVector()
    }
    "x - y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = x - y
      e mustEqual AddVector(x, NegVector(y))
      e.d(x) mustEqual IdentityMatrix()
      e.d(y) mustEqual NegMatrix(IdentityMatrix())
    }
    "-x - y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = -x - y
      e mustEqual NegVector(AddVector(x, y))
      e.d(y) mustEqual NegMatrix(IdentityMatrix())
    }
    "a * x + b * y" in {
      val a = Var("a")
      val b = Var("b")
      val x = ConstVector("x")
      val y = ConstVector("y")
      val e = a * x + b * y
      e.d(a) mustEqual x
      e.d(b) mustEqual y
    }
    "2 * x + 3 * y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = 2 * x + 3 * y
      e.d(x) mustEqual ScalarMatrixProduct(2, IdentityMatrix())
      e.d(y) mustEqual ScalarMatrixProduct(3, IdentityMatrix())
    }
    "sin2(x) + cos2(x)" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = sin(x*y)**2 + cos(x*y)**2
      e mustEqual Val(1)
    }
    "log(x) + log(y)" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val z = Var("z")
      val e = log(x*y) - log(z)
      e mustEqual Log(Div(InnerProduct(x, y), z))
    }
    "x - -x" in {
      val x = VectorVar("x")
      val e = x - (-x)
      e mustEqual ScalarVectorProduct(2, x)
    }
    "x - -y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = x - (-y)
      e mustEqual AddVector(x, y)
    }
    "-x - -y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = -x - (-y)
      e mustEqual AddVector(NegVector(x), y)
    }
    "a * x - b * x" in {
      val x = VectorVar("x")
      val e = x * 2 - 3 * x
      e mustEqual NegVector(x)
    }
    "-0" in {
      val x = ZeroVector()
      val e = -x
      e mustEqual x
    }
    "-5" in {
      val x = VectorVal(Array(5))
      val e = -x
      e mustEqual VectorVal(Array(-5))
    }
    "- -x" in {
      val x = VectorVar("x")
      val e = -(-x)
      e mustEqual x
    }
    "(x * 0)" in {
      val x = VectorVar("x")
      val e = x * 0
      e mustEqual ZeroVector()
    }
    "(0 * x)" in {
      val x = VectorVar("x")
      val e = 0 * x
      e mustEqual ZeroVector()
    }
    "x * 1" in {
      val x = VectorVar("x")
      val e = x * 1
      e mustEqual x
    }
    "1 * x" in {
      val x = VectorVar("x")
      val e = 1 * x
      e mustEqual x
    }
    "x * -1" in {
      val x = VectorVar("x")
      val e = x * -1
      e mustEqual NegVector(x)
    }
    "-1 * x" in {
      val x = VectorVar("x")
      val e = -1 * x
      e mustEqual NegVector(x)
    }
    "5 * (x * 3)" in {
      val x = VectorVar("x")
      val e = 5.0 * (x * 3.0)
      e mustEqual ScalarVectorProduct(15, x)
    }
    "-x * -y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = -x * -y
      e mustEqual InnerProduct(x, y)
    }
    "x * -y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = x * -y
      e mustEqual Neg(InnerProduct(x, y))
    }
    "-x * y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = -x * y
      e mustEqual Neg(InnerProduct(x, y))
    }
    "<x, y>" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = (2 * x) * (3 * y)
      e mustEqual 6 * InnerProduct(x, y)
      e.d(x) mustEqual 6 * y
      e.d(y) mustEqual 6 * x
    }
    "x \u2297 y" in {
      val x = VectorVar("x")
      val y = VectorVar("y")
      val e = (2 * x) *~ (3 * y)
      e mustEqual 6 * OuterProduct(x, y)
    }
  }
}
