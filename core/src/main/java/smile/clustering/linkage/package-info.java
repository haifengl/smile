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
 * </ul>
 * 
 * @author Haifeng Li
 */
package smile.clustering.linkage;
