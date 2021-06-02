/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.math

import scala.reflect.ClassTag
import com.typesafe.scalalogging.LazyLogging
import smile.math.blas.Transpose.{NO_TRANSPOSE, TRANSPOSE}
import smile.math.matrix.Matrix

/**
 * Vector Expression.
 */
sealed trait VectorExpression {
  def length: Int
  def apply(i: Int): Double
  def apply(slice: Slice): Array[Double] = {
    val vector = simplify
    slice.toRange(length).map(vector.apply).toArray
  }

  def simplify: VectorExpression
  def toArray: Array[Double] = {
    val z = new Array[Double](length)
    for (i <- 0 until length) z(i) = apply(i)
    z
  }

  /** Dot product. */
  def %*% (b: VectorExpression): Double = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match for dot product: $length %*% ${b.length}")
    MathEx.dot(toArray, b.toArray)
  }

  def + (b: VectorExpression): VectorAddVector = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: $length + ${b.length}")
    VectorAddVector(this, b)
  }
  def - (b: VectorExpression): VectorSubVector = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: $length - ${b.length}")
    VectorSubVector(this, b)
  }
  def * (b: VectorExpression): VectorMulVector = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: $length * ${b.length}")
    VectorMulVector(this, b)
  }
  def / (b: VectorExpression): VectorDivVector = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: $length / ${b.length}")
    VectorDivVector(this, b)
  }

  def + (b: Double): VectorAddValue = VectorAddValue(this, b)
  def - (b: Double): VectorSubValue = VectorSubValue(this, b)
  def * (b: Double): VectorMulValue = VectorMulValue(this, b)
  def / (b: Double): VectorDivValue = VectorDivValue(this, b)
}

case class VectorLift(x: Array[Double]) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i)
  override def simplify: VectorExpression = this
  override def toArray: Array[Double] = x
}

case class VectorAddValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorAddValue(x.simplify, y)
  override def apply(i: Int): Double = x(i) + y
}

case class VectorSubValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorSubValue(x.simplify, y)
  override def apply(i: Int): Double = x(i) - y
}

case class VectorMulValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorMulValue(x.simplify, y)
  override def apply(i: Int): Double = x(i) * y
}

case class VectorDivValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorDivValue(x.simplify, y)
  override def apply(i: Int): Double = x(i) / y
}

case class ValueAddVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = ValueAddVector(y, x.simplify)
  override def apply(i: Int): Double = y + x(i)
}

case class ValueSubVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = ValueSubVector(y, x.simplify)
  override def apply(i: Int): Double = y - x(i)
}

case class ValueMulVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = ValueMulVector(y, x.simplify)
  override def apply(i: Int): Double = y * x(i)
}

case class ValueDivVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = ValueDivVector(y, x.simplify)
  override def apply(i: Int): Double = y / x(i)
}

case class VectorAddVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorAddVector(x.simplify, y.simplify)
  override def apply(i: Int): Double = x(i) + y(i)
}

case class VectorSubVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorSubVector(x.simplify, y.simplify)
  override def apply(i: Int): Double = x(i) - y(i)
}

case class VectorMulVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorMulVector(x.simplify, y.simplify)
  override def apply(i: Int): Double = x(i) * y(i)
}

case class VectorDivVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = VectorDivVector(x.simplify, y.simplify)
  override def apply(i: Int): Double = x(i) / y(i)
}

case class AbsVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = AbsVector(x.simplify)
  override def apply(i: Int): Double = Math.abs(x(i))
}

case class AcosVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = AcosVector(x.simplify)
  override def apply(i: Int): Double = Math.acos(x(i))
}

case class AsinVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = AsinVector(x.simplify)
  override def apply(i: Int): Double = Math.asin(x(i))
}

case class AtanVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = AtanVector(x.simplify)
  override def apply(i: Int): Double = Math.atan(x(i))
}

case class CbrtVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = CbrtVector(x.simplify)
  override def apply(i: Int): Double = Math.cbrt(x(i))
}

case class CeilVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = CeilVector(x.simplify)
  override def apply(i: Int): Double = Math.ceil(x(i))
}

case class ExpVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = ExpVector(x.simplify)
  override def apply(i: Int): Double = Math.exp(x(i))
}

case class Expm1Vector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = Expm1Vector(x.simplify)
  override def apply(i: Int): Double = Math.expm1(x(i))
}

