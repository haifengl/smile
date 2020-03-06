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

/** Matrix: rank-2 tensor. */
trait Matrix extends Tensor {
  override def rank: Option[Int] = Some(2)
  override def shape: Option[Array[IntScalar]] = Some(Array(size._1, size._2))

  /** The size of matrix (rows x columns). */
  def size: (IntScalar, IntScalar)
  /** Returns the partial derivative. */
  def d(dx: Var): Matrix
  /** Applies the expression. */
  def apply(env: Map[String, Tensor]): Matrix
  /** Applies the expression. */
  def apply(env: (String, Tensor)*): Matrix = apply(Map(env: _*))
  /** Simplify the expression. */
  def simplify: Matrix = this

  def + (y: Matrix): Matrix = AddMatrix(this, y).simplify
  def - (y: Matrix): Matrix = AddMatrix(this, NegMatrix(y)).simplify

  def * (y: Scalar): Matrix = ScalarMatrixProduct(y, this).simplify
  def * (y: Vector): Vector = MatrixVectorProduct(this, y).simplify
  def * (y: Matrix): Matrix = MatrixProduct(this, y).simplify

  def unary_+ : Matrix = simplify
  def unary_- : Matrix = NegMatrix(this).simplify

  /** The transpose of matrix. */
  def t : Matrix = MatrixTranspose(this).simplify

  /** The inverse of matrix. */
  def inv : Matrix = {
    if (isInstanceOf[ZeroMatrix] || isInstanceOf[OneMatrix])
      throw new UnsupportedOperationException("Matrix is not of full rank")

    val s = size
    if (s._1 != s._2) throw new UnsupportedOperationException("Inverse of non square matrix")

    MatrixInverse(this).simplify
  }
}

/** Matrix of all 0's */
case class ZeroMatrix(size: (IntScalar, IntScalar) = (IntConst("m"), IntConst("n"))) extends Matrix {
  override def toString: String = "0"
  override def d(dx: Var): Matrix = this
  override def apply(env: Map[String, Tensor]): Matrix = this
}

/** Matrix of all 1's */
case class OneMatrix(size: (IntScalar, IntScalar) = (IntConst("m"), IntConst("n"))) extends Matrix {
  override def toString: String = "1"
  override def d(dx: Var): Matrix = this
  override def apply(env: Map[String, Tensor]): Matrix = this
}

/** Identity matrix */
case class IdentityMatrix(size: (IntScalar, IntScalar) = (IntConst("n"), IntConst("n"))) extends Matrix {
  override def toString: String = "I"
  override def d(dx: Var): Matrix = this
  override def apply(env: Map[String, Tensor]): Matrix = this
}

/** Constant matrix. */
case class ConstMatrix(symbol: String, size: (IntScalar, IntScalar) = (IntConst("m"), IntConst("n"))) extends Matrix {
  override def toString: String = symbol
  override def d(dx: Var): Matrix = ZeroMatrix(size)
  override def apply(env: Map[String, Tensor]): Matrix = env.get(symbol) match {
    case Some(x: ZeroMatrix) => x
    case Some(x: OneMatrix) => x
    case Some(x: IdentityMatrix) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected 0 | 1 | I")
  }
}

/** Abstract matrix variable */
case class MatrixVar(symbol: String, size: (IntScalar, IntScalar) = (IntConst("m"), IntConst("n"))) extends Matrix {
  override def toString: String = symbol

  override def d(dx: Var): Matrix = TangentMatrix(this, dx)

  override def apply(env: Map[String, Tensor]): Matrix = env.get(symbol) match {
    case None => this
    case Some(x : Matrix) => x
    case x => throw new IllegalArgumentException(s"Invalid type: ${x.getClass}, expected Matrix")
  }
}

/** Diagonal matrix */
case class DiagonalMatrix(x: Scalar*) extends Matrix {
  override def toString: String = x.mkString("diag[", ", ", "]")

  override def size: (IntScalar, IntScalar) = (IntVal(x.length), IntVal(x.length))

  override def d(dx: Var): Matrix = {
    DiagonalMatrix(x.map(_.d(dx)): _*).simplify
  }

  override def apply(env: Map[String, Tensor]): Matrix = DiagonalMatrix(x.map(_(env)): _*).simplify

  override def simplify: Matrix = {
    if (x.forall(_ == Val(1))) IdentityMatrix(size)
    else if (x.forall(_ == Val(0))) ZeroMatrix(size)
    else this
  }
}

/** Row-wise matrix */
case class RowMatrix(x: Vector*) extends Matrix {
  override def toString: String = x.mkString("row[", ", ", "]")

  override def size: (IntScalar, IntScalar) = (IntVal(x.length), IntVal(x.length))

  override def d(dx: Var): Matrix = {
    RowMatrix(x.map(_.d(dx)): _*).simplify
  }

  override def apply(env: Map[String, Tensor]): Matrix = RowMatrix(x.map(_(env)): _*).simplify

  override def simplify: Matrix = {
    if (x.forall(_ == OneVector(x.length))) OneMatrix(size)
    else if (x.forall(_ == ZeroVector(x.length))) ZeroMatrix(size)
    else this
  }
}

