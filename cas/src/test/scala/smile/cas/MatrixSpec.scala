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

class MatrixSpec extends Specification {

  "Matrix" should {
    "(x + 0)" in {
      val x = MatrixVar("x")
      val e = x + ZeroMatrix()
      e mustEqual x
    }
    "(0 + x)" in {
      val x = MatrixVar("x")
      val e = ZeroMatrix() + x
      e mustEqual x
    }
    "x + x" in {
      val x = MatrixVar("x")
      val e = x + x
      e mustEqual ScalarMatrixProduct(2, x)
    }
    "x - x" in {
      val x = MatrixVar("x")
      val e = x - x
      e mustEqual ZeroMatrix()
    }
    "x + -x" in {
      val x = MatrixVar("x")
      val e = x + (-x)
      e mustEqual ZeroMatrix()
    }
    "x - y" in {
      val x = MatrixVar("x")
      val y = MatrixVar("y")
      val e = x - y
      e mustEqual AddMatrix(x, NegMatrix(y))
    }
    "-x - y" in {
      val x = MatrixVar("x")
      val y = MatrixVar("y")
      val e = -x - y
      e mustEqual NegMatrix(AddMatrix(x, y))
    }
    "a * x + b * y" in {
      val a = Var("a")
      val b = Var("b")
      val x = ConstMatrix("x")
      val y = ConstMatrix("y")
      val e = a * x + b * y
      e.d(a) mustEqual x
      e.d(b) mustEqual y
    }
    "x - -x" in {
      val x = MatrixVar("x")
      val e = x - (-x)
      e mustEqual ScalarMatrixProduct(2, x)
    }
    "x - -y" in {
      val x = MatrixVar("x")
      val y = MatrixVar("y")
      val e = x - (-y)
      e mustEqual AddMatrix(x, y)
    }
    "-x - -y" in {
      val x = MatrixVar("x")
      val y = MatrixVar("y")
      val e = -x - (-y)
      e mustEqual AddMatrix(NegMatrix(x), y)
    }
    "a * x - b * x" in {
      val x = MatrixVar("x")
      val e = x * 2 - 3 * x
      e mustEqual NegMatrix(x)
    }
    "-0" in {
      val x = ZeroMatrix()
      val e = -x
      e mustEqual x
    }
    "- -x" in {
      val x = MatrixVar("x")
      val e = -(-x)
      e mustEqual x
    }
    "(x * 0)" in {
      val x = MatrixVar("x")
      val e = x * 0
      e mustEqual ZeroMatrix()
    }
    "(0 * x)" in {
      val x = MatrixVar("x")
      val e = 0 * x
      e mustEqual ZeroMatrix()
    }
    "x * 1" in {
      val x = MatrixVar("x")
      val e = x * 1
      e mustEqual x
    }
    "1 * x" in {
      val x = MatrixVar("x")
      val e = 1 * x
      e mustEqual x
    }
    "x * -1" in {
      val x = MatrixVar("x")
      val e = x * -1
      e mustEqual NegMatrix(x)
    }
    "-1 * x" in {
      val x = MatrixVar("x")
      val e = -1 * x
      e mustEqual NegMatrix(x)
    }
    "5 * (x * 3)" in {
      val x = MatrixVar("x")
      val e = 5.0 * (x * 3.0)
      e mustEqual ScalarMatrixProduct(15, x)
    }
    "-x * -y" in {
      val x = MatrixVar("x")
      val y = MatrixVar("y", (IntConst("n"), IntConst("p")))
      val e = -x * -y
      e mustEqual MatrixProduct(x, y)
    }
    "x * -y" in {
      val x = MatrixVar("x")
      val y = MatrixVar("y", (IntConst("n"), IntConst("p")))
      val e = x * -y
      e mustEqual NegMatrix(MatrixProduct(x, y))
    }
    "-x * y" in {
      val x = MatrixVar("x")
      val y = MatrixVar("y", (IntConst("n"), IntConst("p")))
      val e = -x * y
      e mustEqual NegMatrix(MatrixProduct(x, y))
    }
  }
}
