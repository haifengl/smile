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

/** Vector: rank-1 tensor. */
trait Vector extends Tensor {
  override def rank: Option[Int] = Some(1)
  override def shape: Option[Array[IntScalar]] = Some(Array(size))

  /** The size of vector. */
  def size: IntScalar

  /** Applies the expression. */
  def apply(env: Map[String, Tensor]): Vector
  /** Applies the expression. */
  def apply(env: (String, Tensor)*): Vector = apply(Map(env: _*))
  /** Simplify the expression. */
  def simplify: Vector = this

  /** Returns the partial derivative. */
  def d(dx: Var): Vector
  /** Returns the Jacobian matrix. */
  def d(dx: VectorVar): Matrix

  def + (y: Vector): Vector = AddVector(this, y).simplify
  def - (y: Vector): Vector = AddVector(this, NegVector(y)).simplify
  def * (y: Scalar): Vector = ScalarVectorProduct(y, this).simplify
  def / (y: Scalar): Vector = ScalarVectorProduct(1/y, this).simplify

  def * (y: Vector): Scalar = InnerProduct(this, y).simplify
  def *~ (y: Vector): Matrix = OuterProduct(this, y).simplify

  def unary_+ : Vector = simplify
  def unary_- : Vector = NegVector(this).simplify
}

/** Vector of all 0's */
case class ZeroVector(size: IntScalar = IntConst("n")) extends Vector {
  override def toString: String = "0"
  override def d(dx: Var): Vector = this
  override def d(dx: VectorVar): Matrix = ZeroMatrix(size, dx.size)
  override def apply(env: Map[String, Tensor]): Vector = this
}

/** Vector of all 1's */
case class OneVector(size: IntScalar = IntConst("n")) extends Vector {
  override def toString: String = "1"
  override def d(dx: Var): Vector = ZeroVector(size)
  override def d(dx: VectorVar): Matrix = ZeroMatrix(size, dx.size)
  override def apply(env: Map[String, Tensor]): Vector = this
}

/** Vector value. */
case class VectorVal(x: Array[Double]) extends Vector {
  override def toString: String = x.mkString("[", ", ", "]")
  override def equals(that: Any): Boolean = that match {
    case VectorVal(y) => x.sameElements(y)
    case _ => false
  }
  override def size: IntScalar = IntVal(x.length)
  override def d(dx: Var): Vector = ZeroVector(size)
  override def d(dx: VectorVar): Matrix = ZeroMatrix(size, dx.size)
  override def apply(env: Map[String, Tensor]): VectorVal = this

  override def simplify: Vector = {
    if (x.forall(_ == 0)) ZeroVector(IntVal(x.length))
    else if (x.forall(_ == 1)) OneVector(IntVal(x.length))
    else this
  }
}

/** Constant vector. Different from VectorVal that has concrete values, this is of constant yet abstract value. */
case class ConstVector(symbol: String, size: IntScalar = IntConst("n")) extends Vector {
  override def toString: String = symbol
  override def d(dx: Var): Vector = ZeroVector(size)
  override def d(dx: VectorVar): Matrix = ZeroMatrix(size, dx.size)
  override def apply(env: Map[String, Tensor]): Vector = env.get(symbol) match {
    case Some(x: ZeroVector) => x
    case Some(x: OneVector) => x
    case Some(x: VectorVal) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected 0 | 1 | VectorVal")
  }
}

/** Vector variable */
case class Vars(x: Scalar*) extends Vector {
  override def toString: String = x.mkString("[", ", ", "]")

  override def size: IntScalar = IntVal(x.length)

  override def d(dx: Var): Vector = {
    Vars(x.map(_.d(dx)): _*).simplify
  }

  override def d(dx: VectorVar): Matrix = {
    RowMatrix(x.map(_.d(dx).simplify): _*).simplify
  }

  override def apply(env: Map[String, Tensor]): Vector = {
    Vars(x.map(_(env)): _*).simplify
  }

  override def simplify: Vector = {
    if (x.forall(_.isInstanceOf[Val]))
      VectorVal(x.map(_.asInstanceOf[Val].x).toArray).simplify
    else
      this
  }
}

/** Abstract vector variable */
case class VectorVar(symbol: String, size: IntScalar = IntConst("n")) extends Vector {
  override def toString: String = symbol

  override def d(dx: Var): Vector = TangentVector(this, dx)

  override def d(dx: VectorVar): Matrix = {
    if (symbol.equals(dx.symbol)) IdentityMatrix(size, size) else ZeroMatrix(size, size)
  }

  override def apply(env: Map[String, Tensor]): Vector = env.get(symbol) match {
    case None => this
    case Some(x : Vector) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected Vector")
  }
}

