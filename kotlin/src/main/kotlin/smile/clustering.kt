/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering

import java.util.function.ToDoubleBiFunction
import smile.clustering.linkage.*
import smile.math.distance.Distance
import smile.math.distance.EuclideanDistance
import smile.math.distance.Metric
import smile.math.matrix.Matrix
import smile.neighbor.RNNSearch
import smile.util.SparseArray

/**
 * Agglomerative Hierarchical Clustering. This method
 * seeks to build a hierarchy of clusters in a bottom up approach: each
 * observation starts in its own cluster, and pairs of clusters are merged as
 * one moves up the hierarchy. The results of hierarchical clustering are
 * usually presented in a dendrogram.
 *
 * In general, the merges are determined in a greedy manner. In order to decide
 * which clusters should be combined, a measure of dissimilarity between sets
 * of observations is required. In most methods of hierarchical clustering,
 * this is achieved by use of an appropriate metric, and a linkage criteria
 * which specifies the dissimilarity of sets as a function of the pairwise
 * distances of observations in the sets.
 *
 * Hierarchical clustering has the distinct advantage that any valid measure
 * of distance can be used. In fact, the observations themselves are not
 * required: all that is used is a matrix of distances.
 *
 * <h2>References</h2>
 *  - David Eppstein. Fast hierarchical clustering and other applications of dynamic closest pairs. SODA 1998.
 *
 * @param data The data set.
 * @param method the agglomeration method to merge clusters. This should be one of
 *                "single", "complete", "upgma", "upgmc", "wpgma", "wpgmc", and "ward".
 */
fun hclust(data: Array<DoubleArray>, method: String): HierarchicalClustering {
    val linkage = when (method) {
        "single" -> SingleLinkage.of(data)
        "complete" -> CompleteLinkage.of(data)
        "upgma", "average" -> UPGMALinkage.of(data)
        "upgmc", "centroid" -> UPGMCLinkage.of(data)
        "wpgma" -> WPGMALinkage.of(data)
        "wpgmc", "median" -> WPGMCLinkage.of(data)
        "ward" -> WardLinkage.of(data)
        else -> throw IllegalArgumentException("Unknown agglomeration method: " + method)
    }

    return HierarchicalClustering.fit(linkage)
}

/**
 * Agglomerative Hierarchical Clustering. This method
 * seeks to build a hierarchy of clusters in a bottom up approach: each
 * observation starts in its own cluster, and pairs of clusters are merged as
 * one moves up the hierarchy. The results of hierarchical clustering are
 * usually presented in a dendrogram.
 *
 * In general, the merges are determined in a greedy manner. In order to decide
 * which clusters should be combined, a measure of dissimilarity between sets
 * of observations is required. In most methods of hierarchical clustering,
 * this is achieved by use of an appropriate metric, and a linkage criteria
 * which specifies the dissimilarity of sets as a function of the pairwise
 * distances of observations in the sets.
 *
 * Hierarchical clustering has the distinct advantage that any valid measure
 * of distance can be used. In fact, the observations themselves are not
 * required: all that is used is a matrix of distances.
 *
 * <h2>References</h2>
 *  - David Eppstein. Fast hierarchical clustering and other applications of dynamic closest pairs. SODA 1998.
 *
 * @param data The data set.
 * @param distance the distance/dissimilarity measure.
 * @param method the agglomeration method to merge clusters. This should be one of
 *                "single", "complete", "upgma", "upgmc", "wpgma", "wpgmc", and "ward".
 */
fun <T> hclust(data: Array<T>, distance: Distance<T>, method: String): HierarchicalClustering {
    val linkage = when (method) {
        "single" -> SingleLinkage.of(data, distance)
        "complete" -> CompleteLinkage.of(data, distance)
        "upgma", "average" -> UPGMALinkage.of(data, distance)
        "upgmc", "centroid" -> UPGMCLinkage.of(data, distance)
        "wpgma" -> WPGMALinkage.of(data, distance)
        "wpgmc", "median" -> WPGMCLinkage.of(data, distance)
        "ward" -> WardLinkage.of(data, distance)
        else -> throw IllegalArgumentException("Unknown agglomeration method: " + method)
    }

    return HierarchicalClustering.fit(linkage)
}