case class FloorVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = FloorVector(x.simplify)
  override def apply(i: Int): Double = Math.floor(x(i))
}

case class LogVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = LogVector(x.simplify)
  override def apply(i: Int): Double = Math.log(x(i))
}

case class Log2Vector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = Log2Vector(x.simplify)
  override def apply(i: Int): Double = MathEx.log2(x(i))
}

case class Log10Vector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = Log10Vector(x.simplify)
  override def apply(i: Int): Double = Math.log10(x(i))
}

case class Log1pVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = Log1pVector(x.simplify)
  override def apply(i: Int): Double = Math.log1p(x(i))
}

case class RoundVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = RoundVector(x.simplify)
  override def apply(i: Int): Double = Math.round(x(i)).toDouble
}

case class SinVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = SinVector(x.simplify)
  override def apply(i: Int): Double = Math.sin(x(i))
}

case class SqrtVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = SqrtVector(x.simplify)
  override def apply(i: Int): Double = Math.sqrt(x(i))
}

case class TanVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = TanVector(x.simplify)
  override def apply(i: Int): Double = Math.tan(x(i))
}

case class TanhVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def simplify: VectorExpression = TanhVector(x.simplify)
  override def apply(i: Int): Double = Math.tanh(x(i))
}

case class Ax(A: MatrixExpression, x: VectorExpression) extends VectorExpression {
  override def length: Int = A.nrow
  override def simplify: VectorExpression = VectorLift(toArray)
  override def apply(i: Int): Double = throw new UnsupportedOperationException("Call simplify first")
  override lazy val toArray: Array[Double] = {
    A.toMatrix.mv(x)
  }
}

sealed trait MatrixExpression {
  def nrow: Int
  def ncol: Int
  def apply(i: Int, j: Int): Double

  def simplify: MatrixExpression
  def toMatrix: Matrix = {
    val z = new Matrix(nrow, ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = apply(i, j)
    z
  }

  def + (b: MatrixExpression): MatrixAddMatrix = {
    if (nrow != b.nrow || ncol != b.ncol) throw new IllegalArgumentException(s"Matrix sizes don't match: $nrow x $ncol + ${b.nrow} x ${b.ncol}")
    MatrixAddMatrix(this, b)
  }
  def - (b: MatrixExpression): MatrixSubMatrix = {
    if (nrow != b.nrow || ncol != b.ncol) throw new IllegalArgumentException(s"Matrix sizes don't match: $nrow x $ncol - ${b.nrow} x ${b.ncol}")
    MatrixSubMatrix(this, b)
  }
  /** Element-wise multiplication */
  def * (b: MatrixExpression): MatrixMulMatrix = {
    if (nrow != b.nrow || ncol != b.ncol) throw new IllegalArgumentException(s"Matrix sizes don't match: $nrow x $ncol * ${b.nrow} x ${b.ncol}")
    MatrixMulMatrix(this, b)
  }
  def / (b: MatrixExpression): MatrixDivMatrix = {
    if (nrow != b.nrow || ncol != b.ncol) throw new IllegalArgumentException(s"Matrix sizes don't match: $nrow x $ncol / ${b.nrow} x ${b.ncol}")
    MatrixDivMatrix(this, b)
  }

  /** Matrix transpose */
  def t: MatrixTranspose = MatrixTranspose(this)

  /** A * x */
  def * (x: VectorExpression): Ax = Ax(this, x)

  /** Matrix multiplication A * B */
  def %*% (b: MatrixExpression): MatrixExpression = {
    if (ncol != b.nrow) throw new IllegalArgumentException(s"Matrix sizes don't match for matrix multiplication: $nrow x $ncol %*% ${b.nrow} x ${b.ncol}")
    MatrixMultiplication(this, b)
  }

  def + (b: Double): MatrixAddValue = MatrixAddValue(this, b)
  def - (b: Double): MatrixSubValue = MatrixSubValue(this, b)
  def * (b: Double): MatrixMulValue = MatrixMulValue(this, b)
  def / (b: Double): MatrixDivValue = MatrixDivValue(this, b)
}

case class MatrixLift(A: Matrix) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = this
  override def apply(i: Int, j: Int): Double = A(i, j)
  override def toMatrix: Matrix = A
}

case class MatrixTranspose(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.ncol
  override def ncol: Int = A.nrow
  override def simplify: MatrixExpression = MatrixTranspose(A.simplify)
  override def apply(i: Int, j: Int): Double = A(j, i)
  override def toMatrix: Matrix = A.toMatrix.transpose()
}

