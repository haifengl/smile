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
  "Agglomerative hierarchical clustering.

  This method seeks to build a hierarchy of clusters in a bottom up approach:
  each observation starts in its own cluster, and pairs of clusters are merged
  as one moves up the hierarchy. The results of hierarchical clustering are
  usually presented in a dendrogram.

  In general, the merges are determined in a greedy manner. In order to decide
  which clusters should be combined, a measure of dissimilarity between sets
  of observations is required. In most methods of hierarchical clustering,
  this is achieved by use of an appropriate metric, and a linkage criteria
  which specifies the dissimilarity of sets as a function of the pairwise
  distances of observations in the sets.

  Hierarchical clustering has the distinct advantage that any valid measure
  of distance can be used. In fact, the observations themselves are not
  required: all that is used is a matrix of distances.

  `data` is the data set.
  `distance` is the distance/dissimilarity measure.
  `method` is the agglomeration method to merge clusters. This should be one
  of 'single', 'complete', 'upgma', 'upgmc', 'wpgma', 'wpgmc', and 'ward'."
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
  "K-Means clustering.

  K-Means clustering. The algorithm partitions n observations into k clusters
  in which each observation belongs to the cluster with the nearest mean.
  Although finding an exact solution to the k-means problem for arbitrary
  input is NP-hard, the standard approach to finding an approximate solution
  (often called Lloyd's algorithm or the k-means algorithm) is used widely
  and frequently finds reasonable solutions quickly.

  However, the k-means algorithm has at least two major theoretic shortcomings:

   - First, it has been shown that the worst case running time of the
  algorithm is super-polynomial in the input size.
   - Second, the approximation found can be arbitrarily bad with respect
  to the objective function compared to the optimal learn.

  In this implementation, we use k-means++ which addresses the second of these
  obstacles by specifying a procedure to initialize the cluster centers before
  proceeding with the standard k-means optimization iterations. With the
  k-means++ initialization, the algorithm is guaranteed to find a solution
  that is O(log k) competitive to the optimal k-means solution.

  We also use k-d trees to speed up each k-means step as described in the
  filter algorithm by Kanungo, et al.

  K-means is a hard clustering method, i.e. each sample is assigned to
  a specific cluster. In contrast, soft clustering, e.g. the
  Expectation-Maximization algorithm for Gaussian mixtures, assign samples
  to different clusters with different probabilities.

  This method runs the algorithm for given times and return the best one with
  smallest distortion.

  `data` is the data set.
  `k` is the number of clusters.
  `max-iter` is the maximum number of iterations for each running.
  `tol` is the tolerance of convergence test.
  `runs` is the number of runs of K-Means algorithm."
  ([data k] (kmeans data k 100 1E-4 10))
  ([data k max-iter tol runs]
   (PartitionClustering/run runs
     (reify java.util.function.Supplier
       (get [this] (KMeans/fit data k max-iter tol))))))

(defn xmeans
  "X-Means clustering.

  X-Means clustering algorithm is an extended K-Means which tries to
  automatically determine the number of clusters based on BIC scores.
  Starting with only one cluster, the X-Means algorithm goes into action
  after each run of K-Means, making local decisions about which subset of the
  current centroids should split themselves in order to better fit the data.
  The splitting decision is done by computing the Bayesian Information
  Criterion (BIC).

  `data` is the data set.
  `k` is the maximum number of clusters."
  [data k] (XMeans/fit data k))

(defn gmeans
  "G-Means clustering.

  G-Means clustering algorithm is an extended K-Means which tries to
  automatically determine the number of clusters by normality test.
  The G-means algorithm is based on a statistical test for the hypothesis
  that a subset of data follows a Gaussian distribution. G-means runs
  k-means with increasing k in a hierarchical fashion until the test accepts
  the hypothesis that the data assigned to each k-means center are Gaussian.

  `data` is the data set.
  `k` is the maximum number of clusters."
  [data k] (GMeans/fit data k))

(defn sib
  "Sequential Information Bottleneck algorithm.

  SIB clusters co-occurrence data such as text documents vs words.
  SIB is guaranteed to converge to a local maximum of the information.
  Moreover, the time and space complexity are significantly improved
  in contrast to the agglomerative IB algorithm.

  In analogy to K-Means, SIB's update formulas are essentially same as the
  EM algorithm for estimating finite Gaussian mixture model by replacing
  regular Euclidean distance with Kullback-Leibler divergence, which is
  clearly a better dissimilarity measure for co-occurrence data. However,
  the common batch updating rule (assigning all instances to nearest centroids
  and then updating centroids) of K-Means won't work in SIB, which has
  to work in a sequential way (reassigning (if better) each instance then
  immediately update related centroids). It might be because K-L divergence
  is very sensitive and the centroids may be significantly changed in each
  iteration in batch updating rule.

  Note that this implementation has a little difference from the original
  paper, in which a weighted Jensen-Shannon divergence is employed as a
  criterion to assign a randomly-picked sample to a different cluster.
  However, this doesn't work well in some cases as we experienced probably
  because the weighted JS divergence gives too much weight to clusters which
  is much larger than a single sample. In this implementation, we instead
  use the regular/unweighted Jensen-Shannon divergence.

  `data` is the data set.
  `k` is the number of clusters.
  `max-iter` is the maximum number of iterations.
  `runs` is the number of runs of SIB algorithm."
  ([data k] (sib data k 100 8))
  ([data k max-iter runs]
   (PartitionClustering/run runs
     (reify java.util.function.Supplier
       (get [this] (SIB/fit data k max-iter))))))