/** The derivative of a matrix y with respect to a scalar x. */
case class TangentMatrix(y: MatrixVar, x: Var) extends Matrix {
  override def toString: String = s"\u2202${y.symbol}/\u2202${x.symbol}"

  override def size: (IntScalar, IntScalar) = y.size

  override def d(dx: Var): Matrix = throw new UnsupportedOperationException("derivative of tangent matrix")

  override def apply(env: Map[String, Tensor]): Matrix = {
    val yv = env.get(y.symbol) match {
      case None | Some(ZeroMatrix(_)) | Some(OneMatrix(_)) | Some(IdentityMatrix(_)) | Some(ConstMatrix(_, _)) => y
      case Some(y: Matrix) => y
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}, expected Vector")
    }

    env.get(x.symbol) match {
      case None => yv.d(x)
      case Some(x: Var) => yv.d(x)
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}, expected Var")
    }
  }
}

/** A + B */
case class AddMatrix(A: Matrix, B: Matrix) extends Matrix {
  if (A.size != B.size) throw new IllegalArgumentException(s"Vector sizes mismatch: ${A.size} vs ${B.size}")

  override def toString: String = B match {
    case NegMatrix(B) => s"$A - $B"
    case _ => s"$A + $B"
  }

  override def equals(o: Any): Boolean = o match {
    case AddMatrix(a, b) => (A == a && B == b) || (A == b && B == a)
    case _ => false
  }

  override def size: (IntScalar, IntScalar) = A.size

  override def apply(env: Map[String, Tensor]): Matrix = A(env) + B(env)

  override def d(dx: Var): Matrix = {
    A.d(dx) + B.d(dx)
  }

  override def simplify: Matrix = (A, B) match {
    case (ZeroMatrix(_), b) => b
    case (a, ZeroMatrix(_)) => a
    case (a, NegMatrix(NegMatrix(b))) => a + b
    case (NegMatrix(a), NegMatrix(b)) => -(a + b)
    case (NegMatrix(a), b) if a == b => ZeroMatrix(a.size)
    case (a, NegMatrix(b)) if a == b => ZeroMatrix(a.size)
    case (ScalarMatrixProduct(a, b), ScalarMatrixProduct(c, d)) if b == d => (a + c) * b
    case (ScalarMatrixProduct(a, b), ScalarMatrixProduct(c, d)) if a == c => a * (b + d)
    case (ScalarMatrixProduct(a, b), c) if b == c => (a + 1) * b
    case (a, ScalarMatrixProduct(b, c)) if a == c => (b + 1) * a
    case (ScalarMatrixProduct(a, b), NegMatrix(ScalarMatrixProduct(c, d))) if b == d => (a - c) * b
    case (ScalarMatrixProduct(a, b), NegMatrix(ScalarMatrixProduct(c, d))) if a == c => a * (b - d)
    case (NegMatrix(ScalarMatrixProduct(a, b)), c) if b == c => (1 - a) * b
    case (a, NegMatrix(ScalarMatrixProduct(b, c))) if a == c => (1 - b) * a
    case (MatrixProduct(a, b), MatrixProduct(c, d)) if b == d => (a + c) * b
    case (MatrixProduct(a, b), MatrixProduct(c, d)) if a == c => a * (b + d)
    case (a, b) if a == b => 2 * a
    case (a, b) => AddMatrix(a, b)
  }
}

/** -A */
case class NegMatrix(A: Matrix) extends Matrix {
  override def toString: String = A match {
    case AddMatrix(_, _) | OuterProduct(_, _) => s"-($A)"
    case _ => s"-$A"
  }

  override def size: (IntScalar, IntScalar) = A.size

  override def apply(env: Map[String, Tensor]): Matrix = -A(env)

  override def d(dx: Var): Matrix = {
    -A.d(dx)
  }

  override def simplify: Matrix = A match {
    case a @ ZeroMatrix(_) => a
    case NegMatrix(a) => a
    case _ => this
  }
}

/** A' */
case class MatrixTranspose(A: Matrix) extends Matrix {
  override def toString: String = A match {
    case AddMatrix(_, _) | OuterProduct(_, _) => s"($A)'"
    case _ => s"$A'"
  }

  override def size: (IntScalar, IntScalar) = (A.size._2, A.size._1)

  override def apply(env: Map[String, Tensor]): Matrix = A(env).t

  override def d(dx: Var): Matrix = {
    A.d(dx).t
  }

  override def simplify: Matrix = A match {
    case ZeroMatrix(_) => ZeroMatrix(size)
    case MatrixTranspose(a) => a
    case _ => this
  }
}

