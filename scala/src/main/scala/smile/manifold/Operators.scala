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

package smile.manifold

import smile.util._

/** High level manifold learning operators.
  *
  * @author Haifeng Li
  */
trait Operators {
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
  def isomap(data: Array[Array[Double]], d: Int, k: Int, CIsomap: Boolean = true): IsoMap = {
    time {
      new IsoMap(data, d, k, CIsomap)
    }
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
  def lle(data: Array[Array[Double]], d: Int, k: Int): LLE = {
    time {
      new LLE(data, d, k)
    }
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
  def laplacian(data: Array[Array[Double]], d: Int, k: Int, t: Double = -1): LaplacianEigenmap = {
    time {
      new LaplacianEigenmap(data, d, k, t)
    }
  }
}