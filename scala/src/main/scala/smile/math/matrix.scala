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

package smile.math.matrix

/** Matrix companion object.
  *
  * @author Haifeng Li
  */
object matrix {
  /** Creates a matrix filled with given value. */
  def apply(nrows: Int, ncols: Int, value: Double) = Matrix.newInstance(nrows, ncols, value)

  /** Creates a Matrix instance. */
  def apply(A: Array[Array[Double]]) = Matrix.newInstance(A)

  /** Creates a Matrix instance. */
  def apply(A: Array[Double]*) = Matrix.newInstance(A.toArray)


  /** Creates a single column matrix. */
  def apply(A: Array[Double]) = Matrix.newInstance(A)
}