(defn dac
  "Deterministic annealing clustering.

  Deterministic annealing extends soft-clustering to an annealing process.
  For each temperature value, the algorithm iterates between the calculation
  of all posteriori probabilities and the update of the centroids vectors,
  until convergence is reached. The annealing starts with a high temperature.
  Here, all centroids vectors converge to the center of the pattern
  distribution (independent of their initial positions). Below a critical
  temperature the vectors start to split. Further decreasing the temperature
  leads to more splittings until all centroids vectors are separate. The
  annealing can therefore avoid (if it is sufficiently slow) the convergence
  to local minima.

  `data` is the data set.
  `k` is the maximum number of clusters.
  `alpha` is the temperature T is decreasing as T = T * alpha. alpha has
  to be in (0, 1).
  `tol` is the tolerance of convergence test.
  `split-tol` is the tolerance to split a cluster."
  ([data k] (dac data k 0.9 100 1E-4 0.01))
  ([data k alpha max-iter tol split-tol]
   (DeterministicAnnealing/fit data k alpha max-iter tol split-tol)))

(defn clarans
  "Clustering Large Applications based upon RANdomized Search.

  CLARANS is an efficient medoid-based clustering algorithm. The k-medoids
  algorithm is an adaptation of the k-means algorithm. Rather than calculate
  the mean of the items in each cluster, a representative item, or medoid, is
  chosen for each cluster at each iteration. In CLARANS, the process of
  finding k medoids from n objects is viewed abstractly as searching through
  a certain graph. In the graph, a node is represented by a set of k objects
  as selected medoids. Two nodes are neighbors if their sets differ by only
  one object. In each iteration, CLARANS considers a set of randomly chosen
  neighbor nodes as candidate of new medoids. We will move to the neighbor
  node if the neighbor is a better choice for medoids. Otherwise, a local
  optima is discovered. The entire process is repeated multiple time to find
  better.

  CLARANS has two parameters: the maximum number of neighbors examined
  (maxNeighbor) and the number of local minima obtained (numLocal). The
  higher the value of maxNeighbor, the closer is CLARANS to PAM, and the
  longer is each search of a local minima. But the quality of such a local
  minima is higher and fewer local minima needs to be obtained.

  `data` is the data set.
  `distance` is the distance/dissimilarity measure.
  `k` is the number of clusters.
  `max-neighbor` is the maximum number of neighbors examined during a random
  search of local minima.
  `num-local` is the number of local minima to search for."
  ([data distance k max-neighbor] (clarans data distance k max-neighbor 16))
  ([data distance k max-neighbor num-local]
   (PartitionClustering/run num-local
     (reify java.util.function.Supplier
       (get [this] (CLARANS/fit data distance k max-neighbor))))))

