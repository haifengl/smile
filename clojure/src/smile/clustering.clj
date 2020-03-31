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

(ns smile.clustering
  "Clustering Analysis"
  {:author "Haifeng Li"}
  (:import [smile.clustering HierarchicalClustering PartitionClustering
                             KMeans XMeans GMeans SIB DeterministicAnnealing
                             CLARANS DBSCAN DENCLUE MEC SpectralClustering]
           [smile.clustering.linkage SingleLinkage CompleteLinkage
                                     UPGMALinkage UPGMCLinkage
                                     WPGMALinkage WPGMCLinkage WardLinkage]
           [smile.math.distance EuclideanDistance]))

(defn hclust 
  "Agglomerative hierarchical clustering."
  [data method]
  (let [linkage (case method
                      "single" (SingleLinkage/of data)
                      "complete" (CompleteLinkage/of data)
                      ("upgma" "average") (UPGMALinkage/of data)
                      ("upgmc" "centroid") (UPGMCLinkage/of data)
                      "wpgma" (WPGMALinkage/of data)
                      ("wpgmc" "median") (WPGMCLinkage/of data)
                      "ward" (WardLinkage/of data)
                      (throw (IllegalArgumentException. (str "Unknown agglomeration method: " method))))]
    (HierarchicalClustering/fit linkage)))

(defn kmeans
  "K-Means clustering."
  ([data k] (kmeans data k 100 1E-4 10))
  ([data k max-iter tol runs]
   (PartitionClustering/run runs
     (reify java.util.function.Supplier
       (get [this] (KMeans/fit data k max-iter tol))))))

(defn xmeans
  "X-Means clustering."
  [data k] (XMeans/fit data k))

(defn gmeans
  "G-Means clustering."
  [data k] (GMeans/fit data k))

(defn sib
  "Sequential Information Bottleneck algorithm."
  ([data k] (sib data k 100 8))
  ([data k max-iter runs]
   (PartitionClustering/run runs
     (reify java.util.function.Supplier
       (get [this] (SIB/fit data k max-iter))))))

(defn dac
  "Deterministic annealing clustering."
  ([data k] (dac data k 0.9 100 1E-4 0.01))
  ([data k alpha max-iter tol split-tol]
   (DeterministicAnnealing/fit data k alpha max-iter tol split-tol)))

(defn clarans
  "Clustering Large Applications based upon RANdomized Search."
  ([data distance k max-neighbor] (clarans data distance k max-neighbor 16))
  ([data distance k max-neighbor num-local]
   (PartitionClustering/run num-local
     (reify java.util.function.Supplier
       (get [this] (CLARANS/fit data distance k max-neighbor))))))

(defn dbscan
  "Density-Based Spatial Clustering of Applications with Noise."
  ([data min-pts radius] (dbscan data (EuclideanDistance.) min-pts radius))
  ([data distance min-pts radius] (DBSCAN/fit data distance min-pts radius)))

(defn denclue
  "DENsity CLUstering."
  [data sigma m] (DENCLUE/fit data sigma m))

(defn mec
  "Nonparametric minimum conditional entropy clustering."
  ([data k radius] (mec data (EuclideanDistance.) k radius))
  ([data distance k radius] (MEC/fit data distance k radius)))

(defn specc
  "Spectral clustering."
  ([data k sigma] (SpectralClustering/fit data k sigma))
  ([data k l sigma] (SpectralClustering/fit data k l sigma)))