case class MatrixMultiplication(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = B.ncol
  override def simplify: MatrixExpression = MatrixLift(toMatrix)
  override def apply(i: Int, j: Int): Double = throw new UnsupportedOperationException("Call simplify first")
  override lazy val toMatrix: Matrix = {
    (A, B) match {
      case (MatrixTranspose(A), MatrixTranspose(B)) => B.toMatrix.mm(A.toMatrix).transpose()
      case (MatrixTranspose(A), _) => A.toMatrix.tm(B.toMatrix)
      case (_, MatrixTranspose(B)) => A.toMatrix.mt(B.toMatrix)
      case (_, _) => A.toMatrix.mm(B.toMatrix)
    }
  }

  override def %*% (C: MatrixExpression): MatrixMultiplicationChain = MatrixMultiplicationChain(Seq(A, B, C))
}

case class MatrixMultiplicationChain(A: Seq[MatrixExpression]) extends MatrixExpression {
  override def nrow: Int = A.head.nrow
  override def ncol: Int = A.last.ncol
  override def simplify: MatrixExpression = MatrixLift(toMatrix)
  override def apply(i: Int, j: Int): Double = throw new UnsupportedOperationException("Call simplify first")
  override def %*% (B: MatrixExpression): MatrixMultiplicationChain = MatrixMultiplicationChain(A :+ B)

  override lazy val toMatrix: Matrix = {
    val dims = (A.head.nrow +: A.map(_.ncol)).toArray
    val n = dims.length - 1
    val order = new MatrixOrderOptimization(dims)
    toMatrix(order.s, 0, n - 1)
  }

  private def toMatrix(s: Array[Array[Int]], i: Int, j: Int): Matrix = {
    if (i == j) return A(i)

    val Ai = toMatrix(s, i, s(i)(j))
    val Aj = toMatrix(s, s(i)(j) + 1, j)
    Ai.mm(Aj)
  }
}

case class MatrixAddValue(A: MatrixExpression, x: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixAddValue(A.simplify, x)
  override def apply(i: Int, j: Int): Double = A(i, j) + x
}
case class MatrixSubValue(A: MatrixExpression, x: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixSubValue(A.simplify, x)
  override def apply(i: Int, j: Int): Double = A(i, j) - x
}
case class MatrixMulValue(A: MatrixExpression, x: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixMulValue(A.simplify, x)
  override def apply(i: Int, j: Int): Double = A(i, j) * x
}
case class MatrixDivValue(A: MatrixExpression, x: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixDivValue(A.simplify, x)
  override def apply(i: Int, j: Int): Double = A(i, j) / x
}

case class ValueAddMatrix(x: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = ValueAddMatrix(x, A.simplify)
  override def apply(i: Int, j: Int): Double = x + A(i, j)
}
case class ValueSubMatrix(x: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = ValueSubMatrix(x, A.simplify)
  override def apply(i: Int, j: Int): Double = x - A(i, j)
}
case class ValueMulMatrix(x: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = ValueMulMatrix(x, A.simplify)
  override def apply(i: Int, j: Int): Double = x * A(i, j)
}
case class ValueDivMatrix(x: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = ValueDivMatrix(x, A.simplify)
  override def apply(i: Int, j: Int): Double = x / A(i, j)
}

case class MatrixAddMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixAddMatrix(A.simplify, B.simplify)
  override def apply(i: Int, j: Int): Double = A(i, j) + B(i, j)
}
case class MatrixSubMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixSubMatrix(A.simplify, B.simplify)
  override def apply(i: Int, j: Int): Double = A(i, j) - B(i, j)
}
case class MatrixMulMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixMulMatrix(A.simplify, B.simplify)
  override def apply(i: Int, j: Int): Double = A(i, j) * B(i, j)
}
case class MatrixDivMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = MatrixDivMatrix(A.simplify, B.simplify)
  override def apply(i: Int, j: Int): Double = A(i, j) / B(i, j)
}

/**
 * Optimizes the order of matrix multiplication chain.
 * Matrix multiplication is associative. However, the complexity of
 * matrix multiplication chain is not associative.
 * @param dims Matrix A[i] has dimension dims[i-1] x dims[i] for i = 1..n
 */