/**
 * K-Modes clustering. K-Modes is the binary equivalent for K-Means.
 * The mean update for centroids is replace by the mode one which is
 * a majority vote among element of each cluster.
 */
fun kmodes(data: Array<IntArray>, k: Int, maxIter: Int = 100, runs: Int = 10): CentroidClustering<IntArray, IntArray> {
    return Clustering.run(runs, { KModes.fit(data, k, maxIter) })
}

/**
 * K-Means clustering. The algorithm partitions n observations into k clusters in which
 * each observation belongs to the cluster with the nearest mean.
 * Although finding an exact solution to the k-means problem for arbitrary
 * input is NP-hard, the standard approach to finding an approximate solution
 * (often called Lloyd's algorithm or the k-means algorithm) is used widely
 * and frequently finds reasonable solutions quickly.
 *
 * However, the k-means algorithm has at least two major theoretic shortcomings:
 *
 *  - First, it has been shown that the worst case running time of the
 * algorithm is super-polynomial in the input size.
 *  - Second, the approximation found can be arbitrarily bad with respect
 * to the objective function compared to the optimal learn.
 *
 * In this implementation, we use k-means++ which addresses the second of these
 * obstacles by specifying a procedure to initialize the cluster centers before
 * proceeding with the standard k-means optimization iterations. With the
 * k-means++ initialization, the algorithm is guaranteed to find a solution
 * that is O(log k) competitive to the optimal k-means solution.
 *
 * We also use k-d trees to speed up each k-means step as described in the filter
 * algorithm by Kanungo, et al.
 *
 * K-means is a hard clustering method, i.e. each sample is assigned to
 * a specific cluster. In contrast, soft clustering, e.g. the
 * Expectation-Maximization algorithm for Gaussian mixtures, assign samples
 * to different clusters with different probabilities.
 *
 * ====References:====
 *  - Tapas Kanungo, David M. Mount, Nathan S. Netanyahu, Christine D. Piatko, Ruth Silverman, and Angela Y. Wu. An Efficient k-Means Clustering Algorithm: Analysis and Implementation. IEEE TRANS. PAMI, 2002.
 *  - D. Arthur and S. Vassilvitskii. "K-means++: the advantages of careful seeding". ACM-SIAM symposium on Discrete algorithms, 1027-1035, 2007.
 *  - Anna D. Peterson, Arka P. Ghosh and Ranjan Maitra. A systematic evaluation of different methods for initializing the K-means clustering algorithm. 2010.
 *
 * This method runs the algorithm for given times and return the best one with smallest distortion.
 *
 * @param data the data set.
 * @param k the number of clusters.
 * @param maxIter the maximum number of iterations for each running.
 * @param runs the number of runs of K-Means algorithm.
 */
fun kmeans(data: Array<DoubleArray>, k: Int, maxIter: Int = 100, runs: Int = 16): CentroidClustering<DoubleArray, DoubleArray> {
    return Clustering.run(runs, { KMeans.fit(data, k, maxIter) })
}

/**
 * X-Means clustering algorithm, an extended K-Means which tries to
 * automatically determine the number of clusters based on BIC scores.
 * Starting with only one cluster, the X-Means algorithm goes into action
 * after each run of K-Means, making local decisions about which subset of the
 * current centroids should split themselves in order to better fit the data.
 * The splitting decision is done by computing the Bayesian Information
 * Criterion (BIC).
 *
 * ====References:====
 *  - Dan Pelleg and Andrew Moore. X-means: Extending K-means with Efficient Estimation of the Number of Clusters. ICML, 2000.
 *
 * @param data the data set.
 * @param k the maximum number of clusters.
 * @param maxIter the maximum number of iterations for k-means.
 */
