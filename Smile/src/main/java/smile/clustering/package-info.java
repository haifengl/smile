/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
