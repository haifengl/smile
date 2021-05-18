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
import smile.math.matrix.Matrix

/**
 * Vector Expression.
 */
sealed trait VectorExpression {
  def length: Int
  def apply(i: Int): Double
  def toArray: Array[Double]
  override def toString: String = runtime.ScalaRunTime.stringOf(toArray)

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
  override def toArray: Array[Double] = x
}

case class VectorAddValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) + y
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) + y
    z
  }
}

case class VectorSubValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) - y
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) - y
    z
  }
}

case class VectorMulValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) * y
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) * y
    z
  }
}

case class VectorDivValue(x: VectorExpression, y: Double) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) / y
  override lazy val toArray: Array[Double] ={
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) / y
    z
  }
}

case class ValueAddVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = y + x(i)
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = y + x(i)
    z
  }
}

case class ValueSubVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = y - x(i)
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = y - x(i)
    z
  }
}

case class ValueMulVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = y * x(i)
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = y * x(i)
    z
  }
}

case class ValueDivVector(y: Double, x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) / y
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = y / x(i)
    z
  }
}

case class VectorAddVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) + y(i)
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) + y(i)
    z
  }
}

case class VectorSubVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) + y(i)
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) - y(i)
    z
  }
}

case class VectorMulVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) + y(i)
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) * y(i)
    z
  }
}

case class VectorDivVector(x: VectorExpression, y: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = x(i) + y(i)
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = x(i) / y(i)
    z
  }
}

case class AbsVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.abs(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.abs(x(i))
    z
  }
}

case class AcosVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.acos(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.acos(x(i))
    z
  }
}

case class AsinVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.asin(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.asin(x(i))
    z
  }
}

case class AtanVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.atan(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.atan(x(i))
    z
  }
}

case class CbrtVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.cbrt(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.cbrt(x(i))
    z
  }
}

case class CeilVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.ceil(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.ceil(x(i))
    z
  }
}

case class ExpVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.exp(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.exp(x(i))
    z
  }
}

case class Expm1Vector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.expm1(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.expm1(x(i))
    z
  }
}

case class FloorVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.floor(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.floor(x(i))
    z
  }
}

case class LogVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.log(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.log(x(i))
    z
  }
}

case class Log2Vector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = MathEx.log2(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = MathEx.log2(x(i))
    z
  }
}

case class Log10Vector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.log10(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.log10(x(i))
    z
  }
}

case class Log1pVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.log1p(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.log1p(x(i))
    z
  }
}

case class RoundVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.round(x(i)).toDouble
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.round(x(i)).toDouble
    z
  }
}

case class SinVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.sin(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.sin(x(i))
    z
  }
}

case class SqrtVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.sqrt(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.sqrt(x(i))
    z
  }
}

case class TanVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.tan(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.tan(x(i))
    z
  }
}

case class TanhVector(x: VectorExpression) extends VectorExpression {
  override def length: Int = x.length
  override def apply(i: Int): Double = Math.tanh(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.tanh(x(i))
    z
  }
}

sealed trait MatrixExpression {
  def nrow: Int
  def ncol: Int
  def apply(i: Int, j: Int): Double
  def toMatrix: Matrix
  override def toString: String = runtime.ScalaRunTime.stringOf(toMatrix)

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
  def * (b: VectorExpression): Ax = Ax(this, b)

  /** Matrix multiplication A * B */
  def %*% (b: MatrixExpression): MatrixExpression = {
    if (ncol != b.nrow) throw new IllegalArgumentException(s"Matrix sizes don't match for matrix multiplication: $nrow x $ncol %*% ${b.nrow} x ${b.ncol}")
    MatrixMultiplicationExpression(this, b)
  }

  def + (b: Double): MatrixAddValue = MatrixAddValue(this, b)
  def - (b: Double): MatrixSubValue = MatrixSubValue(this, b)
  def * (b: Double): MatrixMulValue = MatrixMulValue(this, b)
  def / (b: Double): MatrixDivValue = MatrixDivValue(this, b)
}

case class Ax(A: MatrixExpression, x: VectorExpression) extends VectorExpression {
  override def length: Int = A.nrow
  override def apply(i: Int): Double = toArray(i)
  override lazy val toArray: Array[Double] = {
    A.toMatrix.mv(x)
  }
}

case class MatrixLift(A: Matrix) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j)
  override def toMatrix: Matrix = A
}

