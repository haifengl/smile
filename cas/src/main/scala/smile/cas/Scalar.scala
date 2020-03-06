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

/** Scalar: rank-0 tensor. */
trait Scalar extends Tensor {
  override def rank: Option[Int] = Some(0)
  override def shape: Option[Array[IntScalar]] = Some(Array())

  /** Applies the expression. */
  def apply(env: Map[String, Tensor]): Scalar
  /** Applies the expression. */
  def apply(env: (String, Tensor)*): Scalar = apply(Map(env: _*))
  /** Simplify the expression. */
  def simplify: Scalar = this

  /** Returns the derivative. */
  def d(dx: Var): Scalar
  /** Returns the gradient vector. */
  def d(dx: Var*): Vector = Vars(dx.map(d(_)): _*).simplify
  /** Returns the gradient vector. */
  def d(dx: VectorVar): Vector

  def + (y: Scalar): Scalar = Add(this, y).simplify
  def - (y: Scalar): Scalar = Sub(this, y).simplify
  def * (y: Scalar): Scalar = Mul(this, y).simplify
  def / (y: Scalar): Scalar = Div(this, y).simplify
  def ** (y: Scalar): Scalar = Power(this, y).simplify
  def unary_+ : Scalar = simplify
  def unary_- : Scalar = Neg(this).simplify

  def * (y: Vector): Vector = ScalarVectorProduct(this, y).simplify
  def * (y: Matrix): Matrix = ScalarMatrixProduct(this, y).simplify
}

/** Scalar: rank-0 tensor. */
trait IntScalar extends Tensor {
  override def rank: Option[Int] = Some(0)
  override def shape: Option[Array[IntScalar]] = Some(Array())

  /** Explicit conversion of int to float. */
  def toScalar: Scalar = Int2Scalar(this)
  /** Applies the expression. */
  def apply(env: Map[String, Tensor]): IntScalar
  /** Applies the expression. */
  def apply(env: (String, Tensor)*): IntScalar = apply(Map(env: _*))
  /** Simplify the expression. */
  def simplify: IntScalar = this

  def + (y: IntScalar): IntScalar = IntAdd(this, y).simplify
  def - (y: IntScalar): IntScalar = IntSub(this, y).simplify
  def * (y: IntScalar): IntScalar = IntMul(this, y).simplify
  def / (y: IntScalar): IntScalar = IntDiv(this, y).simplify
  def % (y: IntScalar): IntScalar = Mod(this, y).simplify
  def ** (y: IntScalar): IntScalar = IntPower(this, y).simplify
  def unary_+ : IntScalar = simplify
  def unary_- : IntScalar = IntNeg(this).simplify
}

/** Explicit conversion of int to float. */
case class Int2Scalar(x: IntScalar) extends Scalar {
  override def toString: String = x.toString

  override def apply(env: Map[String, Tensor]): Scalar = Int2Scalar(x.apply(env))
  override def simplify: Scalar = Int2Scalar(x.simplify)

  override def d(dx: Var): Scalar = Val(0)
  override def d(dx: VectorVar): Vector = ZeroVector(dx.size)
}

/** Scalar value. */
case class Val(x: Double) extends Scalar {
  override def toString: String = x.toString

  override def apply(env: Map[String, Tensor]): Val = this

  override def d(dx: Var): Scalar = Val(0)
  override def d(dx: VectorVar): Vector = ZeroVector(dx.size)
}

/** Integer scalar value. */
case class IntVal(x: Int) extends IntScalar {
  override def toString: String = x.toString

  override def toScalar: Scalar = Val(x)

  override def apply(env: Map[String, Tensor]): IntVal = this
}

/** Constant value. */
case class Const(symbol: String) extends Scalar {
  override def toString: String = symbol

  override def apply(env: Map[String, Tensor]): Scalar = env.get(symbol) match {
    case None => this
    case Some(x : Val) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected Val")
  }

  override def d(dx: Var): Scalar = Val(0)
  override def d(dx: VectorVar): Vector = ZeroVector(dx.size)
}

