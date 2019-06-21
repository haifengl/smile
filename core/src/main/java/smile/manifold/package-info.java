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
 * Manifold learning finds a low-dimensional basis for describing
 * high-dimensional data. Manifold learning is a popular approach to nonlinear
 * dimensionality reduction. Algorithms for this task are based on the idea
 * that the dimensionality of many data sets is only artificially high; though
 * each data point consists of perhaps thousands of features, it may be
 * described as a function of only a few underlying parameters. That is, the
 * data points are actually samples from a low-dimensional manifold that is
 * embedded in a high-dimensional space. Manifold learning algorithms attempt
 * to uncover these parameters in order to find a low-dimensional representation
 * of the data.
 * <p>
 * Some prominent approaches are locally linear embedding
 * (LLE), Hessian LLE, Laplacian eigenmaps, and LTSA. These techniques
 * construct a low-dimensional data representation using a cost function
 * that retains local properties of the data, and can be viewed as defining
 * a graph-based kernel for Kernel PCA. More recently, techniques have been
 * proposed that, instead of defining a fixed kernel, try to learn the kernel
 * using semidefinite programming. The most prominent example of such a
 * technique is maximum variance unfolding (MVU). The central idea of MVU
 * is to exactly preserve all pairwise distances between nearest neighbors
 * (in the inner product space), while maximizing the distances between points
 * that are not nearest neighbors.
 * <p>
 * An alternative approach to neighborhood preservation is through the
 * minimization of a cost function that measures differences between
 * distances in the input and output spaces. Important examples of such
 * techniques include classical multidimensional scaling (which is identical
 * to PCA), Isomap (which uses geodesic distances in the data space), diffusion
 * maps (which uses diffusion distances in the data space), t-SNE (which
 * minimizes the divergence between distributions over pairs of points),
 * and curvilinear component analysis.
 * 
 * @author Haifeng Li
 */
package smile.manifold;