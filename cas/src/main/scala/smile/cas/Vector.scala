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
  /** The size of vector. */
  def size: IntScalar
  /** Returns true if the expression contains the variable dx. */
  def contains(dx: Var): Boolean
  /** Returns the partial derivative. */
  def d(dx: Var): Vector
  /** Applies the expression. */
  def apply(env: Map[String, Tensor]): Vector
  /** Applies the expression. */
  def apply(env: (String, Tensor)*): Vector = apply(Map(env: _*))
  /** Simplify the expression. */
  def simplify: Vector = this

  def + (y: Vector): Vector = VectorAdd(this, y).simplify
  def - (y: Vector): Vector = VectorAdd(this, VectorNeg(y)).simplify
  def * (y: Scalar): Vector = ScalarVectorProduct(y, this).simplify
  def * (y: Vector): Scalar = InnerProduct(this, y).simplify
  //def ** (y: Vector): Matrix = OutProduct(this, y).simplify
  def unary_+ : Vector = simplify
  def unary_- : Vector = VectorNeg(this).simplify
}

/** 0 */
case class VectorZero(size: IntScalar) extends Vector {
  override def toString: String = "0"
  override def contains(dx: Var): Boolean = false
  override def d(dx: Var): Vector = this
  override def apply(env: Map[String, Tensor]): Vector = this
}

/** 1 */
case class VectorOne(size: IntScalar) extends Vector {
  override def toString: String = "1"
  override def contains(dx: Var): Boolean = false
  override def d(dx: Var): Vector = this
  override def apply(env: Map[String, Tensor]): Vector = this
}

/** Vector value. */
case class VectorVal(x: Array[Double]) extends Vector {
  override def toString: String = x.toString
  override def contains(dx: Var): Boolean = false
  override def size: IntScalar = IntVal(x.length)
  override def d(dx: Var): Vector = VectorZero(size)
  override def apply(env: Map[String, Tensor]): VectorVal = this

  override def simplify: Vector = {
    if (x.forall(_ == 0)) VectorZero(IntVal(x.length))
    else if (x.forall(_ == 1)) VectorOne(IntVal(x.length))
    else this
  }
}

/** Vector variable */
case class VectorVar(symbol: String, size: IntScalar = IntVar("n")) extends Vector {
  override def toString: String = symbol

  override def contains(dx: Var): Boolean = symbol.equals(dx.symbol)

  override def d(dx: Var): Vector = {
    if (symbol.equals(dx.symbol)) VectorOne(size) else VectorZero(size)
  }

  override def apply(env: Map[String, Tensor]): Vector = env.get(symbol) match {
    case None => this
    case Some(x : Vector) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected Vector")
  }
}

/** x + y */
case class VectorAdd(x: Vector, y: Vector) extends Vector {
  if (x.size != y.size) throw new IllegalArgumentException(s"Vector sizes mismatch: ${x.size} vs ${y.size}")

  override def toString: String = y match {
    case VectorNeg(b) => s"$x - $b"
    case _ => s"$x + $y"
  }

  override def equals(o: Any): Boolean = o match {
    case VectorAdd(a, b) => (x == a && y == b) || (x == b && y == a)
    case _ => false
  }

  override def size: IntScalar = x.size

  override def contains(dx: Var): Boolean = x.contains(dx) || y.contains(dx)

  override def apply(env: Map[String, Tensor]): Vector = x(env) + y(env)

  override def d(dx: Var): Vector = {
    x.d(dx) + y.d(dx)
  }

  override def simplify: Vector = (x, y) match {
    case (VectorVal(a), VectorVal(b)) => VectorVal(a.zip(b).map { case (x, y) => x + y })
    case (VectorZero(_), b) => b
    case (a, VectorZero(_)) => a
    case (VectorNeg(a), VectorNeg(b)) => -(a + b)
    case (VectorNeg(a), b) if a == b => VectorZero(a.size)
    case (a, VectorNeg(b)) if a == b => VectorZero(a.size)
    case (ScalarVectorProduct(a, b), ScalarVectorProduct(c, d)) if b == d => (a + c) * b
    case (ScalarVectorProduct(a, b), ScalarVectorProduct(c, d)) if a == c => a * (b + d)
    case (ScalarVectorProduct(a, b), c) if b == c => (a + 1) * b
    case (a, ScalarVectorProduct(b, c)) if a == c => (b + 1) * a
    case (a, b) if a == b => 2 * a
    case (a, b) => VectorAdd(a, b)
  }
}

/** -x */
case class VectorNeg(x: Vector) extends Vector {
  override def toString: String = x match {
    case VectorAdd(_, _) => s"-($x)"
    case _ => s"-$x"
  }

  override def size: IntScalar = x.size

  override def contains(dx: Var): Boolean = x.contains(dx)

  override def apply(env: Map[String, Tensor]): Vector = -x(env)

  override def d(dx: Var): Vector = {
    -x.d(dx)
  }

  override def simplify: Vector = x match {
    case a @ VectorZero(_) => a
    case VectorVal(a) => VectorVal(a.map(-_))
    case VectorNeg(a) => a
    case a => VectorNeg(a)
  }
}

/** x * y */
case class ScalarVectorProduct(a: Scalar, x: Vector) extends Vector {
  override def toString: String = {
    val xs = x match {
      case VectorAdd(_, _) => s"($x)"
      case _ => x.toString
    }

    val as = a match {
      case Add(_, _) | Sub(_, _) | Power(_, _) | InnerProduct(_, _) => s"($a)"
      case _ => a.toString
    }

    s"$as * $xs"
  }

  override def size: IntScalar = x.size

  override def contains(dx: Var): Boolean = x.contains(dx)

  override def apply(env: Map[String, Tensor]): Vector = a(env) * x(env)

  override def d(dx: Var): Vector = a * x.d(dx)

  override def simplify: Vector = (a, x) match {
    case (Val(0), _) => VectorZero(x.size)
    case (Val(1), b) => b
    case (Val(-1), b) => -b
    case (Val(a), VectorVal(b)) => VectorVal(b.map(_ * a))
    case (Neg(a), VectorNeg(b)) => a * b
    case (Neg(a), b) => -(a * b)
    case (a, VectorNeg(b)) => -(a * b)
    case (a, ScalarVectorProduct(b, c)) => (a * b) * c
    case (a, b) => ScalarVectorProduct(a, b)
  }
}

/** Inner product (x * y) */
case class InnerProduct(x: Vector, y: Vector) extends Scalar {
  override def toString: String = {
    val xs = x match {
      case VectorAdd(_, _) | ScalarVectorProduct(_, _) => s"($x)"
      case _ => x.toString
    }

    val ys = y match {
      case VectorAdd(_, _) | ScalarVectorProduct(_, _) => s"($y)"
      case _ => y.toString
    }

    s"$xs \u00B7 $ys"
  }

  override def contains(dx: Var): Boolean = x.contains(dx) || y.contains(dx)

  override def apply(env: Map[String, Tensor]): Scalar = x(env) * y(env)

  override def d(dx: Var): Scalar = {
    (x.d(dx) * y) + (x * y.d(dx))
  }

  override def simplify: Scalar = (x, y) match {
    case (VectorZero(_), _) => Val(0)
    case (_, VectorZero(_)) => Val(0)
    case (VectorVal(a), VectorVal(b)) => Val(a.zip(b).map { case (x, y) => x * y }.sum)
    case (a, b) => InnerProduct(a, b)
  }
}