class MatrixOrderOptimization(dims: Array[Int]) extends LazyLogging {
  val n: Int = dims.length - 1

  // m[i,j] = Minimum number of scalar multiplications (i.e., cost)
  // needed to compute the matrix A[i]A[i+1]...A[j] = A[i..j]
  // The cost is zero when multiplying one matrix
  val m: Array[Array[Int]] = Array.ofDim[Int](n, n)
  // Index of the subsequence split that achieved minimal cost
  val s: Array[Array[Int]] = Array.ofDim[Int](n, n)

  for (l <- 1 until n) {
    for (i <- 0 until (n - l)) {
      val j = i + l
      m(i)(j) = Int.MaxValue
      for(k <- i until j) {
        val cost = m(i)(k) + m(k+1)(j) + dims(i) * dims(k+1) * dims(j+1)
        if (cost < m(i)(j)) {
          m(i)(j) = cost
          s(i)(j) = k
        }
      }
    }
  }

  logger.info("The minimum cost of matrix multiplication chain: {}", m(0)(n-1))

  override def toString: String = {
    val sb = new StringBuilder
    val intermediate = new Array[Boolean](n)
    buildString(sb, 0, n - 1, intermediate)
    sb.toString
  }

  private def buildString(sb: StringBuilder, i: Int, j: Int, intermediate: Array[Boolean]): Unit = {
    if (i != j) {
      sb.append('(')
      buildString(sb, i, s(i)(j), intermediate)
      if (!intermediate(i)) sb.append(dims(i)).append('x').append(dims(i+1))

      sb.append(" * ")

      buildString(sb, s(i)(j) + 1, j, intermediate)
      if (!intermediate(j)) sb.append(dims(j)).append('x').append(dims(j+1))
      sb.append(')')

      intermediate(i) = true
      intermediate(j) = true
    }
  }
}

case class AbsMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = AbsMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.abs(A(i, j))
}

case class AcosMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = AcosMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.acos(A(i, j))
}

case class AsinMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = AsinMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.asin(A(i, j))
}

case class AtanMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = AtanMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.atan(A(i, j))
}

case class CbrtMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = CbrtMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.cbrt(A(i, j))
}

case class CeilMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = CeilMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.ceil(A(i, j))
}

case class ExpMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = ExpMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.exp(A(i, j))
}

case class Expm1Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = Expm1Matrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.expm1(A(i, j))
}

case class FloorMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = FloorMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.floor(A(i, j))
}

case class LogMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = LogMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.log(A(i, j))
}

case class Log2Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = Log2Matrix(A.simplify)
  override def apply(i: Int, j: Int): Double = MathEx.log2(A(i, j))
}

case class Log10Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = Log10Matrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.log10(A(i, j))
}

case class Log1pMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = Log1pMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.log1p(A(i, j))
}

case class RoundMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = RoundMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.abs(A(i, j))
}

case class SinMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = SinMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.sin(A(i, j))
}

case class SqrtMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = SqrtMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.sqrt(A(i, j))
}

case class TanMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = TanMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.tan(A(i, j))
}

case class TanhMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def simplify: MatrixExpression = TanhMatrix(A.simplify)
  override def apply(i: Int, j: Int): Double = Math.tanh(A(i, j))
}

private[math] abstract class PimpedArrayLike[T: ClassTag] {

  val a: Array[T]

  /** Get an element */
  def apply(rows: Int*): Array[T] = rows.map(row => a(row)).toArray

  /** Get a range of array */
  def apply(rows: Range): Array[T] = rows.map(row => a(row)).toArray

  /** Sampling the data.
    * @param n the number of samples.
    * @return samples
    */
  def sample(n: Int): Array[T] = {
    val perm = a.indices.toArray
    MathEx.permutate(perm)
    (0 until n).map(i => a(perm(i))).toArray
  }

  /** Sampling the data.
    * @param f the fraction of samples.
    * @return samples
    */
  def sample(f: Double): Array[T] = sample(Math.round(a.length * f).toInt)
}

private[math] class PimpedArray[T](override val a: Array[T])(implicit val tag: ClassTag[T]) extends PimpedArrayLike[T]