/** inv(A) */
case class MatrixInverse(A: Matrix) extends Matrix {
  override def toString: String = s"inv($A)"

  override def size: (IntScalar, IntScalar) = A.size

  override def apply(env: Map[String, Tensor]): Matrix = A(env).inv

  override def d(dx: Var): Matrix = {
    -(A.inv * A.d(dx) * A.inv)
  }
}

/** a * A */
case class ScalarMatrixProduct(a: Scalar, A: Matrix) extends Matrix {
  override def toString: String = {
    val xs = A match {
      case AddMatrix(_, _) | OuterProduct(_, _) => s"($A)"
      case _ => A.toString
    }

    val as = a match {
      case Add(_, _) | Sub(_, _) | Power(_, _) | InnerProduct(_, _) => s"($a)"
      case _ => a.toString
    }

    s"$as * $xs"
  }

  override def size: (IntScalar, IntScalar) = A.size

  override def apply(env: Map[String, Tensor]): Matrix = a(env) * A(env)

  override def d(dx: Var): Matrix = {
    a * A.d(dx) + a.d(dx) * A
  }

  override def simplify: Matrix = (a, A) match {
    case (Val(0), _) => ZeroMatrix(size)
    case (_, ZeroMatrix(_)) => ZeroMatrix(size)
    case (Val(1), b) => b
    case (Val(-1), b) => -b
    case (Neg(a), NegMatrix(b)) => a * b
    case (Neg(a), b) => -(a * b)
    case (a, NegMatrix(b)) => -(a * b)
    case (a, ScalarMatrixProduct(b, c)) => (a * b) * c
    case _ => this
  }
}

/** Matrix vector multiplication (A * x) */
case class MatrixVectorProduct(A: Matrix, x: Vector) extends Vector {
  if (A.size._2 != x.size) throw new IllegalArgumentException(s"Matrix and vector sizes mismatch: ${A.size} vs ${x.size}")

  override def toString: String = {
    val xs = x match {
      case AddVector(_, _) => s"($x)"
      case _ => x.toString
    }

    val as = A match {
      case AddMatrix(_, _) | OuterProduct(_, _) => s"($A)"
      case _ => A.toString
    }

    s"$as * $xs"
  }

  override def size: IntScalar = A.size._1

  override def apply(env: Map[String, Tensor]): Vector = A(env) * x(env)

  override def d(dx: Var): Vector = {
    A * x.d(dx) + A.d(dx) * x
  }

  override def d(dx: VectorVar): Matrix = {
    throw new UnsupportedOperationException
  }

  override def simplify: Vector = (A, x) match {
    case (ZeroMatrix(_), _) => ZeroVector(size)
    case (_, ZeroVector(_)) => ZeroVector(size)
    case (IdentityMatrix(_), b) => b
    case (NegMatrix(a), NegVector(b)) => a * b
    case (NegMatrix(a), b) => -(a * b)
    case (a, NegVector(b)) => -(a * b)
    case _ => this
  }
}

/** Matrix multiplication (A * B) */
case class MatrixProduct(A: Matrix, B: Matrix) extends Matrix {
  if (A.size._2 != B.size._1) throw new IllegalArgumentException(s"Matrix multiplication size mismatch: ${A.size} x ${B.size}")

  override def toString: String = {
    val xs = A match {
      case AddMatrix(_, _) | OuterProduct(_, _) => s"($A)"
      case _ => A.toString
    }

    val ys = B match {
      case AddMatrix(_, _) | OuterProduct(_, _) => s"($B)"
      case _ => B.toString
    }

    s"$xs$ys"
  }

  override def size: (IntScalar, IntScalar) = (A.size._1, B.size._2)

  override def apply(env: Map[String, Tensor]): Matrix = A(env) * B(env)

  override def d(dx: Var): Matrix = {
    (A.d(dx) * B) + (A * B.d(dx))
  }

  override def simplify: Matrix = (A, B) match {
    case (ZeroMatrix(_), _) => ZeroMatrix(size)
    case (_, ZeroMatrix(_)) => ZeroMatrix(size)
    case (IdentityMatrix(_), b) => b
    case (a, IdentityMatrix(_)) => a
    case (NegMatrix(IdentityMatrix(_)), b) => -b
    case (a, NegMatrix(IdentityMatrix(_))) => a
    case (a, MatrixInverse(b)) if a == b => IdentityMatrix(size)
    case (MatrixInverse(a), b) if a == b => IdentityMatrix(size)
    case (a, NegMatrix(MatrixInverse(b))) if a == b => -IdentityMatrix(size)
    case (NegMatrix(MatrixInverse(a)), b) if a == b => -IdentityMatrix(size)
    case (NegMatrix(a), NegMatrix(b)) => a * b
    case (a, NegMatrix(b)) => -(a * b)
    case (NegMatrix(a), b) => -(a * b)
    case (MatrixInverse(a), MatrixInverse(b)) => MatrixInverse(b * a)
    case _ => this
  }
}