/** The derivative of a scalar y with respect to a vector x. */
case class GradientVector(y: Var, x: VectorVar) extends Vector {
  override def toString: String = s"\u2202${y.symbol}/\u2202${x.symbol}"

  override def size: IntScalar = x.size

  override def d(dx: Var): Vector = throw new UnsupportedOperationException("derivative of gradient vector")

  override def d(dx: VectorVar): Matrix = throw new UnsupportedOperationException("derivative of gradient vector")

  override def apply(env: Map[String, Tensor]): Vector = {
    val yv = env.get(y.symbol) match {
      case None | Some(Val(_)) => y
      case Some(y: Scalar) => y
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}, expected Scalar")
    }

    env.get(x.symbol) match {
      case None => yv.d(x)
      case Some(x: VectorVar) => yv.d(x)
      case Some(Vars(x @ _*)) if x.forall(_.isInstanceOf[Var]) => yv.d(x.map(_.asInstanceOf[Var]): _*)
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}, expected VectorVar or Vars")
    }
  }
}

/** The derivative of a vector y with respect to a scalar x. */
case class TangentVector(y: VectorVar, x: Var) extends Vector {
  override def toString: String = s"\u2202${y.symbol}/\u2202${x.symbol}"

  override def size: IntScalar = y.size

  override def d(dx: Var): Vector = throw new UnsupportedOperationException("derivative of tangent vector")

  override def d(dx: VectorVar): Matrix = throw new UnsupportedOperationException("derivative of tangent vector")

  override def apply(env: Map[String, Tensor]): Vector = {
    val yv = env.get(y.symbol) match {
      case None | Some(ZeroVector(_)) | Some(OneVector(_)) | Some(VectorVal(_)) | Some(ConstVector(_, _)) => y
      case Some(y: Vector) => y
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}, expected Vector")
    }

    env.get(x.symbol) match {
      case None => yv.d(x)
      case Some(x: Var) => yv.d(x)
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}, expected Var")
    }
  }
}

/** x + y */
case class AddVector(x: Vector, y: Vector) extends Vector {
  if (x.size != y.size) throw new IllegalArgumentException(s"Vector sizes mismatch: ${x.size} vs ${y.size}")

  override def toString: String = y match {
    case NegVector(y) => s"$x - $y"
    case _ => s"$x + $y"
  }

  override def equals(o: Any): Boolean = o match {
    case AddVector(a, b) => (x == a && y == b) || (x == b && y == a)
    case _ => false
  }

  override def size: IntScalar = x.size

  override def apply(env: Map[String, Tensor]): Vector = x(env) + y(env)

  override def d(dx: Var): Vector = {
    x.d(dx) + y.d(dx)
  }

  override def d(dx: VectorVar): Matrix = {
    x.d(dx) + y.d(dx)
  }

  override def simplify: Vector = (x, y) match {
    case (VectorVal(a), VectorVal(b)) => VectorVal(a.zip(b).map { case (x, y) => x + y })
    case (ZeroVector(_), b) => b
    case (a, ZeroVector(_)) => a
    case (a, NegVector(NegVector(b))) => a + b
    case (NegVector(a), NegVector(b)) => -(a + b)
    case (NegVector(a), b) if a == b => ZeroVector(a.size)
    case (a, NegVector(b)) if a == b => ZeroVector(a.size)
    case (ScalarVectorProduct(a, b), ScalarVectorProduct(c, d)) if b == d => (a + c) * b
    case (ScalarVectorProduct(a, b), ScalarVectorProduct(c, d)) if a == c => a * (b + d)
    case (ScalarVectorProduct(a, b), c) if b == c => (a + 1) * b
    case (a, ScalarVectorProduct(b, c)) if a == c => (b + 1) * a
    case (ScalarVectorProduct(a, b), NegVector(ScalarVectorProduct(c, d))) if b == d => (a - c) * b
    case (ScalarVectorProduct(a, b), NegVector(ScalarVectorProduct(c, d))) if a == c => a * (b - d)
    case (NegVector(ScalarVectorProduct(a, b)), c) if b == c => (1 - a) * b
    case (a, NegVector(ScalarVectorProduct(b, c))) if a == c => (1 - b) * a
    case (a, b) if a == b => 2 * a
    case _ => this
  }
}

