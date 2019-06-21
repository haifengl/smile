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
 * Clustering analysis. Clustering is the assignment of a set of observations
 * into subsets (called clusters) so that observations in the same cluster are
 * similar in some sense. Clustering is a method of unsupervised learning,
 * and a common technique for statistical data analysis used in many fields.
 * <p>
 * Hierarchical algorithms find successive clusters using previously
 * established clusters. These algorithms usually are either agglomerative
 * ("bottom-up") or divisive ("top-down"). Agglomerative algorithms begin
 * with each element as a separate cluster and merge them into successively
 * larger clusters. Divisive algorithms begin with the whole set and proceed
 * to divide it into successively smaller clusters.
 * <p>
 * Partitional algorithms typically determine all clusters at once, but can
 * also be used as divisive algorithms in the hierarchical clustering.
 * Many partitional clustering algorithms require the specification of
 * the number of clusters to produce in the input data set, prior to
 * execution of the algorithm. Barring knowledge of the proper value
 * beforehand, the appropriate value must be determined, a problem on
 * its own for which a number of techniques have been developed.
 * <p>
 * Density-based clustering algorithms are devised to discover
 * arbitrary-shaped clusters. In this approach, a cluster is regarded as
 * a region in which the density of data objects exceeds a threshold.
 * <p>
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
package smile.clustering;