private[math] class PimpedArray2D(override val a: Array[Array[Double]])(implicit val tag: ClassTag[Array[Double]]) extends PimpedArrayLike[Array[Double]] {
  def toMatrix: Matrix = Matrix.of(a)

  def nrow: Int = a.length
  def ncol: Int = a(0).length

  /** Returns a submatrix. */
  def apply(rows: Range, cols: Range): Array[Array[Double]] = rows.map { row =>
    val x = a(row)
    cols.map { col => x(col) }.toArray
  }.toArray

  /** Returns a column. */
  def $(col: Int): Array[Double] = a.map(_(col))

  /** Returns multiple rows. */
  def row(i: Int*): Array[Array[Double]] = apply(i: _*)

  /** Returns a range of rows. */
  def row(i: Range): Array[Array[Double]] = apply(i)

  /** Returns multiple columns. */
  def col(j: Int*): Array[Array[Double]] = a.map { x =>
    j.map { col => x(col) }.toArray
  }

  /** Returns a range of columns. */
  def col(j: Range): Array[Array[Double]] = a.map { x =>
    j.map { col => x(col) }.toArray
  }
}

/** Python like slicing. */
case class Slice(start: Int, end: Int, step: Int = 1) {
  def ~ (step: Int): Slice = copy(step=step)

  def toRange(length: Int): Range =
    Range(index(start, length), index(end, length), step)

  def toArray(length: Int): Array[Int] =
    Range(index(start, length), index(end, length), step).toArray

  private def index(i: Int, length: Int): Int =
    if (i < 0) length + i else i
}

private[math] case class PimpedInt(a: Int) {
  def ~ : Slice = Slice(a, -1)
  def ~ (b: Int): Slice = Slice(a, b)
  def unary_~ (b: Int): Slice = Slice(0, b)
}

private[math] case class PimpedDouble(a: Double) {
  def + (b: Array[Double]): ValueAddVector = ValueAddVector(a, b)
  def - (b: Array[Double]): ValueSubVector = ValueSubVector(a, b)
  def * (b: Array[Double]): ValueMulVector = ValueMulVector(a, b)
  def / (b: Array[Double]): ValueDivVector = ValueDivVector(a, b)

  def + (b: VectorExpression): ValueAddVector = ValueAddVector(a, b)
  def - (b: VectorExpression): ValueSubVector = ValueSubVector(a, b)
  def * (b: VectorExpression): ValueMulVector = ValueMulVector(a, b)
  def / (b: VectorExpression): ValueDivVector = ValueDivVector(a, b)

  def + (b: Matrix): ValueAddMatrix = ValueAddMatrix(a, b)
  def - (b: Matrix): ValueSubMatrix = ValueSubMatrix(a, b)
  def * (b: Matrix): ValueMulMatrix = ValueMulMatrix(a, b)
  def / (b: Matrix): ValueDivMatrix = ValueDivMatrix(a, b)

  def + (b: MatrixExpression): ValueAddMatrix = ValueAddMatrix(a, b)
  def - (b: MatrixExpression): ValueSubMatrix = ValueSubMatrix(a, b)
  def * (b: MatrixExpression): ValueMulMatrix = ValueMulMatrix(a, b)
  def / (b: MatrixExpression): ValueDivMatrix = ValueDivMatrix(a, b)
}

private[math] class PimpedDoubleArray(override val a: Array[Double]) extends PimpedArray[Double](a) {
  def toMatrix: Matrix = Matrix.column(a)

  def += (b: Double): Array[Double] = a.mapInPlace(_ + b)
  def -= (b: Double): Array[Double] = a.mapInPlace(_ - b)
  def *= (b: Double): Array[Double] = a.mapInPlace(_ * b)
  def /= (b: Double): Array[Double] = a.mapInPlace(_ / b)
  def ^= (b: Double): Array[Double] = a.mapInPlace(math.pow(_, b))

  def += (b: VectorExpression): Array[Double] = {
    for (i <- a.indices) a(i) += b(i)
    a
  }
  def -= (b: VectorExpression): Array[Double] = {
    for (i <- a.indices) a(i) -= b(i)
    a
  }
  def *= (b: VectorExpression): Array[Double] = {
    for (i <- a.indices) a(i) *= b(i)
    a
  }
  def /= (b: VectorExpression): Array[Double] = {
    for (i <- a.indices) a(i) /= b(i)
    a
  }
}