fun xmeans(data: Array<DoubleArray>, k: Int = 100, maxIter: Int = 100): CentroidClustering<DoubleArray, DoubleArray> {
    return XMeans.fit(data, k, maxIter)
}

/**
 * G-Means clustering algorithm, an extended K-Means which tries to
 * automatically determine the number of clusters by normality test.
 * The G-means algorithm is based on a statistical test for the hypothesis
 * that a subset of data follows a Gaussian distribution. G-means runs
 * k-means with increasing k in a hierarchical fashion until the test accepts
 * the hypothesis that the data assigned to each k-means center are Gaussian.
 *
 * ====References:====
 *  - G. Hamerly and C. Elkan. Learning the k in k-means. NIPS, 2003.
 *
 * @param data the data set.
 * @param k the maximum number of clusters.
 * @param maxIter the maximum number of iterations for k-means.
 */
fun gmeans(data: Array<DoubleArray>, k: Int = 100, maxIter: Int = 100): CentroidClustering<DoubleArray, DoubleArray> {
    return GMeans.fit(data, k, maxIter)
}

/**
 * The Sequential Information Bottleneck algorithm. SIB clusters co-occurrence
 * data such as text documents vs words. SIB is guaranteed to converge to a local
 * maximum of the information. Moreover, the time and space complexity are
 * significantly improved in contrast to the agglomerative IB algorithm.
 *
 * In analogy to K-Means, SIB's update formulas are essentially same as the
 * EM algorithm for estimating finite Gaussian mixture model by replacing
 * regular Euclidean distance with Kullback-Leibler divergence, which is
 * clearly a better dissimilarity measure for co-occurrence data. However,
 * the common batch updating rule (assigning all instances to nearest centroids
 * and then updating centroids) of K-Means won't work in SIB, which has
 * to work in a sequential way (reassigning (if better) each instance then
 * immediately update related centroids). It might be because K-L divergence
 * is very sensitive and the centroids may be significantly changed in each
 * iteration in batch updating rule.
 *
 * Note that this implementation has a little difference from the original
 * paper, in which a weighted Jensen-Shannon divergence is employed as a
 * criterion to assign a randomly-picked sample to a different cluster.
 * However, this doesn't work well in some cases as we experienced probably
 * because the weighted JS divergence gives too much weight to clusters which
 * is much larger than a single sample. In this implementation, we instead
 * use the regular/unweighted Jensen-Shannon divergence.
 *
 * ====References:====
 *  - N. Tishby, F.C. Pereira, and W. Bialek. The information bottleneck method. 1999.
 *  - N. Slonim, N. Friedman, and N. Tishby. Unsupervised document classification using sequential information maximization. ACM SIGIR, 2002.
 *  - Jaakko Peltonen, Janne Sinkkonen, and Samuel Kaski. Sequential information bottleneck for finite data. ICML, 2004.
 *
 * @param data the data set.
 * @param k the number of clusters.
 * @param maxIter the maximum number of iterations.
 * @param runs the number of runs of SIB algorithm.
 */
fun sib(data: Array<SparseArray>, k: Int, maxIter: Int = 100, runs: Int = 8): CentroidClustering<DoubleArray, SparseArray> {
    return Clustering.run(runs, { SIB.fit(data, k, maxIter) })
}

/**
 * Deterministic annealing clustering. Deterministic annealing extends
 * soft-clustering to an annealing process.
 * For each temperature value, the algorithm iterates between the calculation
 * of all posteriori probabilities and the update of the centroids vectors,
 * until convergence is reached. The annealing starts with a high temperature.
 * Here, all centroids vectors converge to the center of the pattern
 * distribution (independent of their initial positions). Below a critical
 * temperature the vectors start to split. Further decreasing the temperature
 * leads to more splittings until all centroids vectors are separate. The
 * annealing can therefore avoid (if it is sufficiently slow) the convergence
 * to local minima.
 *
 * ====References:====
 *  - Kenneth Rose. Deterministic Annealing for Clustering, Compression, Classification, Regression, and Speech Recognition.
 *
 * @param data the data set.
 * @param k the maximum number of clusters.
 * @param alpha the temperature T is decreasing as T = T * alpha. alpha has
 *              to be in (0, 1).
 * @param tol   the tolerance of convergence test.
 * @param splitTol the tolerance to split a cluster.
 */
