;   Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
;
;   Smile is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Lesser General Public License as
;   published by the Free Software Foundation, either version 3 of
;   the License, or (at your option) any later version.
;
;   Smile is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;   GNU Lesser General Public License for more details.
;
;   You should have received a copy of the GNU Lesser General Public License
;   along with Smile.  If not, see <https://www.gnu.org/licenses/>.

(ns smile.mds
  "Multidimensional Scaling"
  {:author "Haifeng Li"}
  (:import [smile.mds MDS IsotonicMDS SammonMapping]))

(defn mds
  "Classical multidimensional scaling, also known as principal coordinates
   analysis. Given a matrix of dissimilarities (e.g. pairwise distances), MDS
   finds a set of points in low dimensional space that well-approximates the
   dissimilarities in A. We are not restricted to using a Euclidean
   distance metric. However, when Euclidean distances are used MDS is
   equivalent to PCA."
  ([proximity k] (mds proximity k false))
  ([proximity k positive] (MDS/of proximity k positive)))

(defn isomds
  "Kruskal's nonmetric MDS. In non-metric MDS, only the rank order of entries
   in the proximity matrix (not the actual dissimilarities) is assumed to
   contain the significant information. Hence, the distances of the final
   configuration should as far as possible be in the same rank order as the
   original data. Note that a perfect ordinal re-scaling of the data into
   distances is usually not possible. The relationship is typically found
   using isotonic regression."
  ([proximity k] (isomds proximity k 0.0001 200))
  ([proximity k tol max-iter] (IsotonicMDS/of proximity k tol max-iter)))

(defn sammon
  "The Sammon's mapping is an iterative technique for making interpoint
   distances in the low-dimensional projection as close as possible to the
   interpoint distances in the high-dimensional object. Two points close
   together in the high-dimensional space should appear close together in the
   projection, while two points far apart in the high dimensional space should
   appear far apart in the projection. The Sammon's mapping is a special case of
   metric least-square multidimensional scaling."
   ([proximity k] (sammon proximity k 0.2, 0.0001 0.001 100))
   ([proximity k lambda tol step-tol max-iter] (SammonMapping/of proximity k lambda tol step-tol max-iter)))
 