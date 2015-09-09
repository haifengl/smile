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
 * Nearest neighbor search. Nearest neighbor search is an optimization problem
 * for finding closest points in metric spaces. The problem is: given a set S
 * of points in a metric space M and a query point q &isin; M, find the closest
 * point in S to q.
 * <p>
 * The simplest solution to the NNS problem is to compute the distance from
 * the query point to every other point in the database, keeping track of
 * the "best so far". This algorithm, sometimes referred to as the naive
 * approach, has a running time of O(Nd) where N is the cardinality of S
 * and d is the dimensionality of M. There are no search data structures
 * to maintain, so linear search has no space complexity beyond the storage
 * of the database. Surprisingly, naive search outperforms space partitioning
 * approaches on higher dimensional spaces.
 * <p>
 * Space partitioning, a branch and bound methodology, has been applied to the
 * problem. The simplest is the k-d tree, which iteratively bisects the search
 * space into two regions containing half of the points of the parent region.
 * Queries are performed via traversal of the tree from the root to a leaf by
 * evaluating the query point at each split. Depending on the distance
 * specified in the query, neighboring branches that might contain hits
 * may also need to be evaluated. For constant dimension query time,
 * average complexity is O(log N) in the case of randomly distributed points.
 * Alternatively the R-tree data structure was designed to support nearest
 * neighbor search in dynamic context, as it has efficient algorithms for
 * insertions and deletions.
 * <p>
 * In case of general metric space branch and bound approach is known under
 * the name of metric trees. Particular examples include vp-tree and BK-tree.
 * <p>
 * Locality sensitive hashing (LSH) is a technique for grouping points in space
 * into 'buckets' based on some distance metric operating on the points.
 * Points that are close to each other under the chosen metric are mapped
 * to the same bucket with high probability.
 * <p>
 * The cover tree has a theoretical bound that is based on the dataset's
 * doubling constant. The bound on search time is O(c12 log n) where c is
 * the expansion constant of the dataset.
 * 
 * @author Haifeng Li
 */
package smile.neighbor;