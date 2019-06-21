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

/**
 * Radial basis functions. A radial basis function is a real-valued function
 * whose value depends only on the distance from the origin, so that
 * &phi;(x)=&phi;(||x||); or alternatively on the distance from some other
 * point c, called a center, so that &phi;(x,c)=&phi;(||x-c||). Any function
 * &phi; that satisfies the property is a radial function. The norm is usually
 * Euclidean distance, although other distance functions are also possible.
 * For example by using probability metric it is for some radial functions
 * possible to avoid problems with ill conditioning of the matrix solved to
 * determine coefficients w<sub>i</sub> (see below), since the ||x|| is always
 * greater than zero.
 * <p>
 * Sums of radial basis functions are typically used to approximate given
 * functions:
 * <p>
 * y(x) = &Sigma; w<sub>i</sub> &phi;(||x-c<sub>i</sub>||)
 * <p>
 * where the approximating function y(x) is represented as a sum of N radial
 * basis functions, each associated with a different center c<sub>i</sub>, and weighted
 * by an appropriate coefficient w<sub>i</sub>. The weights w<sub>i</sub> can
 * be estimated using the matrix methods of linear least squares, because
 * the approximating function is linear in the weights.
 * <p>
 * This approximation process can also be interpreted as a simple kind of neural
 * network and has been particularly used in time series prediction and control
 * of nonlinear systems exhibiting sufficiently simple chaotic behavior,
 * 3D reconstruction in computer graphics (for example, hierarchical RBF).
 *
 * @author Haifeng Li
 */
package smile.math.rbf;
