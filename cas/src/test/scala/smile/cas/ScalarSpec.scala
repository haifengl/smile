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
    "(3 + 5)" in {
      val e = Val(3) + Val(5)
      e mustEqual Val(8)
      val x = Var("x")
      e.d(x) mustEqual Val(0)
    }
    "(x + 0)" in {
      val x = Var("x")
      val e = x + 0
      e mustEqual x
      e.d(x) mustEqual Val(1)
    }
    "(0 + x)" in {
      val x = Var("x")
      val e = 0 + x
      e mustEqual x
    }
    "x + x" in {
      val x = Var("x")
      val e = x + x
      e mustEqual Mul(2, x)
      e.d(x) mustEqual Val(2)
    }
    "x + -x" in {
      val x = Var("x")
      val e = x + (-x)
      e mustEqual Val(0)
    }
    "x + -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = x + (-y)
      e mustEqual Sub(x, y)
      e.d(x) mustEqual Val(1)
    }
    "-x + -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = -x + (-y)
      e mustEqual Neg(Add(x, y))
      e.d(x) mustEqual Val(-1)
    }
    "a * x + b * x" in {
      val x = Var("x")
      val e = x * 2 + 3 * x
      e mustEqual Mul(5, x)
      e.d(x) mustEqual Val(5)
    }
    "sin2(x) + cos2(x)" in {
      val x = Var("x")
      val e = sin(x)**2 + cos(x)**2
      e mustEqual Val(1)
    }
    "log(x) + log(y)" in {
      val x = Var("x")
      val y = Var("y")
      val e = log(x) + log(-y)
      e mustEqual Log(Neg(Mul(x, y)))
      e.d(x) mustEqual Div(1.0, x)
    }
    "(3 - 5)" in {
      val e = Val(3) - Val(5)
      e mustEqual Val(-2)
    }
    "(x - 0)" in {
      val x = Var("x")
      val e = x - 0
      e mustEqual x
    }
    "(0 - x)" in {
      val x = Var("x")
      val e = 0 - x
      e mustEqual Neg(x)
    }
    "x - x" in {
      val x = Var("x")
      val e = x - x
      e mustEqual Val(0)
    }
    "x - -x" in {
      val x = Var("x")
      val e = x - (-x)
      e mustEqual Mul(2, x)
    }
    "x - -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = x - (-y)
      e mustEqual Add(x, y)
    }
    "-x - -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = -x - (-y)
      e mustEqual Sub(y, x)
      e.d(x) mustEqual Val(-1)
      e.d(y) mustEqual Val(1)
    }
    "a * x - b * x" in {
      val x = Var("x")
      val e = x * 2 - 3 * x
      e mustEqual Neg(x)
    }
    "log(x) - log(y)" in {
      val x = Var("x")
      val y = Var("y")
      val e = log(x) - log(y)
      e mustEqual Log(Div(x, y))
      e.d(x) mustEqual Div(1, x)
    }
    "-0" in {
      val x = Val(0)
      val e = -x
      e mustEqual x
    }
    "-5" in {
      val x = Val(5)
      val e = -x
      e mustEqual Val(-5)
    }
    "- -x" in {
      val x = Var("x")
      val e = -(-x)
      e mustEqual x
    }
    "(3 * 5)" in {
      val e = Val(3) * Val(5)
      e mustEqual Val(15)
    }
    "(x * 0)" in {
      val x = Var("x")
      val e = x * 0
      e mustEqual Val(0)
    }
    "(0 * x)" in {
      val x = Var("x")
      val e = 0 * x
      e mustEqual Val(0)
    }
    "x * 1" in {
      val x = Var("x")
      val e = x * 1
      e mustEqual x
    }
    "1 * x" in {
      val x = Var("x")
      val e = 1 * x
      e mustEqual x
    }
    "x * -1" in {
      val x = Var("x")
      val e = x * -1
      e mustEqual Neg(x)
    }
    "-1 * x" in {
      val x = Var("x")
      val e = -1 * x
      e mustEqual Neg(x)
    }
    "5 * (x * 3)" in {
      val x = Var("x")
      val e = 5.0 * (x * 3.0)
      e mustEqual Mul(Val(15), x)
    }
    "(5 * y) * (x * 3)" in {
      val x = Var("x")
      val y = Var("y")
      val e = (5.0 * y) * (x * 3.0)
      e mustEqual 15 * y * x
    }
    "-x * -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = -x * -y
      e mustEqual Mul(x, y)
    }
    "x * -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = x * -y
      e mustEqual Neg(Mul(x, y))
    }
    "-x * y" in {
      val x = Var("x")
      val y = Var("y")
      val e = -x * y
      e mustEqual Neg(Mul(x, y))
    }
    "(a / x) * (b / y)" in {
      val a = Var("a")
      val b = Var("b")
      val x = Var("x")
      val y = Var("y")
      val e = (a / x) * (b / y)
      e mustEqual Div(Mul(a, b), Mul(x, y))
      e.d(a) mustEqual Div(b, Mul(x, y))
      e.d(x).toString mustEqual "-(a * b * y) / ((x * y) ** 2.0)"
    }
    "(2 * (a * y) / x) * ((b * x) / y)" in {
      val a = Var("a")
      val b = Var("b")
      val x = Var("x")
      val y = Var("y")
      val e = (2 * (a * y) / x) * ((b * x) / y)
      e mustEqual 2 * a * b
    }
    "exp(x) * exp(y)" in {
      val x = Var("x")
      val y = Var("y")
      val e = exp(x) * exp(y)
      e mustEqual Exp(Add(x, y))
      e.d(x) mustEqual e
      exp(x * y).d(x) mustEqual y * exp(x * y)
    }
    "tan(x) * cot(x)" in {
      val x = Var("x")
      val e = tan(x) * cot(x)
      e mustEqual Val(1)
    }
    "x * 3" in {
      val x = Var("x")
      val e = x * 3
      e mustEqual Mul(3, x)
    }
    "x * x" in {
      val x = Var("x")
      val e = x * x
      e mustEqual Power(x, 2)
      e.d(x) mustEqual 2 * x
    }
    "x / x" in {
      val x = Var("x")
      val e = x / x
      e mustEqual Val(1)
    }
    "(3 / 5)" in {
      val e = Val(3) / Val(5)
      e mustEqual Val(0.6)
    }
    "x / 1" in {
      val x = Var("x")
      val e = x / 1
      e mustEqual x
    }
    "x / -1" in {
      val x = Var("x")
      val e = x / -1
      e mustEqual Neg(x)
    }
    "(5 * y) / (x * 5)" in {
      val x = Var("x")
      val y = Var("y")
      val e = (5.0 * y) / (x * 5.0)
      e mustEqual Div(y, x)
      e.d(x) mustEqual Neg(Div(y, x ** 2))
      e.d(y) mustEqual 1 / x
    }
    "-x / -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = -x / -y
      e mustEqual Div(x, y)
    }
    "x / -y" in {
      val x = Var("x")
      val y = Var("y")
      val e = x / -y
      e mustEqual Neg(Div(x, y))
    }
    "-x / y" in {
      val x = Var("x")
      val y = Var("y")
      val e = -x / y
      e mustEqual Neg(Div(x, y))
    }
    "(a / x) / (b / y)" in {
      val a = Var("a")
      val b = Var("b")
      val x = Var("x")
      val y = Var("y")
      val e = (a / x) / (b / y)
      e mustEqual Div(Mul(a, y), Mul(x, b))
    }
    "exp(x) / exp(y)" in {
      val x = Var("x")
      val y = Var("y")
      val e = exp(x) / exp(y)
      e mustEqual Exp(Sub(x, y))
      e.d(y) mustEqual Neg(Exp(Sub(x, y)))
    }
    "sin(x) / cos(x)" in {
      val x = Var("x")
      val e = sin(x) / cos(x)
      e mustEqual tan(x)
    }
    "cos(x) / sin(x)" in {
      val x = Var("x")
      val e = cos(x) / sin(x)
      e mustEqual cot(x)
    }
    "0 ** x" in {
      val x = Var("x")
      val e = 0.0 ** x
      e mustEqual Val(0)
    }
    "1 ** x" in {
      val x = Var("x")
      val e = 1.0 ** x
      e mustEqual Val(1)
    }
    "x ** 0" in {
      val x = Var("x")
      val e = x ** 0
      e mustEqual Val(1)
    }
    "x ** 1" in {
      val x = Var("x")
      val e = x ** 1
      e mustEqual x
    }
    "(x ** a) ** b" in {
      val x = Var("x")
      val a = Var("a")
      val b = Var("b")
      val e = (x ** a) ** b
      e mustEqual Power(x, a * b)
      e.d(x) mustEqual a * b * (x ** (a * b - 1))
    }
    "mod" in {
      val n = IntVar("n")
      val e = (5 * (n + 1)) % 1
      e mustEqual IntVal(0)
      (2 * n) % 2 mustEqual IntVal(0)
      (3 * n) % n mustEqual IntVal(0)
    }
    "e ** x" in {
      val x = Var("x")
      val e = Math.E ** x
      e mustEqual Exp(x)
    }
    "exp" in {
      val x = Var("x")
      val e = exp(x)
      e.d(x) mustEqual e
      exp(x ** 2).d(x) mustEqual 2 * x * exp(x ** 2)
      exp(log(x)) mustEqual x
    }
    "log" in {
      val x = Var("x")
      val e = log(x)
      e.d(x) mustEqual 1/x
      log(x ** 2).d(x) mustEqual 2 / x
      log(exp(x)) mustEqual x
    }
    "sin" in {
      val x = Var("x")
      val e = sin(x)
      e.d(x) mustEqual cos(x)
      sin(x ** 2).d(x) mustEqual 2 * x * cos(x ** 2)
      sin(asin(x)) mustEqual x
    }
    "cos" in {
      val x = Var("x")
      val e = cos(x)
      e.d(x) mustEqual -sin(x)
      cos(x ** 2).d(x) mustEqual 2 * x * -sin(x ** 2)
      cos(acos(x)) mustEqual x
    }
    "tan" in {
      val x = Var("x")
      val e = tan(x)
      e.d(x) mustEqual Val(1) / (cos(x) ** 2)
      tan(x ** 2).d(x) mustEqual (2 * x) / (cos(x ** 2) ** 2)
      tan(atan(x)) mustEqual x
    }
    "cot" in {
      val x = Var("x")
      val e = cot(x)
      e.d(x) mustEqual Val(-1) / (sin(x) ** 2)
      cot(x ** 2).d(x) mustEqual -(2 * x) / (sin(x ** 2) ** 2)
      cot(acot(x)) mustEqual x
    }
    "asin" in {
      val x = Var("x")
      val e = asin(x)
      e.d(x) mustEqual Val(1) / sqrt(Val(1) - x ** 2)
      asin(x ** 2).d(x) mustEqual 2 * x / sqrt(Val(1) - x ** 4)
      asin(sin(x)) mustEqual x
    }
    "acos" in {
      val x = Var("x")
      val e = acos(x)
      e.d(x) mustEqual -Val(1) / sqrt(Val(1) - x ** 2)
      acos(x ** 2).d(x) mustEqual -(2 * x / sqrt(Val(1) - x ** 4))
      acos(cos(x)) mustEqual x
    }
    "atan" in {
      val x = Var("x")
      val e = atan(x)
      e.d(x) mustEqual Val(1) / (Val(1) + x ** 2)
      atan(x ** 2).d(x) mustEqual 2 * x / (Val(1) + x ** 4)
      atan(tan(x)) mustEqual x
    }
    "acot" in {
      val x = Var("x")
      val e = acot(x)
      e.d(x) mustEqual -Val(1) / (Val(1) + x ** 2)
      acot(x ** 2).d(x) mustEqual -(2 * x / (Val(1) + x ** 4))
      acot(cot(x)) mustEqual x
    }
    "ceil" in {
      val x = Var("x")
      val e = floor(x + 0.5)
      e.apply("x" -> Val(0.1)) mustEqual IntVal(0)
    }
    "floor" in {
      val x = Var("x")
      val e = ceil(x + 0.5)
      e.apply("x" -> Val(0.1)) mustEqual IntVal(1)
    }
    "round" in {
      val x = Var("x")
      val e = round(x + 0.5)
      e.apply("x" -> Val(0.1)) mustEqual IntVal(1)
    }
    "abs" in {
      val x = Var("x")
      val e = abs(x)
      val d = e.d(x)
      d mustEqual e / x
      abs(x ** 2).d(x) mustEqual Val(2) * abs(x ** 2) / x
      d.apply("x" -> Val(2)) mustEqual Val(1)
      d.apply("x" -> Val(-2)) mustEqual Val(-1)
    }
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
      e mustEqual 4.0 * x + (x ** 2.0) * cot(x ** 3.0)
      e.d(x).toString mustEqual "4.0 + 2.0 * cot(x ** 3.0) * x - (3.0 * (x ** 4.0)) / (sin(x ** 3.0) ** 2.0)"
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
