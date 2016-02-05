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

package smile

/** Feature extraction. Feature extraction transforms the data in the
  * high-dimensional space to a space of fewer dimensions. The data
  * transformation may be linear, as in principal component analysis (PCA),
  * but many nonlinear dimensionality reduction techniques also exist.
  *
  * The main linear technique for dimensionality reduction, principal component
  * analysis, performs a linear mapping of the data to a lower dimensional
  * space in such a way that the variance of the data in the low-dimensional
  * representation is maximized. In practice, the correlation matrix of the
  * data is constructed and the eigenvectors on this matrix are computed.
  * The eigenvectors that correspond to the largest eigenvalues (the principal
  * components) can now be used to reconstruct a large fraction of the variance
  * of the original data. Moreover, the first few eigenvectors can often be
  * interpreted in terms of the large-scale physical behavior of the system.
  * The original space has been reduced (with data loss, but hopefully
  * retaining the most important variance) to the space spanned by a few
  * eigenvectors.
  *
  * Compared to regular batch PCA algorithm, the generalized Hebbian algorithm
  * is an adaptive method to find the largest k eigenvectors of the covariance
  * matrix, assuming that the associated eigenvalues are distinct. GHA works
  * with an arbitrarily large sample size and the storage requirement is modest.
  * Another attractive feature is that, in a nonstationary environment, it
  * has an inherent ability to track gradual changes in the optimal solution
  * in an inexpensive way.
  *
  * Random projection is a promising linear dimensionality reduction technique
  * for learning mixtures of Gaussians. The key idea of random projection arises
  * from the Johnson-Lindenstrauss lemma: if points in a vector space are
  * projected onto a randomly selected subspace of suitably high dimension,
  * then the distances between the points are approximately preserved.
  *
  * Principal component analysis can be employed in a nonlinear way by means
  * of the kernel trick. The resulting technique is capable of constructing
  * nonlinear mappings that maximize the variance in the data. The resulting
  * technique is entitled Kernel PCA. Other prominent nonlinear techniques
  * include manifold learning techniques such as locally linear embedding
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
  *
  * An alternative approach to neighborhood preservation is through the
  * minimization of a cost function that measures differences between
  * distances in the input and output spaces. Important examples of such
  * techniques include classical multidimensional scaling (which is identical
  * to PCA), Isomap (which uses geodesic distances in the data space), diffusion
  * maps (which uses diffusion distances in the data space), t-SNE (which
  * minimizes the divergence between distributions over pairs of points),
  * and curvilinear component analysis.
  *
  * A different approach to nonlinear dimensionality reduction is through the
  * use of autoencoders, a special kind of feed-forward neural networks with
  * a bottle-neck hidden layer. The training of deep encoders is typically
  * performed using a greedy layer-wise pre-training (e.g., using a stack of
  * Restricted Boltzmann machines) that is followed by a finetuning stage based
  * on backpropagation.
  *
  * @author Haifeng Li
  */
package object projection extends Operators {

}