/** Integer constant value. */
case class IntConst(symbol: String) extends IntScalar {
  override def toString: String = symbol

  override def apply(env: Map[String, Tensor]): IntScalar = env.get(symbol) match {
    case None => this
    case Some(x : IntVal) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected IntVal")
  }
}

/** Scalar variable */
case class Var(symbol: String) extends Scalar {
  override def toString: String = symbol

  override def d(dx: Var): Scalar = {
    if (symbol.equals(dx.symbol)) Val(1) else Val(0)
  }

  override def d(dx: VectorVar): Vector = GradientVector(this, dx).simplify

  override def apply(env: Map[String, Tensor]): Scalar = env.get(symbol) match {
    case None => this
    case Some(x : Scalar) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected Scalar")
  }
}

/** Integer scalar variable */
case class IntVar(symbol: String) extends IntScalar {
  override def toString: String = symbol

  override def apply(env: Map[String, Tensor]): IntScalar = env.get(symbol) match {
    case None => this
    case Some(x : IntScalar) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected IntScalar")
  }
}

/** x + y */
case class Add(x: Scalar, y: Scalar) extends Scalar {
  override def toString: String = s"$x + $y"

  override def equals(o: Any): Boolean = o match {
    case Add(a, b) => (x == a && y == b) || (x == b && y == a)
    case _ => false
  }

  override def apply(env: Map[String, Tensor]): Scalar = x(env) + y(env)

  override def d(dx: Var): Scalar = {
    x.d(dx) + y.d(dx)
  }

  override def d(dx: VectorVar): Vector = {
    x.d(dx) + y.d(dx)
  }

  override def simplify: Scalar = (x, y) match {
    case (Val(a), Val(b)) => Val(a+b)
    case (Val(0), b) => b
    case (a, Val(0)) => a
    case (Neg(a), Neg(b)) => -(a + b)
    case (Neg(a), b) => b - a
    case (a, Neg(b)) => a - b
    case (Mul(a, b), Mul(c, d)) if a == c => a * (b + d)
    case (Mul(a, b), Mul(c, d)) if a == d => a * (b + c)
    case (Mul(a, b), Mul(c, d)) if b == c => b * (a + d)
    case (Mul(a, b), Mul(c, d)) if b == d => b * (a + c)
    case (Mul(a, b), c) if a == c => a * (b + 1)
    case (Mul(a, b), c) if b == c => b * (a + 1)
    case (a, Mul(b, c)) if a == b => a * (c + 1)
    case (a, Mul(b, c)) if a == c => a * (b + 1)
    case (Power(Sin(a), Val(2)), Power(Cos(b), Val(2))) if a == b => Val(1)
    case (Power(Cos(a), Val(2)), Power(Sin(b), Val(2))) if a == b => Val(1)
    case (Log(a), Log(b)) => Log(a * b)
    case (a, b) if a == b => 2 * a
    case _ => this
  }
}