private[math] class MatrixOps(a: Matrix) {
  def apply(i: Slice, j: Slice): Matrix = (i, j) match {
    case (Slice(0, -1, 1), Slice(0, -1, 1)) => a
    case (Slice(0, -1, 1), _) => a.cols(j.toRange(a.ncol): _*)
    case (_, Slice(0, -1, 1)) => a.rows(i.toRange(a.nrow): _*)
    case (_, _) =>
      val rows = i.toRange(a.nrow)
      val cols = j.toRange(a.ncol)
      val z = new Matrix(rows.length, cols.length)
      for (j <- 0 until z.ncol) for (i <- 0 until z.nrow) z(i, j) = a.apply(i, j)
      z
  }

  def apply(topLeft: (Int, Int), bottomRight: (Int, Int)): Matrix =
    a.submatrix(topLeft._1, topLeft._2, bottomRight._1, bottomRight._2)

  def := (b: MatrixExpression): Matrix = b match {
    case MatrixLift(b) => a.set(b.toMatrix)
    case b if a.nrow != b.nrow || a.ncol != b.ncol => a.set(b.toMatrix)
    case MatrixMultiplication(MatrixLift(_A: Matrix), MatrixLift(_B: Matrix)) =>
      a.mm(NO_TRANSPOSE, _A, NO_TRANSPOSE, _B, 1.0, 0.0)
    case MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixLift(_B: Matrix)) =>
      a.mm(TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B, 1.0, 0.0)
    case MatrixMultiplication(MatrixLift(_A: Matrix), MatrixTranspose(_B: MatrixExpression)) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, 1.0, 0.0)
    case MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixTranspose(_B: MatrixExpression)) =>
      a.mm(TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, 1.0, 0.0)
    case MatrixMultiplication(_A: MatrixExpression, _B: MatrixExpression) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B.toMatrix, 1.0, 0.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixLift(_A: Matrix), MatrixLift(_B: Matrix))) =>
      a.mm(NO_TRANSPOSE, _A, NO_TRANSPOSE, _B, alpha, 0.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixLift(_B: Matrix))) =>
      a.mm(TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B, alpha, 0.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixLift(_A: Matrix), MatrixTranspose(_B: MatrixExpression))) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 0.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixTranspose(_B: MatrixExpression))) =>
      a.mm(TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 0.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(_A: MatrixExpression, _B: MatrixExpression)) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B.toMatrix, alpha, 0.0)
    case MatrixMulValue(MatrixMultiplication(MatrixLift(_A: Matrix), MatrixLift(_B: Matrix)), alpha) =>
      a.mm(NO_TRANSPOSE, _A, NO_TRANSPOSE, _B, alpha, 0.0)
    case MatrixMulValue(MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixLift(_B: Matrix)), alpha) =>
      a.mm(TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B, alpha, 0.0)
    case MatrixMulValue(MatrixMultiplication(MatrixLift(_A: Matrix), MatrixTranspose(_B: MatrixExpression)), alpha) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 0.0)
    case MatrixMulValue(MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixTranspose(_B: MatrixExpression)), alpha) =>
      a.mm(TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 0.0)
    case MatrixMulValue(MatrixMultiplication(_A: MatrixExpression, _B: MatrixExpression), alpha) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B.toMatrix, alpha, 0.0)
    case _ =>
      val c = b.simplify
      for (j <- 0 until a.ncol) for (i <- 0 until a.nrow) a(i, j) = c(i, j)
      a
  }

  def += (b: MatrixExpression): Matrix = b match {
    case MatrixMultiplication(MatrixLift(_A: Matrix), MatrixLift(_B: Matrix)) =>
      a.mm(NO_TRANSPOSE, _A, NO_TRANSPOSE, _B, 1.0, 1.0)
    case MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixLift(_B: Matrix)) =>
      a.mm(TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B, 1.0, 1.0)
    case MatrixMultiplication(MatrixLift(_A: Matrix), MatrixTranspose(_B: MatrixExpression)) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, 1.0, 1.0)
    case MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixTranspose(_B: MatrixExpression)) =>
      a.mm(TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, 1.0, 1.0)
    case MatrixMultiplication(_A: MatrixExpression, _B: MatrixExpression) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B.toMatrix, 1.0, 1.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixLift(_A: Matrix), MatrixLift(_B: Matrix))) =>
      a.mm(NO_TRANSPOSE, _A, NO_TRANSPOSE, _B, alpha, 1.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixLift(_B: Matrix))) =>
      a.mm(TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B, alpha, 1.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixLift(_A: Matrix), MatrixTranspose(_B: MatrixExpression))) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 1.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixTranspose(_B: MatrixExpression))) =>
      a.mm(TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 1.0)
    case ValueMulMatrix(alpha, MatrixMultiplication(_A: MatrixExpression, _B: MatrixExpression)) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B.toMatrix, alpha, 1.0)
    case MatrixMulValue(MatrixMultiplication(MatrixLift(_A: Matrix), MatrixLift(_B: Matrix)), alpha) =>
      a.mm(NO_TRANSPOSE, _A, NO_TRANSPOSE, _B, alpha, 1.0)
    case MatrixMulValue(MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixLift(_B: Matrix)), alpha) =>
      a.mm(TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B, alpha, 1.0)
    case MatrixMulValue(MatrixMultiplication(MatrixLift(_A: Matrix), MatrixTranspose(_B: MatrixExpression)), alpha) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 1.0)
    case MatrixMulValue(MatrixMultiplication(MatrixTranspose(_A: MatrixExpression), MatrixTranspose(_B: MatrixExpression)), alpha) =>
      a.mm(TRANSPOSE, _A.toMatrix, TRANSPOSE, _B, alpha, 1.0)
    case MatrixMulValue(MatrixMultiplication(_A: MatrixExpression, _B: MatrixExpression), alpha) =>
      a.mm(NO_TRANSPOSE, _A.toMatrix, NO_TRANSPOSE, _B.toMatrix, alpha, 1.0)
    case MatrixMulValue(_B: MatrixLift, beta: Double) =>
      a.add(beta, _B.A)
    case ValueMulMatrix(beta: Double, _B: MatrixLift) =>
      a.add(beta, _B.A)
    case MatrixAddMatrix(_A: MatrixLift, _B: MatrixLift) =>
      a.add(1.0, _A.A, 1.0, _B.A)
    case MatrixAddMatrix(ValueMulMatrix(alpha: Double, _A: MatrixLift), _B: MatrixLift) =>
      a.add(alpha, _A.A, 1.0, _B.A)
    case MatrixAddMatrix(MatrixMulValue(_A: MatrixLift, alpha: Double), _B: MatrixLift) =>
      a.add(alpha, _A.A, 1.0, _B.A)
    case MatrixAddMatrix(_A: MatrixLift, ValueMulMatrix(beta: Double, _B: MatrixLift)) =>
      a.add(1.0, _A.A, beta, _B.A)
    case MatrixAddMatrix(_A: MatrixLift, MatrixMulValue(_B: MatrixLift, beta: Double)) =>
      a.add(1.0, _A.A, beta, _B.A)
    case MatrixAddMatrix(ValueMulMatrix(alpha: Double, _A: MatrixLift), ValueMulMatrix(beta: Double, _B: MatrixLift)) =>
      a.add(alpha, _A.A, beta, _B.A)
    case MatrixAddMatrix(MatrixMulValue(_A: MatrixLift, alpha: Double), ValueMulMatrix(beta: Double, _B: MatrixLift)) =>
      a.add(alpha, _A.A, beta, _B.A)
    case MatrixAddMatrix(ValueMulMatrix(alpha: Double, _A: MatrixLift), MatrixMulValue(_B: MatrixLift, beta: Double)) =>
      a.add(alpha, _A.A, beta, _B.A)
    case MatrixAddMatrix(MatrixMulValue(_A: MatrixLift, alpha: Double), MatrixMulValue(_B: MatrixLift, beta: Double)) =>
      a.add(alpha, _A.A, beta, _B.A)
    case _ =>
      val c = b.simplify
      for (j <- 0 until a.ncol) for (i <- 0 until a.nrow) a.add(i, j, c(i, j))
      a
  }

  def += (b: Double): Matrix = a.add(b)
  def -= (b: Double): Matrix = a.sub(b)
  def *= (b: Double): Matrix = a.mul(b)
  def /= (b: Double): Matrix = a.div(b)

  def += (b: Matrix): Matrix = a.add(b)
  def -= (b: Matrix): Matrix = a.sub(b)
  /** Element-wise multiplication */
  def *= (b: Matrix): Matrix = a.mul(b)
  /** Element-wise division */
  def /= (b: Matrix): Matrix = a.div(b)

  /** Solves A * x = b */
  def \ (b: VectorExpression): Array[Double] = {
    if (a.nrow == a.ncol)
      a.lu().solve(b.toArray)
    else
      a.qr().solve(b.toArray)
  }
}