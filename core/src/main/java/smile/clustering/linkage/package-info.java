/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
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