fun dac(data: Array<DoubleArray>, k: Int, alpha: Double = 0.9, maxIter: Int = 100, tol: Double = 1E-4, splitTol: Double = 1E-2): CentroidClustering<DoubleArray, DoubleArray> {
    return DeterministicAnnealing.fit(data, DeterministicAnnealing.Options(k, alpha, maxIter, tol, splitTol, null))
}

/**
 * Clustering Large Applications based upon RANdomized Search. CLARANS is an
 * efficient medoid-based clustering algorithm. The k-medoids algorithm is an
 * adaptation of the k-means algorithm. Rather than calculate the mean of the
 * items in each cluster, a representative item, or medoid, is chosen for each
 * cluster at each iteration. In CLARANS, the process of finding k medoids from
 * n objects is viewed abstractly as searching through a certain graph. In the
 * graph, a node is represented by a set of k objects as selected medoids. Two
 * nodes are neighbors if their sets differ by only one object. In each iteration,
 * CLARANS considers a set of randomly chosen neighbor nodes as candidate
 * of new medoids. We will move to the neighbor node if the neighbor
 * is a better choice for medoids. Otherwise, a local optima is discovered. The
 * entire process is repeated multiple time to find better.
 *
 * CLARANS has two parameters: the maximum number of neighbors examined
 * (maxNeighbor) and the number of local minima obtained (numLocal). The
 * higher the value of maxNeighbor, the closer is CLARANS to PAM, and the
 * longer is each search of a local minima. But the quality of such a local
 * minima is higher and fewer local minima needs to be obtained.
 *
 * ====References:====
 *  - R. Ng and J. Han. CLARANS: A Method for Clustering Objects for Spatial Data Mining. IEEE TRANS. KNOWLEDGE AND DATA ENGINEERING, 2002.
 *
 * @param data the data set.
 * @param distance the distance/dissimilarity measure.
 * @param k the number of clusters.
 * @param maxNeighbor the maximum number of neighbors examined during a random search of local minima.
 * @param numLocal the number of local minima to search for.
 */
fun <T> clarans(data: Array<T>, distance: Distance<T>, k: Int): CentroidClustering<T, T> {
    return KMedoids.fit(data, distance, k)
}

