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

package smile

import smile.util.time

/** Manifold learning finds a low-dimensional basis for describing
  * high-dimensional data. Manifold learning is a popular approach to nonlinear
  * dimensionality reduction. Algorithms for this task are based on the idea
  * that the dimensionality of many data sets is only artificially high; though
  * each data point consists of perhaps thousands of features, it may be
  * described as a function of only a few underlying parameters. That is, the
  * data points are actually samples from a low-dimensional manifold that is
  * embedded in a high-dimensional space. Manifold learning algorithms attempt
  * to uncover these parameters in order to find a low-dimensional representation
  * of the data.
  *
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
  * @author Haifeng Li
  */
package object manifold {
  /** Isometric feature mapping. Isomap is a widely used low-dimensional embedding methods,
    * where geodesic distances on a weighted graph are incorporated with the
    * classical multidimensional scaling. Isomap is used for computing a
    * quasi-isometric, low-dimensional embedding of a set of high-dimensional
    * data points. Isomap is highly efficient and generally applicable to a broad
    * range of data sources and dimensionalities.
    *
    * To be specific, the classical MDS performs low-dimensional embedding based
    * on the pairwise distance between data points, which is generally measured
    * using straight-line Euclidean distance. Isomap is distinguished by
    * its use of the geodesic distance induced by a neighborhood graph
    * embedded in the classical scaling. This is done to incorporate manifold
    * structure in the resulting embedding. Isomap defines the geodesic distance
    * to be the sum of edge weights along the shortest path between two nodes.
    * The top n eigenvectors of the geodesic distance matrix, represent the
    * coordinates in the new n-dimensional Euclidean space.
    *
    * The connectivity of each data point in the neighborhood graph is defined
    * as its nearest k Euclidean neighbors in the high-dimensional space. This
    * step is vulnerable to "short-circuit errors" if k is too large with
    * respect to the manifold structure or if noise in the data moves the
    * points slightly off the manifold. Even a single short-circuit error
    * can alter many entries in the geodesic distance matrix, which in turn
    * can lead to a drastically different (and incorrect) low-dimensional
    * embedding. Conversely, if k is too small, the neighborhood graph may
    * become too sparse to approximate geodesic paths accurately.
    *
    * This class implements C-Isomap that involves magnifying the regions
    * of high density and shrink the regions of low density of data points
    * in the manifold. Edge weights that are maximized in Multi-Dimensional
    * Scaling(MDS) are modified, with everything else remaining unaffected.
    *
    * ====References:====
    *  - J. B. Tenenbaum, V. de Silva and J. C. Langford  A Global Geometric Framework for Nonlinear Dimensionality Reduction. Science 290(5500):2319-2323, 2000.
    *
    * @param data the data set.
    * @param d the dimension of the manifold.
    * @param k k-nearest neighbor.
    * @param CIsomap C-Isomap algorithm if true, otherwise standard algorithm.
    */
  def isomap(data: Array[Array[Double]], k: Int, d: Int = 2, CIsomap: Boolean = true): IsoMap = time("IsoMap") {
    IsoMap.of(data, k, d, CIsomap)
  }

  /** Locally Linear Embedding. It has several advantages over Isomap, including
    * faster optimization when implemented to take advantage of sparse matrix
    * algorithms, and better results with many problems. LLE also begins by
    * finding a set of the nearest neighbors of each point. It then computes
    * a set of weights for each point that best describe the point as a linear
    * combination of its neighbors. Finally, it uses an eigenvector-based
    * optimization technique to find the low-dimensional embedding of points,
    * such that each point is still described with the same linear combination
    * of its neighbors. LLE tends to handle non-uniform sample densities poorly
    * because there is no fixed unit to prevent the weights from drifting as
    * various regions differ in sample densities.
    *
    * ====References:====
    *  - Sam T. Roweis and Lawrence K. Saul. Nonlinear Dimensionality Reduction by Locally Linear Embedding. Science 290(5500):2323-2326, 2000.
    *
    * @param data the data set.
    * @param d the dimension of the manifold.
    * @param k k-nearest neighbor.
    */
  def lle(data: Array[Array[Double]], k: Int, d: Int = 2): LLE = time("LLE") {
    LLE.of(data, k, d)
  }

  /** Laplacian Eigenmap. Using the notion of the Laplacian of the nearest
    * neighbor adjacency graph, Laplacian Eigenmap compute a low dimensional
    * representation of the dataset that optimally preserves local neighborhood
    * information in a certain sense. The representation map generated by the
    * algorithm may be viewed as a discrete approximation to a continuous map
    * that naturally arises from the geometry of the manifold.
    *
    * The locality preserving character of the Laplacian Eigenmap algorithm makes
    * it relatively insensitive to outliers and noise. It is also not prone to
    * "short circuiting" as only the local distances are used.
    *
    * ====References:====
    *  - Mikhail Belkin and Partha Niyogi. Laplacian Eigenmaps and Spectral Techniques for Embedding and Clustering. NIPS, 2001.
    *
    * @param data the data set.
    * @param d the dimension of the manifold.
    * @param k k-nearest neighbor.
    * @param t the smooth/width parameter of heat kernel e<sup>-||x-y||<sup>2</sup> / t</sup>.
    *          Non-positive value means discrete weights.
    */
  def laplacian(data: Array[Array[Double]], k: Int, d: Int = 2, t: Double = -1): LaplacianEigenmap = time("Laplacian Eigen Map") {
    LaplacianEigenmap.of(data, k, d, t)
  }

  /** t-distributed stochastic neighbor embedding. t-SNE is a nonlinear
    * dimensionality reduction technique that is particularly well suited
    * for embedding high-dimensional data into a space of two or three
    * dimensions, which can then be visualized in a scatter plot. Specifically,
    * it models each high-dimensional object by a two- or three-dimensional
    * point in such a way that similar objects are modeled by nearby points
    * and dissimilar objects are modeled by distant points.
    *
    * ====References:====
    *  - L.J.P. van der Maaten. Accelerating t-SNE using Tree-Based Algorithms. Journal of Machine Learning Research 15(Oct):3221-3245, 2014.
    *  - L.J.P. van der Maaten and G.E. Hinton. Visualizing Non-Metric Similarities in Multiple Maps. Machine Learning 87(1):33-55, 2012.
    *  - L.J.P. van der Maaten. Learning a Parametric Embedding by Preserving Local Structure. In Proceedings of the Twelfth International Conference on Artificial Intelligence & Statistics (AI-STATS), JMLR W&CP 5:384-391, 2009.
    *  - L.J.P. van der Maaten and G.E. Hinton. Visualizing High-Dimensional Data Using t-SNE. Journal of Machine Learning Research 9(Nov):2579-2605, 2008.
    *
    * @param X input data. If X is a square matrix, it is assumed to be the squared distance/dissimilarity matrix.
    * @param d the dimension of the manifold.
    * @param perplexity the perplexity of the conditional distribution.
    * @param eta        the learning rate.
    * @param iterations the number of iterations.
    */
  def tsne(X: Array[Array[Double]], d: Int = 2, perplexity: Double = 20.0, eta: Double = 200.0, iterations: Int = 1000): TSNE = time("t-SNE") {
    new TSNE(X, d, perplexity, eta, iterations)
  }

  /**
    * Uniform Manifold Approximation and Projection.
    *
    * UMAP is a dimension reduction technique that can be used for visualization
    * similarly to t-SNE, but also for general non-linear dimension reduction.
    * The algorithm is founded on three assumptions about the data:
    *
    *  - The data is uniformly distributed on a Riemannian manifold;
    *  - The Riemannian metric is locally constant (or can be approximated as such);
    *  - The manifold is locally connected.
    *
    * From these assumptions it is possible to model the manifold with a fuzzy
    * topological structure. The embedding is found by searching for a low
    * dimensional projection of the data that has the closest possible equivalent
    * fuzzy topological structure.
    *
    * @param data               the input data.
    * @param k                  k-nearest neighbors. Larger values result in more global views
    *                           of the manifold, while smaller values result in more local data
    *                           being preserved. Generally in the range 2 to 100.
    * @param d                  The target embedding dimensions. defaults to 2 to provide easy
    *                           visualization, but can reasonably be set to any integer value
    *                           in the range 2 to 100.
    * @param iterations         The number of iterations to optimize the
    *                           low-dimensional representation. Larger values result in more
    *                           accurate embedding. Muse be greater than 10. Choose wise value
    *                           based on the size of the input data, e.g, 200 for large
    *                           data (1000+ samples), 500 for small.
    * @param learningRate       The initial learning rate for the embedding optimization,
    *                           default 1.
    * @param minDist            The desired separation between close points in the embedding
    *                           space. Smaller values will result in a more clustered/clumped
    *                           embedding where nearby points on the manifold are drawn closer
    *                           together, while larger values will result on a more even
    *                           disperse of points. The value should be set no-greater than
    *                           and relative to the spread value, which determines the scale
    *                           at which embedded points will be spread out. default 0.1.
    * @param spread             The effective scale of embedded points. In combination with
    *                           minDist, this determines how clustered/clumped the embedded
    *                           points are. default 1.0.
    * @param negativeSamples    The number of negative samples to select per positive sample
    *                           in the optimization process. Increasing this value will result
    *                           in greater repulsive force being applied, greater optimization
    *                           cost, but slightly more accuracy, default 5.
    * @param repulsionStrength  Weighting applied to negative samples in low dimensional
    *                           embedding optimization. Values higher than one will result in
    *                           greater weight being given to negative samples, default 1.0.
    */
  def umap(data: Array[Array[Double]], k: Int = 15, d: Int = 2, iterations: Int = 0, learningRate: Double = 1.0, minDist: Double = 0.1, spread: Double = 1.0, negativeSamples: Int = 5, repulsionStrength: Double = 1.0): UMAP = time("UMAP") {
    return UMAP.of(data, k, d,
      if (iterations >= 10) iterations else if (data.length > 10000) 200 else 500,
      learningRate, minDist, spread, negativeSamples, repulsionStrength)
  }

  /** Classical multidimensional scaling, also known as principal coordinates
    * analysis. Given a matrix of dissimilarities (e.g. pairwise distances), MDS
    * finds a set of points in low dimensional space that well-approximates the
    * dissimilarities in A. We are not restricted to using a Euclidean
    * distance metric. However, when Euclidean distances are used MDS is
    * equivalent to PCA.
    *
    * @param proximity the nonnegative proximity matrix of dissimilarities. The
    *                  diagonal should be zero and all other elements should be positive and
    *                  symmetric. For pairwise distances matrix, it should be just the plain
    *                  distance, not squared.
    * @param k the dimension of the projection.
    * @param positive if true, estimate an appropriate constant to be added
    *            to all the dissimilarities, apart from the self-dissimilarities, that
    *            makes the learning matrix positive semi-definite. The other formulation of
    *            the additive constant problem is as follows. If the proximity is
    *            measured in an interval scale, where there is no natural origin, then there
    *            is not a sympathy of the dissimilarities to the distances in the Euclidean
    *            space used to represent the objects. In this case, we can estimate a constant c
    *            such that proximity + c may be taken as ratio data, and also possibly
    *            to minimize the dimensionality of the Euclidean space required for
    *            representing the objects.
    */
  def mds(proximity: Array[Array[Double]], k: Int, positive: Boolean = false): MDS = time("MDS") {
    MDS.of(proximity, k, positive)
  }

  /** Kruskal's nonmetric MDS. In non-metric MDS, only the rank order of entries
    * in the proximity matrix (not the actual dissimilarities) is assumed to
    * contain the significant information. Hence, the distances of the final
    * configuration should as far as possible be in the same rank order as the
    * original data. Note that a perfect ordinal re-scaling of the data into
    * distances is usually not possible. The relationship is typically found
    * using isotonic regression.
    *
    * @param proximity the nonnegative proximity matrix of dissimilarities. The
    *                  diagonal should be zero and all other elements should be positive and symmetric.
    * @param k the dimension of the projection.
    * @param tol tolerance for stopping iterations.
    * @param maxIter maximum number of iterations.
    */
  def isomds(proximity: Array[Array[Double]], k: Int, tol: Double = 0.0001, maxIter: Int = 200): IsotonicMDS = time("Kruskal's nonmetric MDS") {
    IsotonicMDS.of(proximity, k, tol, maxIter)
  }

  /** The Sammon's mapping is an iterative technique for making interpoint
    * distances in the low-dimensional projection as close as possible to the
    * interpoint distances in the high-dimensional object. Two points close
    * together in the high-dimensional space should appear close together in the
    * projection, while two points far apart in the high dimensional space should
    * appear far apart in the projection. The Sammon's mapping is a special case of
    * metric least-square multidimensional scaling.
    *
    * Ideally when we project from a high dimensional space to a low dimensional
    * space the image would be geometrically congruent to the original figure.
    * This is called an isometric projection. Unfortunately it is rarely possible
    * to isometrically project objects down into lower dimensional spaces. Instead of
    * trying to achieve equality between corresponding inter-point distances we
    * can minimize the difference between corresponding inter-point distances.
    * This is one goal of the Sammon's mapping algorithm. A second goal of the Sammon's
    * mapping algorithm is to preserve the topology as best as possible by giving
    * greater emphasize to smaller interpoint distances. The Sammon's mapping
    * algorithm has the advantage that whenever it is possible to isometrically
    * project an object into a lower dimensional space it will be isometrically
    * projected into the lower dimensional space. But whenever an object cannot
    * be projected down isometrically the Sammon's mapping projects it down to reduce
    * the distortion in interpoint distances and to limit the change in the
    * topology of the object.
    *
    * The projection cannot be solved in a closed form and may be found by an
    * iterative algorithm such as gradient descent suggested by Sammon. Kohonen
    * also provides a heuristic that is simple and works reasonably well.
    *
    * @param proximity the nonnegative proximity matrix of dissimilarities. The
    *                  diagonal should be zero and all other elements should be positive and symmetric.
    * @param k         the dimension of the projection.
    * @param lambda    initial value of the step size constant in diagonal Newton method.
    * @param tol       tolerance for stopping iterations.
    * @param stepTol   tolerance on step size.
    * @param maxIter   maximum number of iterations.
    */
  def sammon(proximity: Array[Array[Double]], k: Int, lambda: Double = 0.2, tol: Double = 0.0001, stepTol: Double = 0.001, maxIter: Int = 100): SammonMapping = time("Sammon's Mapping") {
    SammonMapping.of(proximity, k, lambda, tol, stepTol, maxIter)
  }

  /** Hacking scaladoc [[https://github.com/scala/bug/issues/8124 issue-8124]].
    * The user should ignore this object. */
  object $dummy
}