(defn dbscan
  "Density-Based Spatial Clustering of Applications with Noise.

  DBSCAN finds a number of clusters starting from the estimated density
  distribution of corresponding nodes.

  DBSCAN requires two parameters: radius (i.e. neighborhood radius) and the
  number of minimum points required to form a cluster (minPts). It starts
  with an arbitrary starting point that has not been visited. This point's
  neighborhood is retrieved, and if it contains sufficient number of points,
  a cluster is started. Otherwise, the point is labeled as noise. Note that
  this point might later be found in a sufficiently sized radius-environment
  of a different point and hence be made part of a cluster.

  If a point is found to be part of a cluster, its neighborhood is also
  part of that cluster. Hence, all points that are found within the
  neighborhood are added, as is their own neighborhood. This process
  continues until the cluster is completely found. Then, a new unvisited point
  is retrieved and processed, leading to the discovery of a further cluster
  of noise.

  DBSCAN visits each point of the database, possibly multiple times (e.g.,
  as candidates to different clusters). For practical considerations, however,
  the time complexity is mostly governed by the number of nearest neighbor
  queries. DBSCAN executes exactly one such query for each point, and if
  an indexing structure is used that executes such a neighborhood query
  in O(log n), an overall runtime complexity of O(n log n) is obtained.

  DBSCAN has many advantages such as

   - DBSCAN does not need to know the number of clusters in the data
     a priori, as opposed to k-means.
   - DBSCAN can find arbitrarily shaped clusters. It can even find clusters
     completely surrounded by (but not connected to) a different cluster.
     Due to the MinPts parameter, the so-called single-link effect
     (different clusters being connected by a thin line of points) is reduced.
   - DBSCAN has a notion of noise.
   - DBSCAN requires just two parameters and is mostly insensitive to the
     ordering of the points in the database. (Only points sitting on the
     edge of two different clusters might swap cluster membership if the
     ordering of the points is changed, and the cluster assignment is unique
     only up to isomorphism.)

  On the other hand, DBSCAN has the disadvantages of

   - In high dimensional space, the data are sparse everywhere
     because of the curse of dimensionality. Therefore, DBSCAN doesn't
     work well on high-dimensional data in general.
   - DBSCAN does not respond well to data sets with varying densities.

  `data` is the data set.

  `distance` is the distance measure for neighborhood search or the data
  structure for neighborhood search (e.g. k-d tree).

  `min-pts` is the minimum number of neighbors for a core data point.

  `radius` is the neighborhood radius."
  ([data min-pts radius] (dbscan data (EuclideanDistance.) min-pts radius))
  ([data distance min-pts radius] (DBSCAN/fit data distance min-pts radius)))

(defn denclue
  "DENsity CLUstering.

  The DENCLUE algorithm employs a cluster model based on kernel density
  estimation. A cluster is defined by a local maximum of the estimated
  density function. Data points going to the same local maximum are put
  into the same cluster.

  Clearly, DENCLUE doesn't work on data with uniform distribution. In high
  dimensional space, the data always look like uniformly distributed because
  of the curse of dimensionality. Therefore, DENCLUDE doesn't work well on
  high-dimensional data in general.

  `data` is the data set.

  `sigma` is the smooth parameter in the Gaussian kernel. The user can
  choose sigma such that number of density attractors is constant for a
  long interval of sigma.

  `m` is the number of selected samples used in the iteration.
  This number should be much smaller than the number of data points
  to speed up the algorithm. It should also be large enough to capture
  the sufficient information of underlying distribution."
  [data sigma m] (DENCLUE/fit data sigma m))

(defn mec
  "Nonparametric minimum conditional entropy clustering.

  This method performs very well especially when the exact number of clusters
  is unknown. The method can also correctly reveal the structure of data and
  effectively identify outliers simultaneously.

  The clustering criterion is based on the conditional entropy H(C | x), where
  C is the cluster label and x is an observation. According to Fano's
  inequality, we can estimate C with a low probability of error only if the
  conditional entropy H(C | X) is small. MEC also generalizes the criterion
  by replacing Shannon's entropy with Havrda-Charvat's structural
  &alpha;-entropy. Interestingly, the minimum entropy criterion based
  on structural &alpha;-entropy is equal to the probability error of the
  nearest neighbor method when &alpha;= 2. To estimate p(C | x), MEC employs
  Parzen density estimation, a nonparametric approach.

  MEC is an iterative algorithm starting with an initial partition given by
  any other clustering methods, e.g. k-means, CLARNAS, hierarchical clustering,
  etc. Note that a random initialization is NOT appropriate.

  `data` is the data set.

  `distance` is the distance measure for neighborhood search or the data
  structure for neighborhood search (e.g. k-d tree).

  `k` is the number of clusters. Note that this is just a hint. The final
  number of clusters may be less.

  `radius` is the neighborhood radius."
  ([data k radius] (mec data (EuclideanDistance.) k radius))
  ([data distance k radius] (MEC/fit data distance k radius)))

(defn specc
  "Spectral clustering.

  Given a set of data points, the similarity matrix may be defined as a
  matrix S where S<sub>ij</sub> represents a measure of the similarity
  between points. Spectral clustering techniques make use of the spectrum
  of the similarity matrix of the data to perform dimensionality reduction
  for clustering in fewer dimensions. Then the clustering will be performed
  in the dimension-reduce space, in which clusters of non-convex shape may
  become tight. There are some intriguing similarities between spectral
  clustering methods and kernel PCA, which has been empirically observed
  to perform clustering.

  `W` is the adjacency matrix of graph or the original data.
  `k` the number of clusters.
  `l` is the number of random samples for Nystrom approximation.
  `sigma` is the smooth/width parameter of Gaussian kernel, which
  is a somewhat sensitive parameter. To search for the best setting,
  one may pick the value that gives the tightest clusters (smallest
  distortion, see { @link #distortion()}) in feature space."
  ([data k sigma] (SpectralClustering/fit data k sigma))
  ([data k l sigma] (SpectralClustering/fit data k l sigma)))