/**
 * Density-Based Spatial Clustering of Applications with Noise.
 * DBSCAN finds a number of clusters starting from the estimated density
 * distribution of corresponding nodes.
 *
 * DBSCAN requires two parameters: radius (i.e. neighborhood radius) and the
 * number of minimum points required to form a cluster (minPts). It starts
 * with an arbitrary starting point that has not been visited. This point's
 * neighborhood is retrieved, and if it contains sufficient number of points,
 * a cluster is started. Otherwise, the point is labeled as noise. Note that
 * this point might later be found in a sufficiently sized radius-environment
 * of a different point and hence be made part of a cluster.
 *
 * If a point is found to be part of a cluster, its neighborhood is also
 * part of that cluster. Hence, all points that are found within the
 * neighborhood are added, as is their own neighborhood. This process
 * continues until the cluster is completely found. Then, a new unvisited point
 * is retrieved and processed, leading to the discovery of a further cluster
 * of noise.
 *
 * DBSCAN visits each point of the database, possibly multiple times (e.g.,
 * as candidates to different clusters). For practical considerations, however,
 * the time complexity is mostly governed by the number of nearest neighbor
 * queries. DBSCAN executes exactly one such query for each point, and if
 * an indexing structure is used that executes such a neighborhood query
 * in O(log n), an overall runtime complexity of O(n log n) is obtained.
 *
 * DBSCAN has many advantages such as
 *
 *  - DBSCAN does not need to know the number of clusters in the data
 *    a priori, as opposed to k-means.
 *  - DBSCAN can find arbitrarily shaped clusters. It can even find clusters
 *    completely surrounded by (but not connected to) a different cluster.
 *    Due to the MinPts parameter, the so-called single-link effect
 *    (different clusters being connected by a thin line of points) is reduced.
 *  - DBSCAN has a notion of noise.
 *  - DBSCAN requires just two parameters and is mostly insensitive to the
 *    ordering of the points in the database. (Only points sitting on the
 *    edge of two different clusters might swap cluster membership if the
 *    ordering of the points is changed, and the cluster assignment is unique
 *    only up to isomorphism.)
 *
 * On the other hand, DBSCAN has the disadvantages of
 *
 *  - In high dimensional space, the data are sparse everywhere
 *    because of the curse of dimensionality. Therefore, DBSCAN doesn't
 *    work well on high-dimensional data in general.
 *  - DBSCAN does not respond well to data sets with varying densities.
 *
 * ====References:====
 *  - Martin Ester, Hans-Peter Kriegel, Jorg Sander, Xiaowei Xu (1996-). A density-based algorithm for discovering clusters in large spatial databases with noise". KDD, 1996.
 *  - Jorg Sander, Martin Ester, Hans-Peter  Kriegel, Xiaowei Xu. (1998). Density-Based Clustering in Spatial Databases: The Algorithm GDBSCAN and Its Applications. 1998.
 *
 * @param data the data set.
 * @param nns the data structure for neighborhood search.
 * @param minPts the minimum number of neighbors for a core data point.
 * @param radius the neighborhood radius.
 */
fun <T> dbscan(data: Array<T>, nns: RNNSearch<T, T>, minPts: Int, radius: Double): DBSCAN<T> {
    return DBSCAN.fit(data, nns, minPts, radius)
}

/**
 * Density-Based Spatial Clustering of Applications with Noise.
 * DBSCAN finds a number of clusters starting from the estimated density
 * distribution of corresponding nodes.
 *
 * @param data the data set.
 * @param distance the distance metric.
 * @param minPts the minimum number of neighbors for a core data point.
 * @param radius the neighborhood radius.
 */
fun <T> dbscan(data: Array<T>, distance: Distance<T>, minPts: Int, radius: Double): DBSCAN<T> {
    return DBSCAN.fit(data, distance, minPts, radius)
}

/**
 * DBSCAN with Euclidean distance.
 * DBSCAN finds a number of clusters starting from the estimated density
 * distribution of corresponding nodes.
 *
 * @param data the data set.
 * @param minPts the minimum number of neighbors for a core data point.
 * @param radius the neighborhood radius.
 */
fun dbscan(data: Array<DoubleArray>, minPts: Int, radius: Double): DBSCAN<DoubleArray> {
    return dbscan(data, EuclideanDistance(), minPts, radius)
}

/**
 * DENsity CLUstering. The DENCLUE algorithm employs a cluster model based on
 * kernel density estimation. A cluster is defined by a local maximum of the
 * estimated density function. Data points going to the same local maximum
 * are put into the same cluster.
 *
 * Clearly, DENCLUE doesn't work on data with uniform distribution. In high
 * dimensional space, the data always look like uniformly distributed because
 * of the curse of dimensionality. Therefore, DENCLUDE doesn't work well on
 * high-dimensional data in general.
 *
 * ====References:====
 *  - A. Hinneburg and D. A. Keim. A general approach to clustering in large databases with noise. Knowledge and Information Systems, 5(4):387-415, 2003.
 *  - Alexander Hinneburg and Hans-Henning Gabriel. DENCLUE 2.0: Fast Clustering based on Kernel Density Estimation. IDA, 2007.
 *
 * @param data the data set.
 * @param sigma the smooth parameter in the Gaussian kernel. The user can
 *              choose sigma such that number of density attractors is constant for a
 *              long interval of sigma.
 * @param m the number of selected samples used in the iteration.
 *          This number should be much smaller than the number of data points
 *          to speed up the algorithm. It should also be large enough to capture
 *          the sufficient information of underlying distribution.
 */
