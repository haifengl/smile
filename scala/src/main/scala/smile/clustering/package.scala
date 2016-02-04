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

/** Clustering analysis. Clustering is the assignment of a set of observations
  * into subsets (called clusters) so that observations in the same cluster are
  * similar in some sense. Clustering is a method of unsupervised learning,
  * and a common technique for statistical data analysis used in many fields.
  *
  * Hierarchical algorithms find successive clusters using previously
  * established clusters. These algorithms usually are either agglomerative
  * ("bottom-up") or divisive ("top-down"). Agglomerative algorithms begin
  * with each element as a separate cluster and merge them into successively
  * larger clusters. Divisive algorithms begin with the whole set and proceed
  * to divide it into successively smaller clusters.
  *
  * Partitional algorithms typically determine all clusters at once, but can
  * also be used as divisive algorithms in the hierarchical clustering.
  * Many partitional clustering algorithms require the specification of
  * the number of clusters to produce in the input data set, prior to
  * execution of the algorithm. Barring knowledge of the proper value
  * beforehand, the appropriate value must be determined, a problem on
  * its own for which a number of techniques have been developed.
  *
  * Density-based clustering algorithms are devised to discover
  * arbitrary-shaped clusters. In this approach, a cluster is regarded as
  * a region in which the density of data objects exceeds a threshold.
  *
  * Subspace clustering methods look for clusters that can only be seen in
  * a particular projection (subspace, manifold) of the data. These methods
  * thus can ignore irrelevant attributes. The general problem is also known
  * as Correlation clustering while the special case of axis-parallel subspaces
  * is also known as two-way clustering, co-clustering or biclustering in
  * bioinformatics: in these methods not only the objects are clustered but
  * also the features of the objects, i.e., if the data is represented in
  * a data matrix, the rows and columns are clustered simultaneously. They
  * usually do not however work with arbitrary feature combinations as in general
  * subspace methods.
  *
  * @author Haifeng Li
  */
package object clustering extends Operators {

}
