/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.math

import smile.math.matrix.{Matrix, DenseMatrix}
import smile.util.Logging

/**
 * Vector Expression.
 */
sealed trait VectorExpression {
  def length: Int
  def apply(i: Int): Double
  def toArray: Array[Double]
  override def toString = runtime.ScalaRunTime.stringOf(toArray)

  def + (b: VectorExpression) = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: ${length} + ${b.length}")
    VectorAddVector(this, b)
  }
  def - (b: VectorExpression) = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: ${length} - ${b.length}")
    VectorSubVector(this, b)
  }
  def * (b: VectorExpression) = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: ${length} * ${b.length}")
    VectorMulVector(this, b)
  }
  def / (b: VectorExpression) = {
    if (length != b.length) throw new IllegalArgumentException(s"Vector sizes don't match: ${length} / ${b.length}")
    VectorDivVector(this, b)
  }

  def + (b: Double) = VectorAddValue(this, b)
  def - (b: Double) = VectorSubValue(this, b)
  def * (b: Double) = VectorMulValue(this, b)
  def / (b: Double) = VectorDivValue(this, b)
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
  override def apply(i: Int): Double = Math.log2(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.log2(x(i))
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
  override def apply(i: Int): Double = Math.round(x(i))
  override lazy val toArray: Array[Double] = {
    val z = new Array[Double](x.length)
    for (i <- 0 until x.length) z(i) = Math.round(x(i))
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
  def nrows: Int
  def ncols: Int
  def apply(i: Int, j: Int): Double
  def toMatrix: DenseMatrix
  override def toString = runtime.ScalaRunTime.stringOf(toMatrix)

  def + (b: MatrixExpression) = {
    if (nrows != b.nrows || ncols != b.ncols) throw new IllegalArgumentException(s"Matrix sizes don't match: ${nrows} x ${ncols} + ${b.nrows} x ${b.ncols}")
    MatrixAddMatrix(this, b)
  }
  def - (b: MatrixExpression) = {
    if (nrows != b.nrows || ncols != b.ncols) throw new IllegalArgumentException(s"Matrix sizes don't match: ${nrows} x ${ncols} - ${b.nrows} x ${b.ncols}")
    MatrixSubMatrix(this, b)
  }
  /** Element-wise multiplication */
  def * (b: MatrixExpression) = {
    if (nrows != b.nrows || ncols != b.ncols) throw new IllegalArgumentException(s"Matrix sizes don't match: ${nrows} x ${ncols} * ${b.nrows} x ${b.ncols}")
    MatrixMulMatrix(this, b)
  }
  def / (b: MatrixExpression) = {
    if (nrows != b.nrows || ncols != b.ncols) throw new IllegalArgumentException(s"Matrix sizes don't match: ${nrows} x ${ncols} / ${b.nrows} x ${b.ncols}")
    MatrixDivMatrix(this, b)
  }

  /** Matrix transpose */
  def t = MatrixTranspose(this)

  /** A * x */
  def * (b: VectorExpression) = Ax(this, b)

  /** Matrix multiplication A * B */
  def %*% (b: MatrixExpression): MatrixExpression = {
    if (ncols != b.nrows) throw new IllegalArgumentException(s"Matrix sizes don't match for matrix multiplication: ${nrows} x ${ncols} %*% ${b.nrows} x ${b.ncols}")
    MatrixMultiplicationExpression(this, b)
  }

  def + (b: Double) = MatrixAddValue(this, b)
  def - (b: Double) = MatrixSubValue(this, b)
  def * (b: Double) = MatrixMulValue(this, b)
  def / (b: Double) = MatrixDivValue(this, b)
}

case class Ax(A: MatrixExpression, x: VectorExpression) extends VectorExpression {
  override def length: Int = A.nrows
  override def apply(i: Int): Double = toArray(i)
  override lazy val toArray: Array[Double] = {
    val y = new Array[Double](A.nrows)
    A.toMatrix.ax(x, y)
  }
}

case class MatrixLift(A: DenseMatrix) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j)
  override def toMatrix: DenseMatrix = A
}

case class MatrixTranspose(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.ncols
  override def ncols: Int = A.nrows
  override def apply(i: Int, j: Int): Double = A(j, i)
  override def toMatrix: DenseMatrix = A.toMatrix.transpose()
}

case class MatrixMultiplicationExpression(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = B.ncols
  override def apply(i: Int, j: Int): Double = toMatrix(i, j)
  override lazy val toMatrix: DenseMatrix = {
    (A, B) match {
      case (MatrixTranspose(A), MatrixTranspose(B)) => B.toMatrix.abmm(A.toMatrix).transpose()
      case (MatrixTranspose(A), _) => A.toMatrix.atbmm(B.toMatrix)
      case (_, MatrixTranspose(B)) => A.toMatrix.abtmm(B.toMatrix)
      case (_, _) => A.toMatrix.abmm(B.toMatrix)
    }
  }

  override def %*% (C: MatrixExpression) = MatrixMultiplicationChain(Seq(A, B, C))
}

