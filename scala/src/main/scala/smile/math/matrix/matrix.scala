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

package smile.math.matrix

/** Matrix companion object.
  *
  * @author Haifeng Li
  */
object matrix {
  /** Creates a matrix filled with given value. */
  def apply(nrow: Int, ncol: Int, value: Double = 0.0) = new Matrix(nrow, ncol, value)

  /** Creates a Matrix instance. */
  def apply(A: Array[Array[Double]]) = new Matrix(A)

  /** Creates a Matrix instance. */
  def apply(A: Array[Double]*) = new Matrix(A.toArray)

  /** Creates a single column matrix. */
  def apply(A: Array[Double]) = new Matrix(A)
}