/** x + y */
case class IntAdd(x: IntScalar, y: IntScalar) extends IntScalar {
  override def toString: String = {
    val xs = x match {
      case Mod(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case Mod(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs + $ys"
  }

  override def apply(env: Map[String, Tensor]): IntScalar = x(env) + y(env)

  override def simplify: IntScalar = (x, y) match {
    case (IntVal(a), IntVal(b)) => IntVal(a+b)
    case (IntVal(0), b) => b
    case (a, IntVal(0)) => a
    case (IntNeg(a), IntNeg(b)) => -(a + b)
    case (IntNeg(a), b) => b - a
    case (a, IntNeg(b)) => a - b
    case (IntMul(a, b), IntMul(c, d)) if a == c => a * (b + d)
    case (IntMul(a, b), IntMul(c, d)) if a == d => a * (b + c)
    case (IntMul(a, b), IntMul(c, d)) if b == c => b * (a + d)
    case (IntMul(a, b), IntMul(c, d)) if b == d => b * (a + c)
    case (IntMul(a, b), c) if a == c => a * (b + 1)
    case (IntMul(a, b), c) if b == c => b * (a + 1)
    case (a, IntMul(b, c)) if a == b => a * (c + 1)
    case (a, IntMul(b, c)) if a == c => a * (b + 1)
    case (a, b) if a == b => 2 * a
    case _ => this
  }
}

/** x - y */
case class Sub(x: Scalar, y: Scalar) extends Scalar {
  override def toString: String = s"$x - $y"

  override def apply(env: Map[String, Tensor]): Scalar = x(env) - y(env)

  override def d(dx: Var): Scalar = {
    x.d(dx) - y.d(dx)
  }

  override def d(dx: VectorVar): Vector = {
    x.d(dx) - y.d(dx)
  }

  override def simplify: Scalar = (x, y) match {
    case (Val(a), Val(b)) => Val(a-b)
    case (Val(0), b) => -b
    case (a, Val(0)) => a
    case (Neg(a), Neg(b)) => b - a
    case (a, Neg(b)) => a + b
    case (Neg(a), b) => -(a + b)
    case (Mul(a, b), Mul(c, d)) if a == c => a * (b - d)
    case (Mul(a, b), Mul(c, d)) if a == d => a * (b - c)
    case (Mul(a, b), Mul(c, d)) if b == c => b * (a - d)
    case (Mul(a, b), Mul(c, d)) if b == d => b * (a - c)
    case (Mul(a, b), c) if a == c => a * (b - 1)
    case (Mul(a, b), c) if b == c => b * (a - 1)
    case (a, Mul(b, c)) if a == b => a * (c - 1)
    case (a, Mul(b, c)) if a == c => a * (b - 1)
    case (Log(a), Log(b)) => Log(a / b)
    case (a, b) if a == b => Val(0)
    case _ => this
  }
}

/** x - y */
case class IntSub(x: IntScalar, y: IntScalar) extends IntScalar {
  override def toString: String = {
    val xs = x match {
      case Mod(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case Mod(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs - $ys"
  }

  override def apply(env: Map[String, Tensor]): IntScalar = x(env) - y(env)

  override def simplify: IntScalar = (x, y) match {
    case (IntVal(a), IntVal(b)) => IntVal(a-b)
    case (IntVal(0), b) => -b
    case (a, IntVal(0)) => a
    case (IntNeg(a), IntNeg(b)) => b - a
    case (a, IntNeg(b)) => a + b
    case (IntNeg(a), b) => -(a + b)
    case (IntMul(a, b), IntMul(c, d)) if a == c => a * (b - d)
    case (IntMul(a, b), IntMul(c, d)) if a == d => a * (b - c)
    case (IntMul(a, b), IntMul(c, d)) if b == c => b * (a - d)
    case (IntMul(a, b), IntMul(c, d)) if b == d => b * (a - c)
    case (IntMul(a, b), c) if a == c => a * (b - 1)
    case (IntMul(a, b), c) if b == c => b * (a - 1)
    case (a, IntMul(b, c)) if a == b => a * (c - 1)
    case (a, IntMul(b, c)) if a == c => a * (b - 1)
    case (a, b) if a == b => IntVal(0)
    case _ => this
  }
}

/** -x */
case class Neg(x: Scalar) extends Scalar {
  override def toString: String = x match {
    case Add(_, _) | Sub(_, _) | Power(_, _) => s"-($x)"
    case _ => s"-$x"
  }

  override def apply(env: Map[String, Tensor]): Scalar = -x(env)

  override def d(dx: Var): Scalar = {
    -x.d(dx)
  }

  override def d(dx: VectorVar): Vector = {
    -x.d(dx)
  }

  override def simplify: Scalar = x match {
    case a @ Val(0) => a
    case Val(a) => Val(-a)
    case Neg(a) => a
    case _ => this
  }
}

/** -x */
case class IntNeg(x: IntScalar) extends IntScalar {
  override def toString: String = x match {
    case IntAdd(_, _) | IntSub(_, _) | IntPower(_, _) | Mod(_, _) => s"-($x)"
    case _ => s"-$x"
  }

  override def apply(env: Map[String, Tensor]): IntScalar = -x(env)

  override def simplify: IntScalar = x match {
    case a @ IntVal(0) => a
    case IntVal(a) => IntVal(-a)
    case IntNeg(a) => a
    case _ => this
  }
}

/** x * y */
case class Mul(x: Scalar, y: Scalar) extends Scalar {
  override def toString: String = {
    val xs = x match {
      case Add(_, _) | Sub(_, _) | Power(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case Add(_, _) | Sub(_, _) | Power(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs * $ys"
  }

  override def apply(env: Map[String, Tensor]): Scalar = x(env) * y(env)

  override def d(dx: Var): Scalar = {
    y * x.d(dx) + x * y.d(dx)
  }

  override def d(dx: VectorVar): Vector = {
    y * x.d(dx) + x * y.d(dx)
  }

  override def simplify: Scalar = (x, y) match {
    case (Val(a), Val(b)) => Val(a*b)
    case (Val(1), b) => b
    case (Val(-1), b) => -b
    case (a, Val(1)) => a
    case (a, Val(-1)) => -a
    case (a @ Val(0), _) => a
    case (_, b @ Val(0)) => b
    case (Neg(a), Neg(b)) => a * b
    case (Neg(a), b) => -(a * b)
    case (a, Neg(b)) => -(a * b)
    case (Div(a, b), Div(c, d)) => (a * c) / (b * d)
    case (a, Div(b, c)) => (a * b) / c
    case (Div(a, b), c) => (a * c) / b
    case (a, Power(b, c)) if a == b => a ** (c + 1)
    case (Power(b, c), a) if a == b => a ** (c + 1)
    case (Power(a, b), Power(c, d)) if a == c => a ** (b + d)
    case (Exp(a), Exp(b)) => exp(a + b)
    case (Tan(a), Cot(b)) if a == b => Val(1)
    case (Cot(a), Tan(b)) if a == b => Val(1)
    case (Tan(a), Cos(b)) if a == b => sin(a)
    case (Cos(a), Tan(b)) if a == b => sin(a)
    case (Cot(a), Sin(b)) if a == b => cos(a)
    case (Sin(a), Cot(b)) if a == b => cos(a)
    case (a, Mul(b, c)) if a * b != Mul(a, b) => (a * b) * c
    case (a, Mul(b, c)) if a * c != Mul(a, c) => (a * c) * b
    case (Mul(a, b), c) if a * c != Mul(a, c) => (a * c) * b
    case (Mul(a, b), c) if b * c != Mul(b, c) => a * (b * c)
    case (a, b @ Val(_)) => b * a
    case (a, b) if a == b => a ** 2
    case _ => this
  }
}

/** x * y */
case class IntMul(x: IntScalar, y: IntScalar) extends IntScalar {
  override def toString: String = {
    val xs = x match {
      case IntAdd(_, _) | IntSub(_, _) | IntPower(_, _) | Mod(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case IntAdd(_, _) | IntSub(_, _) | IntPower(_, _) | Mod(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs * $ys"
  }

  override def apply(env: Map[String, Tensor]): IntScalar = x(env) * y(env)

  override def simplify: IntScalar = (x, y) match {
    case (IntVal(a), IntVal(b)) => IntVal(a*b)
    case (IntVal(1), b) => b
    case (IntVal(-1), b) => -b
    case (a, IntVal(1)) => a
    case (a, IntVal(-1)) => -a
    case (a @ IntVal(0), _) => a
    case (_, b @ IntVal(0)) => b
    case (IntNeg(a), IntNeg(b)) => a * b
    case (IntNeg(a), b) => -(a * b)
    case (a, IntNeg(b)) => -(a * b)
    case (IntDiv(a, b), IntDiv(c, d)) => (a * c) / (b * d)
    case (a, IntDiv(b, c)) => (a * b) / c
    case (IntDiv(a, b), c) => (a * c) / b
    case (a, IntPower(b, c)) if a == b => a ** (c + 1)
    case (IntPower(b, c), a) if a == b => a ** (c + 1)
    case (IntPower(a, b), IntPower(c, d)) if a == c => a ** (b + d)
    case (a, IntMul(b, c)) if a * b != IntMul(a, b) => (a * b) * c
    case (a, IntMul(b, c)) if a * c != IntMul(a, c) => (a * c) * b
    case (IntMul(a, b), c) if a * c != IntMul(a, c) => (a * c) * b
    case (IntMul(a, b), c) if b * c != IntMul(b, c) => a * (b * c)
    case (a, b @ IntVal(_)) => b * a
    case (a, b) if a == b => a ** 2
    case _ => this
  }
}

/** x / y */
case class Div(x: Scalar, y: Scalar) extends Scalar {
  override def toString: String = {
    val xs = x match {
      case Add(_, _) | Sub(_, _) | Mul(_, _) | Div(_, _) | Power(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case Add(_, _) | Sub(_, _) | Mul(_, _) | Div(_, _) | Power(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs / $ys"
  }

  override def apply(env: Map[String, Tensor]): Scalar = x(env) / y(env)

  override def d(dx: Var): Scalar = {
    val dy = y.d(dx)
    dy match {
      case Val(0) => x.d(dx) / y
      case _ => (y * x.d(dx) - x * dy) / (y ** 2)
    }
  }

  override def d(dx: VectorVar): Vector = {
    (y * x.d(dx) - x * y.d(dx)) / (y ** 2)
  }

  override def simplify: Scalar = (x, y) match {
    case (_, Val(0)) => throw new ArithmeticException("/ by zero")
    case (a @ Val(0), _) => a
    case (a, Val(1)) => a
    case (a, Val(-1)) => -a
    case (Val(a), Val(b)) => Val(a/b)
    case (Neg(a), Neg(b)) => a / b
    case (Neg(a), b) => -(a / b)
    case (a, Neg(b)) => -(a / b)
    case (Div(a, b), Div(c, d)) => (a * d) / (b * c)
    case (a, Div(b, c)) => (a * c) / b
    case (Div(a, b), c) => a / (b * c)
    case (a, Mul(b, c)) if a / b != Div(a, b) => (a / b) / c
    case (a, Mul(b, c)) if a / c != Div(a, c) => (a / c) / b
    case (Mul(a, b), c) if a / c != Div(a, c) => (a / c) * b
    case (Mul(a, b), c) if b / c != Div(b, c) => a * (b / c)
    case (Exp(a), Exp(b)) => Exp(a - b)
    case (a, Power(b, c)) if a * Power(b, -c) != Mul(a, Power(b, -c)) => a * Power(b, -c)
    case (Sin(a), Cos(b)) if a == b => tan(a)
    case (Cos(a), Sin(b)) if a == b => cot(a)
    case (a, Tan(b)) => a * cot(b)
    case (a, Cot(b)) => a * tan(b)
    case (a, b) if a == b => Val(1)
    case _ => this
  }
}

/** x / y */
case class IntDiv(x: IntScalar, y: IntScalar) extends IntScalar {
  override def toString: String = {
    val xs = x match {
      case IntAdd(_, _) | IntSub(_, _) | IntMul(_, _) | IntDiv(_, _) | IntPower(_, _) | Mod(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case IntAdd(_, _) | IntSub(_, _) | IntMul(_, _) | IntDiv(_, _) | IntPower(_, _) | Mod(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs / $ys"
  }

  override def apply(env: Map[String, Tensor]): IntScalar = x(env) / y(env)

  override def simplify: IntScalar = (x, y) match {
    case (_, IntVal(0)) => throw new ArithmeticException("/ by zero")
    case (a @ IntVal(0), _) => a
    case (a, IntVal(1)) => a
    case (a, IntVal(-1)) => -a
    case (IntVal(a), IntVal(b)) => IntVal(a/b)
    case (IntNeg(a), IntNeg(b)) => IntDiv(a, b)
    case (IntNeg(a), b) => IntNeg(IntDiv(a, b))
    case (a, IntNeg(b)) => IntNeg(IntDiv(a, b))
    case (IntDiv(a, b), IntDiv(c, d)) => (a * d) / (b * c)
    case (a, IntDiv(b, c)) => (a * c) / b
    case (IntDiv(a, b), c) => a / (b * c)
    case (a, IntMul(b, c)) if a / b != IntDiv(a, b) => (a / b) / c
    case (a, IntMul(b, c)) if a / c != IntDiv(a, c) => (a / c) / b
    case (IntMul(a, b), c) if a / c != IntDiv(a, c) => (a / c) * b
    case (IntMul(a, b), c) if b / c != IntDiv(b, c) => a * (b / c)
    case (a, b) if (a == b) => IntVal(1)
    case _ => this
  }
}

/** x ** y */
case class Power(x: Scalar, y: Scalar) extends Scalar {
  override def toString: String = {
    val xs = x match {
      case Add(_, _) | Sub(_, _) | Mul(_, _) | Div(_, _) | Power(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case Add(_, _) | Sub(_, _) | Mul(_, _) | Div(_, _) | Power(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs ** $ys"
  }

  override def apply(env: Map[String, Tensor]): Scalar = x(env) ** y(env)

  override def d(dx: Var): Scalar = {
    (y.d(dx), x.d(dx)) match {
      case (Val(0), Val(0)) => Val(0)
      case (_, Val(0)) => this * log(x)
      case (Val(0), dx) => y * (x ** (y - 1)) * dx
      case (dy, dx) => this * (dy * log(x) + (y / x) * dx)
    }
  }

  override def d(dx: VectorVar): Vector = {
    this * (y.d(dx) * log(x) + (y / x) * x.d(dx))
  }

  override def simplify: Scalar = (x, y) match {
    case (Val(0), Val(0)) => throw new ArithmeticException("0 ** 0")
    case (a @ Val(0), _) => a
    case (a @ Val(1), _) => a
    case (_, Val(0)) => Val(1)
    case (a, Val(1)) => a
    case (a, Val(-1)) => 1 / a
    case (Val(a), Val(b)) => Val(Math.pow(a, b))
    case (Val(Math.E), b) => exp(b)
    case (Power(a, b), c) => a ** (b * c)
    case _ => this
  }
}

/** x ** y */
case class IntPower(x: IntScalar, y: IntScalar) extends IntScalar {
  override def toString: String = {
    val xs = x match {
      case IntAdd(_, _) | IntSub(_, _) | IntMul(_, _) | IntDiv(_, _) | IntPower(_, _) | Mod(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case IntAdd(_, _) | IntSub(_, _) | IntMul(_, _) | IntDiv(_, _) | IntPower(_, _) | Mod(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs ** $ys"
  }

  override def apply(env: Map[String, Tensor]): IntScalar = x(env) ** y(env)

  override def simplify: IntScalar = (x, y) match {
    case (IntVal(0), IntVal(0)) => throw new ArithmeticException("0 ** 0")
    case (_, IntVal(b)) if b < 0 => throw new ArithmeticException("Negative exponent is not allowed for IntPower")
    case (a @ IntVal(0), _) => a
    case (a @ IntVal(1), _) => a
    case (_, IntVal(0)) => IntVal(1)
    case (a, IntVal(1)) => a
    case (IntVal(a), IntVal(b)) => IntVal(Math.pow(a, b).toInt)
    case (IntPower(a, b), c) => a ** (b * c)
    case _ => this
  }
}

/** x % y */
case class Mod(x: IntScalar, y: IntScalar) extends IntScalar {
  override def toString: String = {
    val xs = x match {
      case IntAdd(_, _) | IntSub(_, _) | IntMul(_, _) | IntDiv(_, _) | IntPower(_, _) | Mod(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case IntAdd(_, _) | IntSub(_, _) | IntMul(_, _) | IntDiv(_, _) | IntPower(_, _) | Mod(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs % $ys"
  }

  override def apply(env: Map[String, Tensor]): IntScalar = x(env) % y(env)

  override def simplify: IntScalar = (x, y) match {
    case (_, IntVal(0)) => throw new ArithmeticException("% by zero")
    case (IntVal(0), _) => IntVal(0)
    case (_, IntVal(1)) => IntVal(0)
    case (IntVal(a), IntVal(b)) => IntVal(a % b)
    case (IntMul(a, b), c) if a % c == IntVal(0) || b % c == IntVal(0) => IntVal(0)
    case (a, b) if a == b => IntVal(0)
    case _ => this
  }
}

/** exp(x) */
case class Exp(x: Scalar) extends Scalar {
  override def toString: String = s"exp($x)"

  override def apply(env: Map[String, Tensor]): Scalar = exp(x(env))

  override def d(dx: Var): Scalar = {
    x.d(dx) * this
  }

  override def d(dx: VectorVar): Vector = {
    this * x.d(dx)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.exp(a))
    case Log(a) => a
    case _ => this
  }
}

/** log(x) */
case class Log(x: Scalar) extends Scalar {
  override def toString: String = s"log($x)"

  override def apply(env: Map[String, Tensor]): Scalar = log(x(env))

  override def d(dx: Var): Scalar = {
    (1 / x) * x.d(dx)
  }

  override def d(dx: VectorVar): Vector = {
    (1 / x) * x.d(dx)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.log(a))
    case Exp(a) => a
    case Power(a, b) => b * log(a)
    case _ => this
  }
}

/** sin(x) */
case class Sin(x: Scalar) extends Scalar {
  override def toString: String = s"sin($x)"

  override def apply(env: Map[String, Tensor]): Scalar = sin(x(env))

  override def d(dx: Var): Scalar = {
    x.d(dx) * cos(x)
  }

  override def d(dx: VectorVar): Vector = {
    cos(x) * x.d(dx)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.sin(a))
    case ArcSin(a) => a
    case _ => this
  }
}

/** cos(x) */
case class Cos(x: Scalar) extends Scalar {
  override def toString: String = s"cos($x)"

  override def apply(env: Map[String, Tensor]): Scalar = cos(x(env))

  override def d(dx: Var): Scalar = {
    x.d(dx) * (-sin(x))
  }

  override def d(dx: VectorVar): Vector = {
    -sin(x) * x.d(dx)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.cos(a))
    case ArcCos(a) => a
    case _ => this
  }
}

/** tan(x) */
case class Tan(x: Scalar) extends Scalar {
  override def toString: String = s"tan($x)"

  override def apply(env: Map[String, Tensor]): Scalar = tan(x(env))

  override def d(dx: Var): Scalar = {
    x.d(dx) / (cos(x) ** 2)
  }

  override def d(dx: VectorVar): Vector = {
    x.d(dx) / (cos(x) ** 2)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.tan(a))
    case ArcTan(a) => a
    case _ => this
  }
}

/** cot(x) */
case class Cot(x: Scalar) extends Scalar {
  override def toString: String = s"cot($x)"

  override def apply(env: Map[String, Tensor]): Scalar = cot(x(env))

  override def d(dx: Var): Scalar = {
    -x.d(dx) / (sin(x) ** 2)
  }

  override def d(dx: VectorVar): Vector = {
    -x.d(dx) / (sin(x) ** 2)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(1.0 / Math.tan(a))
    case ArcCot(a) => a
    case _ => this
  }
}

/** asin(x) */
case class ArcSin(x: Scalar) extends Scalar {
  override def toString: String = s"asin($x)"

  override def apply(env: Map[String, Tensor]): Scalar = asin(x(env))

  override def d(dx: Var): Scalar = {
    x.d(dx) / sqrt(1.0 - x ** 2.0)
  }

  override def d(dx: VectorVar): Vector = {
    x.d(dx) / sqrt(1.0 - x ** 2.0)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.asin(a))
    case Sin(a) => a
    case _ => this
  }
}

/** acos(x) */
case class ArcCos(x: Scalar) extends Scalar {
  override def toString: String = s"acos($x)"

  override def apply(env: Map[String, Tensor]): Scalar = acos(x(env))

  override def d(dx: Var): Scalar = {
    -x.d(dx) / sqrt(1.0 - x ** 2.0)
  }

  override def d(dx: VectorVar): Vector = {
    -x.d(dx) / sqrt(1.0 - x ** 2.0)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.acos(a))
    case Cos(a) => a
    case _ => this
  }
}

/** atan(x) */
case class ArcTan(x: Scalar) extends Scalar {
  override def toString: String = s"atan($x)"

  override def apply(env: Map[String, Tensor]): Scalar = atan(x(env))

  override def d(dx: Var): Scalar = {
    x.d(dx) / (1.0 + x ** 2.0)
  }

  override def d(dx: VectorVar): Vector = {
    x.d(dx) / (1.0 + x ** 2.0)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.atan(a))
    case Tan(a) => a
    case _ => this
  }
}

/** acot(x) */
case class ArcCot(x: Scalar) extends Scalar {
  override def toString: String = s"acot($x)"

  override def apply(env: Map[String, Tensor]): Scalar = acot(x(env))

  override def d(dx: Var): Scalar = {
    -x.d(dx) / (1.0 + x ** 2.0)
  }

  override def d(dx: VectorVar): Vector = {
    -x.d(dx) / (1.0 + x ** 2.0)
  }

  override def simplify: Scalar = x match {
    case Val(a) => Val(Math.PI * 0.5 - Math.atan(a))
    case Cot(a) => a
    case _ => this
  }
}

/** abs(x) */
case class Abs(x: Scalar) extends Scalar {
  override def toString: String = s"abs($x)"

  override def apply(env: Map[String, Tensor]): Scalar = abs(x(env))

  override def d(dx: Var): Scalar = {
    x.d(dx) * (this / x)
  }

  override def d(dx: VectorVar): Vector = {
    x.d(dx) * (this / x)
  }

  override def simplify: Scalar = x.simplify match {
    case Val(a) => Val(Math.abs(a))
    case _ => this
  }
}

/** ceil(x) */
case class Ceil(x: Scalar) extends IntScalar {
  override def toString: String = s"ceil($x)"

  override def apply(env: Map[String, Tensor]): IntScalar = ceil(x(env))

  override def simplify: IntScalar = x.simplify match {
    case Val(a) => IntVal(Math.ceil(a).toInt)
    case _ => this
  }
}

/** floor(x) */
case class Floor(x: Scalar) extends IntScalar {
  override def toString: String = s"floor($x)"

  override def apply(env: Map[String, Tensor]): IntScalar = floor(x(env))

  override def simplify: IntScalar = x.simplify match {
    case Val(a) => IntVal(Math.floor(a).toInt)
    case _ => this
  }
}

/** round(x) */
case class Round(x: Scalar) extends IntScalar {
  override def toString: String = s"round($x)"

  override def apply(env: Map[String, Tensor]): IntScalar = round(x(env))

  override def simplify: IntScalar = x.simplify match {
    case Val(a) => IntVal(Math.round(a).toInt)
    case _ => this
  }
}