case class MatrixMultiplicationChain(A: Seq[MatrixExpression]) extends MatrixExpression {
  override def nrows: Int = A.head.nrows
  override def ncols: Int = A.last.ncols
  override def apply(i: Int, j: Int): Double = toMatrix(i, j)
  override def %*% (B: MatrixExpression) = MatrixMultiplicationChain(A :+ B)

  override lazy val toMatrix: DenseMatrix = {
    val dims = (A.head.nrows +: A.map(_.ncols)).toArray
    val n = dims.length - 1
    val order = new MatrixOrderOptimization(dims)
    toMatrix(order.s, 0, n - 1)
  }

  private def toMatrix(s: Array[Array[Int]], i: Int, j: Int): DenseMatrix = {
    if (i == j) return A(i)

    val Ai = toMatrix(s, i, s(i)(j))
    val Aj = toMatrix(s, s(i)(j) + 1, j)
    Ai.abmm(Aj)
  }
}

case class MatrixAddValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) + y
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = A(i, j) + y
    z
  }
}
case class MatrixSubValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) - y
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = A(i, j) - y
    z
  }
}
case class MatrixMulValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) * y
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = A(i, j) * y
    z
  }
}
case class MatrixDivValue(A: MatrixExpression, y: Double) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) / y
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = A(i, j) / y
    z
  }
}

case class ValueAddMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = y + A(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = y + A(i, j)
    z
  }
}
case class ValueSubMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = y - A(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = y - A(i, j)
    z
  }
}
case class ValueMulMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = y * A(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = y * A(i, j)
    z
  }
}
case class ValueDivMatrix(y: Double, A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = y / A(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = y / A(i, j)
    z
  }
}

case class MatrixAddMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) + B(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = A(i, j) + B(i, j)
    z
  }
}
case class MatrixSubMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) - B(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = A(i, j) - B(i, j)
    z
  }
}
case class MatrixMulMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) * B(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = A(i, j) * B(i, j)
    z
  }
}
case class MatrixDivMatrix(A: MatrixExpression, B: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = A(i, j) / B(i, j)
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
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
class MatrixOrderOptimization(dims: Array[Int]) extends Logging {
  val n = dims.length - 1

  // m[i,j] = Minimum number of scalar multiplications (i.e., cost)
  // needed to compute the matrix A[i]A[i+1]...A[j] = A[i..j]
  // The cost is zero when multiplying one matrix
  val m = Array.ofDim[Int](n, n)
  // Index of the subsequence split that achieved minimal cost
  val s = Array.ofDim[Int](n, n)

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
    toString(sb, 0, n - 1, intermediate)
    sb.toString
  }

  private def toString(sb: StringBuilder, i: Int, j: Int, intermediate: Array[Boolean]): Unit = {
    if (i != j) {
      sb.append('(')
      toString(sb, i, s(i)(j), intermediate)
      if (!intermediate(i)) sb.append(dims(i)).append('x').append(dims(i+1))

      sb.append(" * ")

      toString(sb, s(i)(j) + 1, j, intermediate)
      if (!intermediate(j)) sb.append(dims(j)).append('x').append(dims(j+1))
      sb.append(')')

      intermediate(i) = true
      intermediate(j) = true
    }
  }
}

case class AbsMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.abs(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.abs(A(i, j))
    z
  }
}

case class AcosMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.acos(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.acos(A(i, j))
    z
  }
}

case class AsinMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.asin(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.asin(A(i, j))
    z
  }
}

case class AtanMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.atan(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.atan(A(i, j))
    z
  }
}

case class CbrtMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.cbrt(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.cbrt(A(i, j))
    z
  }
}

case class CeilMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.ceil(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.ceil(A(i, j))
    z
  }
}

case class ExpMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.exp(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.exp(A(i, j))
    z
  }
}

case class Expm1Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.expm1(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.expm1(A(i, j))
    z
  }
}

case class FloorMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.floor(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.floor(A(i, j))
    z
  }
}

case class LogMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.log(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.log(A(i, j))
    z
  }
}

case class Log2Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.log2(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.log2(A(i, j))
    z
  }
}

case class Log10Matrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.log10(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.log10(A(i, j))
    z
  }
}

case class Log1pMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.log1p(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.log1p(A(i, j))
    z
  }
}

case class RoundMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.abs(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.abs(A(i, j))
    z
  }
}

case class SinMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.sin(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.sin(A(i, j))
    z
  }
}

case class SqrtMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.sqrt(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.sqrt(A(i, j))
    z
  }
}

case class TanMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.tan(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.tan(A(i, j))
    z
  }
}

case class TanhMatrix(A: MatrixExpression) extends MatrixExpression {
  override def nrows: Int = A.nrows
  override def ncols: Int = A.ncols
  override def apply(i: Int, j: Int): Double = Math.tanh(A(i, j))
  override lazy val toMatrix: DenseMatrix = {
    val z = Matrix.zeros(A.nrows, A.ncols)
    for (j <- 0 until ncols)
      for (i <- 0 until nrows)
        z(i, j) = Math.tanh(A(i, j))
    z
  }
}