case class MatrixTranspose(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.ncol
  override def ncol: Int = A.nrow
  override def apply(i: Int, j: Int): Double = A(j, i)
  override def toMatrix: Matrix = A.toMatrix.transpose()
}

case class MatrixMultiplicationExpression(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = B.ncol
  override def apply(i: Int, j: Int): Double = toMatrix(i, j)
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
  override def apply(i: Int, j: Int): Double = toMatrix(i, j)
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

case class MatrixAddValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) + y
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) + y
    z
  }
}
case class MatrixSubValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) - y
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) - y
    z
  }
}
case class MatrixMulValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) * y
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) * y
    z
  }
}
case class MatrixDivValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) / y
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) / y
    z
  }
}

case class ValueAddMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = y + A(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = y + A(i, j)
    z
  }
}
case class ValueSubMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = y - A(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = y - A(i, j)
    z
  }
}
case class ValueMulMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = y * A(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = y * A(i, j)
    z
  }
}
case class ValueDivMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = y / A(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = y / A(i, j)
    z
  }
}

case class MatrixAddMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) + B(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) + B(i, j)
    z
  }
}
case class MatrixSubMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) - B(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) - B(i, j)
    z
  }
}
case class MatrixMulMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) * B(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) * B(i, j)
    z
  }
}
case class MatrixDivMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = A(i, j) / B(i, j)
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = A(i, j) / B(i, j)
    z
  }
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
  override def apply(i: Int, j: Int): Double = Math.abs(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.abs(A(i, j))
    z
  }
}

case class AcosMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.acos(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.acos(A(i, j))
    z
  }
}

case class AsinMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.asin(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.asin(A(i, j))
    z
  }
}

case class AtanMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.atan(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.atan(A(i, j))
    z
  }
}

case class CbrtMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.cbrt(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.cbrt(A(i, j))
    z
  }
}

case class CeilMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.ceil(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.ceil(A(i, j))
    z
  }
}

case class ExpMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.exp(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.exp(A(i, j))
    z
  }
}

case class Expm1Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.expm1(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.expm1(A(i, j))
    z
  }
}

case class FloorMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.floor(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.floor(A(i, j))
    z
  }
}

case class LogMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.log(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.log(A(i, j))
    z
  }
}

case class Log2Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = MathEx.log2(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = MathEx.log2(A(i, j))
    z
  }
}

case class Log10Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.log10(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.log10(A(i, j))
    z
  }
}

case class Log1pMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.log1p(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.log1p(A(i, j))
    z
  }
}

case class RoundMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.abs(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.abs(A(i, j))
    z
  }
}

case class SinMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.sin(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.sin(A(i, j))
    z
  }
}

case class SqrtMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.sqrt(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.sqrt(A(i, j))
    z
  }
}

case class TanMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.tan(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.tan(A(i, j))
    z
  }
}

case class TanhMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrow: Int = A.nrow
  override def ncol: Int = A.ncol
  override def apply(i: Int, j: Int): Double = Math.tanh(A(i, j))
  override lazy val toMatrix: Matrix = {
    val z = new Matrix(A.nrow, A.ncol)
    for (j <- 0 until ncol)
      for (i <- 0 until nrow)
        z(i, j) = Math.tanh(A(i, j))
    z
  }
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

  def unary_~ = new Matrix(a)

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
  def unary_~ = new Matrix(a)

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

private[math] class PimpedMatrix(a: Matrix) {
  def += (i: Int, j: Int, x: Double): Double = a.add(i, j, x)
  def -= (i: Int, j: Int, x: Double): Double = a.sub(i, j, x)
  def *= (i: Int, j: Int, x: Double): Double = a.mul(i, j, x)
  def /= (i: Int, j: Int, x: Double): Double = a.div(i, j, x)

  def += (b: Double): Matrix = a.add(b)
  def -= (b: Double): Matrix = a.sub(b)
  def *= (b: Double): Matrix = a.mul(b)
  def /= (b: Double): Matrix = a.div(b)

  def += (b: Matrix): Matrix = a.add(1.0, b)
  def -= (b: Matrix): Matrix = a.sub(1.0, b)
  /** Element-wise multiplication */
  def *= (b: Matrix): Matrix = a.mul(1.0, b)
  /** Element-wise division */
  def /= (b: Matrix): Matrix = a.div(1.0, b)

  /** Solves A * x = b */
  def \ (b: Array[Double]): Array[Double] = {
    if (a.nrow == a.ncol)
      lu(a).solve(b)
    else
      qr(a).solve(b)
  }
}