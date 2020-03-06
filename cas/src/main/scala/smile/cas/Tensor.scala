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

/** A tensor is an algebraic object that describes a (multilinear)
  * relationship between sets of algebraic objects related to a vector
  * space. Objects that tensors may map between include vectors (which
  * are often, but not always, understood as arrows with length that
  * point in a direction) and scalars (which are often familiar numbers
  * such as the real numbers), and, recursively, even other tensors.
  * Tensors are defined independent of any basis, although they are often
  * referred to by their components in a basis related to a particular
  * coordinate system.
  *
  * The shape of tensor (the number of dimensions and the size of each dimension)
  * might be only partially known.
  *
  * @author Haifeng Li
  */
trait Tensor {
  /** The rank of tensor, i.e. the number of dimensions. */
  def rank: Option[Int]

  /** The shape of tensor, i.e the size of each dimension. */
  def shape: Option[Array[IntScalar]]
}