fun denclue(data: Array<DoubleArray>, sigma: Double, m: Int): DENCLUE {
    return DENCLUE.fit(data, sigma, m)
}

/**
 * Nonparametric Minimum Conditional Entropy Clustering. This method performs
 * very well especially when the exact number of clusters is unknown.
 * The method can also correctly reveal the structure of data and effectively
 * identify outliers simultaneously.
 *
 * The clustering criterion is based on the conditional entropy H(C | x), where
 * C is the cluster label and x is an observation. According to Fano's
 * inequality, we can estimate C with a low probability of error only if the
 * conditional entropy H(C | X) is small. MEC also generalizes the criterion
 * by replacing Shannon's entropy with Havrda-Charvat's structural
 * &alpha;-entropy. Interestingly, the minimum entropy criterion based
 * on structural &alpha;-entropy is equal to the probability error of the
 * nearest neighbor method when &alpha;= 2. To estimate p(C | x), MEC employs
 * Parzen density estimation, a nonparametric approach.
 *
 * MEC is an iterative algorithm starting with an initial partition given by
 * any other clustering methods, e.g. k-means, CLARNAS, hierarchical clustering,
 * etc. Note that a random initialization is NOT appropriate.
 *
 * ====References:====
 *  - Haifeng Li. All rights reserved., Keshu Zhang, and Tao Jiang. Minimum Entropy Clustering and Applications to Gene Expression Analysis. CSB, 2004.
 *
 * @param data the data set.
 * @param distance the distance measure for neighborhood search.
 * @param k the number of clusters. Note that this is just a hint. The final
 *          number of clusters may be less.
 * @param radius the neighborhood radius.
 */
fun <T> mec(data: Array<T>, distance: Distance<T>, k: Int, radius: Double): MEC<T> {
    return MEC.fit(data, distance, k, radius)
}

/**
 * Nonparametric Minimum Conditional Entropy Clustering.
 *
 * @param data the data set.
 * @param distance the distance measure for neighborhood search.
 * @param k the number of clusters. Note that this is just a hint. The final
 *          number of clusters may be less.
 * @param radius the neighborhood radius.
 */
fun <T> mec(data: Array<T>, distance: Metric<T>, k: Int, radius: Double): MEC<T> {
    return MEC.fit(data, distance, k, radius)
}

/**
 * Nonparametric Minimum Conditional Entropy Clustering. Assume Euclidean distance.
 *
 * @param data the data set.
 * @param k the number of clusters. Note that this is just a hint. The final
 *          number of clusters may be less.
 * @param radius the neighborhood radius.
 */
fun mec(data: Array<DoubleArray>, k: Int, radius: Double): MEC<DoubleArray> {
    return MEC.fit(data, EuclideanDistance(), k, radius)
}

/**
 * Spectral clustering with Nystrom approximation.
 * @param data the dataset for clustering.
 * @param k the number of clusters.
 * @param l the number of random samples for Nystrom approximation.
 * @param sigma the smooth/width parameter of Gaussian kernel, which
 *              is a somewhat sensitive parameter. To search for the best setting,
 *              one may pick the value that gives the tightest clusters (smallest
 *              distortion, see { @link #distortion()}) in feature space.
 * @param maxIter the maximum number of iterations for k-means.
 */
fun specc(data: Array<DoubleArray>, k: Int, sigma: Double, l: Int = 0, maxIter: Int = 100): CentroidClustering<DoubleArray, DoubleArray> {
    return SpectralClustering.fit(data, SpectralClustering.Options(k, l, sigma, maxIter))
}
