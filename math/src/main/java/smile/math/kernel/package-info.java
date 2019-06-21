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
 * Mercer kernels. A Mercer kernel is a kernel that is positive
 * semi-definite. When a kernel is positive semi-definite, one may exploit
 * the kernel trick, the idea of implicitly mapping data to a high-dimensional
 * feature space where some linear algorithm is applied that works exclusively
 * with inner products. Assume we have some mapping &#934; from an input
 * space X to a feature space H, then a kernel k(u, v) = &lt;&#934;(u), &#934;(v)&gt;
 * may be used to define the inner product in feature space H.
 * <p>
 * Positive definiteness in the context of kernel functions also implies that
 * a kernel matrix created using a particular kernel is positive semi-definite.
 * A matrix is positive semi-definite if its associated eigenvalues are
 * nonnegative.
 *
 * @author Haifeng Li
 */
package smile.math.kernel;
