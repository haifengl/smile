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

package smile

/** Originally used for data compression, Vector quantization (VQ)
  * allows the modeling of probability density functions by
  * the distribution of prototype vectors. It works by dividing a large set of points
  * (vectors) into groups having approximately the same number of
  * points closest to them. Each group is represented by its centroid
  * point, as in K-Means and some other clustering algorithms.
  *
  * Vector quantization is is based on the competitive learning paradigm,
  * and also closely related to sparse coding models
  * used in deep learning algorithms such as autoencoder.
  *
  * Algorithms in this package also support the <code>partition</code>
  * method for clustering purpose.
  *
  * @author Haifeng Li
  */
package object vq extends Operators {

}