/** -x */
case class NegVector(x: Vector) extends Vector {
  override def toString: String = x match {
    case AddVector(_, _) => s"-($x)"
    case _ => s"-$x"
  }

  override def size: IntScalar = x.size

  override def apply(env: Map[String, Tensor]): Vector = -x(env)

  override def d(dx: Var): Vector = {
    -x.d(dx)
  }

  override def d(dx: VectorVar): Matrix = {
    -x.d(dx)
  }

  override def simplify: Vector = x match {
    case a @ ZeroVector(_) => a
    case VectorVal(a) => VectorVal(a.map(-_))
    case NegVector(a) => a
    case _ => this
  }
}

/** a * x */
case class ScalarVectorProduct(a: Scalar, x: Vector) extends Vector {
  override def toString: String = {
    val xs = x match {
      case AddVector(_, _) => s"($x)"
      case _ => x.toString
    }

    val as = a match {
      case Add(_, _) | Sub(_, _) | Power(_, _) | InnerProduct(_, _) => s"($a)"
      case _ => a.toString
    }

    s"$as * $xs"
  }

  override def size: IntScalar = x.size

  override def apply(env: Map[String, Tensor]): Vector = a(env) * x(env)

  override def d(dx: Var): Vector = {
    a * x.d(dx) + a.d(dx) * x
  }

  override def d(dx: VectorVar): Matrix = {
    a * x.d(dx) + a.d(dx) *~ x
  }

  override def simplify: Vector = (a, x) match {
    case (Val(0), _) => ZeroVector(size)
    case (_, ZeroVector(_)) => ZeroVector(size)
    case (Val(1), b) => b
    case (Val(-1), b) => -b
    case (Val(a), VectorVal(b)) => VectorVal(b.map(_ * a))
    case (Neg(a), NegVector(b)) => a * b
    case (Neg(a), b) => -(a * b)
    case (a, NegVector(b)) => -(a * b)
    case (a, ScalarVectorProduct(b, c)) => (a * b) * c
    case _ => this
  }
}

/** Inner product (x * y) */
case class InnerProduct(x: Vector, y: Vector) extends Scalar {
  if (x.size != y.size) throw new IllegalArgumentException(s"Vector sizes mismatch: ${x.size} vs ${y.size}")

  override def toString: String = s"<$x, $y>"

  override def apply(env: Map[String, Tensor]): Scalar = x(env) * y(env)

  override def d(dx: Var): Scalar = {
    (x.d(dx) * y) + (x * y.d(dx))
  }

  override def d(dx: VectorVar): Vector = {
    (x.d(dx) * y) + (y.d(dx) * x)
  }

  override def simplify: Scalar = (x, y) match {
    case (ZeroVector(_), _) => Val(0)
    case (_, ZeroVector(_)) => Val(0)
    case (VectorVal(a), VectorVal(b)) => Val(a.zip(b).map { case (x, y) => x * y }.sum)
    case (ScalarVectorProduct(Val(a), b), c) => a * (b * c)
    case (a, ScalarVectorProduct(Val(b), c)) => b * (a * c)
    case (NegVector(a), NegVector(b)) => a * b
    case (a, NegVector(b)) => -(a * b)
    case (NegVector(a), b) => -(a * b)
    case _ => this
  }
}

/** Outer product (x ** y) */
case class OuterProduct(x: Vector, y: Vector) extends Matrix {
  if (x.size != y.size) throw new IllegalArgumentException(s"Vector sizes mismatch: ${x.size} vs ${y.size}")

  override def toString: String = {
    val xs = x match {
      case AddVector(_, _) | ScalarVectorProduct(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case AddVector(_, _) | ScalarVectorProduct(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs \u2297 $ys"
  }

  override def size: (IntScalar, IntScalar) = (x.size, y.size)

  override def apply(env: Map[String, Tensor]): Matrix = x(env) *~ y(env)

  override def d(dx: Var): Matrix = {
    (x.d(dx) *~ y) + (x *~ y.d(dx))
  }

  override def simplify: Matrix = (x, y) match {
    case (ZeroVector(_), _) => ZeroMatrix(size)
    case (_, ZeroVector(_)) => ZeroMatrix(size)
    case (ScalarVectorProduct(Val(a), b), c) => a * (b *~ c)
    case (a, ScalarVectorProduct(Val(b), c)) => b * (a *~ c)
    case (NegVector(a), NegVector(b)) => a *~ b
    case (a, NegVector(b)) => -(a *~ b)
    case (NegVector(a), b) => -(a *~ b)
    case _ => this
  }
}
