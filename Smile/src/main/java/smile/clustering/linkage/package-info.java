/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

/**
 * Cluster dissimilarity measures. An agglomerative hierarchical clustering
 * builds the hierarchy from the individual elements by progressively merging
 * clusters. The linkage criteria determines the distance between clusters
 * (i.e. sets of observations) based on as a pairwise distance function between
 * observations. Some commonly used linkage criteria are
 * <ul>
 * <li> Maximum or complete linkage clustering </li>
 * <li> Minimum or single-linkage clustering </li>
 * <li> Mean or average linkage clustering, or UPGMA </li>
 * <li> Unweighted Pair Group Method using Centroids, or UPCMA (also known as centroid linkage) </li>
 * <li> Weighted Pair Group Method with Arithmetic mean, or WPGMA. </li>
 * <li> Weighted Pair Group Method using Centroids, or WPGMC (also known as median linkage) </li>
 * <li> Ward's linkage</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
package smile.clustering.linkage;
