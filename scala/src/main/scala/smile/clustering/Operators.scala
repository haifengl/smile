/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
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

package smile.clustering

import smile.clustering._
import smile.clustering.linkage._
import smile.data.SparseDataset
import smile.math.distance._
import smile.neighbor.{CoverTree, KDTree, RNNSearch}

/** High level cluster analysis operators.
  *
  * @author Haifeng Li
  */
trait Operators {
  /** Returns the proximity matrix of a dataset for given distance function.
    * To save space, we only store the lower half of matrix.
    *
    * @param data the data set.
    * @param dist the distance function.
    * @return the lower half of proximity matrix.
    */
  def proximity[T](data: Array[T], dist: Distance[T]): Array[Array[Double]] = {
    val n = data.length
    val d = new Array[Array[Double]](n)
    for (i <- 0 until n) {
      d(i) = new Array[Double](i+1)
      for (j <- 0 until i)
        d(i)(j) = dist.d(data(i), data(j))
    }
    d
  }

  /** Returns the proximity matrix of a dataset for Euclidean distance.
    * To save space, we only store the lower half of matrix.
    *
    * @param data the data set.
    * @return the lower half of proximity matrix.
    */
  def proximity(data: Array[Array[Double]]): Array[Array[Double]] = {
    proximity(data, new EuclideanDistance)
  }

  /** Agglomerative Hierarchical Clustering. This method
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
    * @param proximity The proximity matrix to store the distance measure of
    *                  dissimilarity. To save space, we only need the lower half of matrix.
    * @param method the agglomeration method to merge clusters. This should be one of
    *                "single", "complete", "upgma", "upgmc", "wpgma", "wpgmc", and "ward".
    */
  def hclust(proximity: Array[Array[Double]], method: String): HierarchicalClustering = {
    val linkage = method match {
      case "single" => new SingleLinkage(proximity)
      case "complete" => new CompleteLinkage(proximity)
      case "upgma" => new UPGMALinkage(proximity)
      case "upgmc" => new UPGMCLinkage(proximity)
      case "wpgma" => new WPGMALinkage(proximity)
      case "wpgmc" => new WPGMCLinkage(proximity)
      case "ward" => new WardLinkage(proximity)
      case _ => throw new IllegalArgumentException(s"Unknown agglomeration method: $method")
    }
    new HierarchicalClustering(linkage)
  }

  /** K-Means clustering. The algorithm partitions n observations into k clusters in which
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
    * @param data the data set.
    * @param k the number of clusters.
    * @param maxIter the maximum number of iterations for each running.
    * @param runs the number of runs of K-Means algorithm.
    */
  def kmeans(data: Array[Array[Double]], k: Int, maxIter: Int = 100, runs: Int = 1): KMeans = {
    new KMeans(data, k, maxIter, runs)
  }

  /** X-Means clustering algorithm, an extended K-Means which tries to
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
    */
  def xmeans(data: Array[Array[Double]], k: Int): XMeans = {
    new XMeans(data, k)
  }

  /** G-Means clustering algorithm, an extended K-Means which tries to
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
    */
  def gmeans(data: Array[Array[Double]], k: Int): GMeans = {
    new GMeans(data, k)
  }

  /** The Sequential Information Bottleneck algorithm. SIB clusters co-occurrence
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
  def sib(data: Array[Array[Double]], k: Int, maxIter: Int = 100, runs: Int = 1): SIB = {
    new SIB(data, k, maxIter, runs)
  }

  /** The Sequential Information Bottleneck algorithm on sparse dataset.
    *
    * @param data the data set.
    * @param k the number of clusters.
    * @param maxIter the maximum number of iterations.
    * @param runs the number of runs of SIB algorithm.
    */
  def sib(data: SparseDataset, k: Int, maxIter: Int, runs: Int): SIB = {
    new SIB(data, k, maxIter, runs)
  }

  /** Deterministic annealing clustering. Deterministic annealing extends
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
    */
  def dac(data: Array[Array[Double]], k: Int, alpha: Double): DeterministicAnnealing = {
    new DeterministicAnnealing(data, k, alpha)
  }

  /** Clustering Large Applications based upon RANdomized Search. CLARANS is an
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
  def clarans[T <: Object](data: Array[T], distance: Distance[T], k: Int, maxNeighbor: Int, numLocal: Int): CLARANS[T] = {
    new CLARANS[T](data, distance, k, maxNeighbor, numLocal)
  }

  /** Clustering Large Applications based upon RANdomized Search. Euclidean distance is assumed.
    *
    * @param data the data set.
    * @param k the number of clusters.
    * @param maxNeighbor the maximum number of neighbors examined during a random search of local minima.
    * @param numLocal the number of local minima to search for.
    */
  def clarans(data: Array[Array[Double]], k: Int, maxNeighbor: Int, numLocal: Int): CLARANS[Array[Double]] = {
    new CLARANS(data, new EuclideanDistance, k, maxNeighbor, numLocal)
  }

  /** Balanced Iterative Reducing and Clustering using Hierarchies. BIRCH performs
    * hierarchical clustering over particularly large datasets. An advantage of
    * BIRCH is its ability to incrementally and dynamically cluster incoming,
    * multi-dimensional metric data points in an attempt to produce the high
    * quality clustering for a given set of resources (memory and time constraints).
    *
    * BIRCH has several advantages. For example, each clustering decision is made
    * without scanning all data points and currently existing clusters. It
    * exploits the observation that data space is not usually uniformly occupied
    * and not every data point is equally important. It makes full use of
    * available memory to derive the finest possible sub-clusters while minimizing
    * I/O costs. It is also an incremental method that does not require the whole
    * data set in advance.
    *
    * This implementation produces a clustering in three steps. First step
    * builds a CF (clustering feature) tree by a single scan of database.
    * The second step clusters the leaves of CF tree by hierarchical clustering.
    * Then the user can use the learned model to cluster input data in the final
    * step. In total, we scan the database twice.
    *
    * ====References:====
    *  - Tian Zhang, Raghu Ramakrishnan, and Miron Livny. BIRCH: An Efficient Data Clustering Method for Very Large Databases. SIGMOD, 1996.
    *
    * @param data the data set.
    * @param k the number of clusters.
    * @param minPts a CF leaf will be treated as outlier if the number of its
    *               points is less than minPts.
    * @param branch the branching factor. Maximum number of children nodes.
    * @param radius the maximum radius of a sub-cluster.
    */
  def birch(data: Array[Array[Double]], k: Int, minPts: Int, branch: Int, radius: Double): BIRCH = {
    val cluster = new BIRCH(data(0).length, branch, radius)
    data.foreach(cluster.add(_))
    cluster.partition(k, minPts)
    cluster
  }

  /** Density-Based Spatial Clustering of Applications with Noise.
    * DBScan finds a number of clusters starting from the estimated density
    * distribution of corresponding nodes.
    *
    * DBScan requires two parameters: radius (i.e. neighborhood radius) and the
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
    * DBScan visits each point of the database, possibly multiple times (e.g.,
    * as candidates to different clusters). For practical considerations, however,
    * the time complexity is mostly governed by the number of nearest neighbor
    * queries. DBScan executes exactly one such query for each point, and if
    * an indexing structure is used that executes such a neighborhood query
    * in O(log n), an overall runtime complexity of O(n log n) is obtained.
    *
    * DBScan has many advantages such as
    *
    *  - DBScan does not need to know the number of clusters in the data
    *    a priori, as opposed to k-means.
    *  - DBScan can find arbitrarily shaped clusters. It can even find clusters
    *    completely surrounded by (but not connected to) a different cluster.
    *    Due to the MinPts parameter, the so-called single-link effect
    *    (different clusters being connected by a thin line of points) is reduced.
    *  - DBScan has a notion of noise.
    *  - DBScan requires just two parameters and is mostly insensitive to the
    *    ordering of the points in the database. (Only points sitting on the
    *    edge of two different clusters might swap cluster membership if the
    *    ordering of the points is changed, and the cluster assignment is unique
    *    only up to isomorphism.)
    *
    * On the other hand, DBScan has the disadvantages of
    *
    *  - In high dimensional space, the data are sparse everywhere
    *    because of the curse of dimensionality. Therefore, DBScan doesn't
    *    work well on high-dimensional data in general.
    *  - DBScan does not respond well to data sets with varying densities.
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
  def dbscan[T <: Object](data: Array[T], nns: RNNSearch[T, T], minPts: Int, radius: Double): DBScan[T] = {
    new DBScan[T](data, nns, minPts, radius)
  }

  /** Density-Based Spatial Clustering of Applications with Noise.
    * DBScan finds a number of clusters starting from the estimated density
    * distribution of corresponding nodes.
    * Cover Tree is used for nearest neighbor search.
    *
    * @param data the data set.
    * @param distance the distance metric.
    * @param minPts the minimum number of neighbors for a core data point.
    * @param radius the neighborhood radius.
   */
  def dbscan[T <: Object](data: Array[T], distance: Metric[T], minPts: Int, radius: Double): DBScan[T] = {
    new DBScan[T](data, distance, minPts, radius)
  }

  /** Density-Based Spatial Clustering of Applications with Noise.
    * DBScan finds a number of clusters starting from the estimated density
    * distribution of corresponding nodes.
    * If the data dimensionality is less than 10, KD-Tree is used for
    * neighbor search, otherwise Cover Tree is employed.
    *
    * @param data the data set.
    * @param minPts the minimum number of neighbors for a core data point.
    * @param radius the neighborhood radius.
    */
  def dbscan(data: Array[Array[Double]], minPts: Int, radius: Double): DBScan[Array[Double]] = {
    val n = data.length
    val k = data(0).length
    // KD-Tree performs only when n >> 2^k
    if (n > 10 * (1 << k)) new DBScan(data, new KDTree[Array[Double]](data, data), minPts, radius)
    else new DBScan(data, new CoverTree(data, new EuclideanDistance), minPts, radius)
  }

  /** DENsity CLUstering. The DENCLUE algorithm employs a cluster model based on
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
  def denclue(data: Array[Array[Double]], sigma: Double, m: Int): DENCLUE = {
    new DENCLUE(data, sigma, m)
  }

  /** Neural Gas soft competitive learning algorithm. The Neural Gas is inspired
    * by the Self-Organizing Map for finding optimal data representations based on
    * feature vectors. The algorithm was coined "Neural Gas" because of the
    * dynamics of the feature vectors during the adaptation process, which
    * distribute themselves like a gas within the data space. Although it is mainly
    * applied where data compression or vector quantization is an issue,
    * it is also used for cluster analysis as a robustly converging alternative to
    * the k-means clustering. A prominent extension is the Growing Neural Gas.
    *
    * Compared to SOM, neural gas has no topology of a fixed dimensionality
    * (in fact, no topology at all). For each input signal during learning, the
    * neural gas algorithm sorts the neurons of the network according to the
    * distance of their reference vectors to the input signal. Based on this
    * "rank order", neurons are adapted based on the adaptation strength that are
    * decreased according to a fixed schedule.
    *
    * The adaptation step of the Neural Gas can be interpreted as gradient descent
    * on a cost function. By adapting not only the closest feature vector but all
    * of them with a step size decreasing with increasing distance order,
    * compared to k-means clustering, a much more robust convergence of the
    * algorithm can be achieved.
    *
    * ====References:====
    *  - Thomas Martinetz and Klaus Schulten. A "neural gas" network learns topologies. Artificial Neural Networks, 397-402, 1991.
    *  - T. Martinetz, S. Berkovich, and K. Schulten. "Neural-gas" Network for Vector Quantization and its Application to Time-Series Prediction. IEEE Trans. on Neural Networks, 4(4):558-569, 1993.
    *  - T. Martinetz and K. Schulten. Topology representing networks. Neural Networks, 7(3):507-522, 1994.
    *
    * @param data the data set.
    * @param k the number of units in the neural gas.
    * @param lambda_i the initial value of lambda. lambda_i and lambda_f are
    *                 used to set the soft learning radius/rate, i.e. determining the number
    *                 of neural units significantly changing their synaptic weights with
    *                 each adaptation step.
    * @param lambda_f The final value of lambda.
    * @param eps_i the initial value of epsilon. epsilon_i and epsilon_f
    *              are the initial and final learning rate respectively.
    * @param eps_f the final value of epsilon.
    * @param steps the number of iterations. Note that for one iteration, we
    *              mean that the learning process goes through the whole dataset.
    */
  def neuralgas(data: Array[Array[Double]], k: Int, lambda_i: Double, lambda_f: Double = 0.01, eps_i: Double = 0.5, eps_f: Double = 0.005, steps: Int = 25): NeuralGas = {
    new NeuralGas(data, k, lambda_i, lambda_f, eps_i, eps_f, steps)
  }

  /** Growing Neural Gas. As an extension of Neural Gas, Growing Neural Gas
    * can add and delete nodes during algorithm execution.  The growth mechanism
    * is based on growing cell structures and competitive Hebbian learning.
    *
    * Compared to Neural Gas, GNG has the following distinctions:
    *
    *  - The system has the ability to add and delete nodes.
    *  - Local Error measurements are noted at each step helping it to locally
    * insert/delete nodes.
    *  - Edges are connected between nodes, so a sufficiently old edges is
    * deleted. Such edges are intended place holders for localized data distribution.
    *  - Such edges also help to locate distinct clusters (those clusters are
    * not connected by edges).
    *
    * ====References:====
    *  - B. Fritzke. A growing neural gas network learns topologies. NIPS, 1995.
    *
    * @param data the data set.
    * @param k the number of clusters.
    * @param epsBest the fraction to update nearest neuron.
    * @param epsNeighbor the fraction to update neighbors of nearest neuron.
    * @param maxEdgeAge the maximum age of edges.
    * @param lambda if the number of input signals so far is an integer multiple
    *               of lambda, insert a new neuron.
    * @param alpha decrease error variables by multiplying them with alpha
    *              during inserting a new neuron.
    * @param beta decrease all error variables by multiply them with beta.
   */
  def neuralgas(data: Array[Array[Double]], k: Int, epsBest: Double, epsNeighbor: Double, maxEdgeAge: Int, lambda: Int, alpha: Double, beta: Double): GrowingNeuralGas = {
    val cluster = new GrowingNeuralGas(data(0).length, epsBest, epsNeighbor, maxEdgeAge, lambda, alpha, beta)
    data.foreach(cluster.update(_))
    cluster.partition(k)
    cluster
  }

  /** NeuralMap is an efficient competitive learning algorithm inspired by growing
    * neural gas and BIRCH. Like growing neural gas, NeuralMap has the ability to
    * add and delete neurons with competitive Hebbian learning. Edges exist between
    * neurons close to each other. The edges are intended place holders for
    * localized data distribution. The edges also help to locate distinct clusters
    * (those clusters are not connected by edges). NeuralMap employs Locality-Sensitive
    * Hashing to speedup the learning while BIRCH uses balanced CF trees.
    *
    * @param data the data set.
    * @param k the number of clusters.
    * @param minPts neurons will be removed if the number of its points is
    *               less than minPts.
    * @param r the distance radius to activate a neuron for a given signal.
    * @param epsBest the fraction to update activated neuron.
    * @param epsNeighbor the fraction to update neighbors of activated neuron.
    * @param L the number of hash tables.
    * @param rp the number of random projection hash functions.
    */
  def neuralmap(data: Array[Array[Double]], k: Int, minPts: Int, r: Double, epsBest: Double, epsNeighbor: Double, L: Int, rp: Int): NeuralMap = {
    val cluster = new NeuralMap(data(0).length, r, epsBest, epsNeighbor, L, rp)
    data.foreach(cluster.update(_))
    cluster.purge(minPts)
    cluster.partition(k)
    cluster

  }

  /** Nonparametric Minimum Conditional Entropy Clustering. This method performs
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
    *  - Haifeng Li, Keshu Zhang, and Tao Jiang. Minimum Entropy Clustering and Applications to Gene Expression Analysis. CSB, 2004.
    *
    * @param data the data set.
    * @param distance the distance measure for neighborhood search.
    * @param k the number of clusters. Note that this is just a hint. The final
    *          number of clusters may be less.
    * @param radius the neighborhood radius.
    */
  def mec[T <: Object](data: Array[T], distance: Distance[T], k: Int, radius: Double): MEC[T] = {
    new MEC(data, distance, k, radius)
  }

  /** Nonparametric Minimum Conditional Entropy Clustering.
    *
    * @param data the data set.
    * @param distance the distance measure for neighborhood search.
    * @param k the number of clusters. Note that this is just a hint. The final
    *          number of clusters may be less.
    * @param radius the neighborhood radius.
    */
  def mec[T <: Object](data: Array[T], distance: Metric[T], k: Int, radius: Double): MEC[T] = {
    new MEC(data, distance, k, radius)
  }

  /** Nonparametric Minimum Conditional Entropy Clustering. Assume Euclidean distance.
    *
    * @param data the data set.
    * @param k the number of clusters. Note that this is just a hint. The final
    *          number of clusters may be less.
    * @param radius the neighborhood radius.
    */
  def mec(data: Array[Array[Double]], k: Int, radius: Double): MEC[Array[Double]] = {
    new MEC(data, new EuclideanDistance, k, radius)
  }

  /** Nonparametric Minimum Conditional Entropy Clustering.
    *
    * @param data the data set.
    * @param nns the data structure for neighborhood search.
    * @param k the number of clusters. Note that this is just a hint. The final
    *          number of clusters may be less.
    * @param radius the neighborhood radius.
    */
  def mec[T <: Object](data: Array[T], nns: RNNSearch[T, T], k: Int, radius: Double, y: Array[Int]): MEC[T] = {
    new MEC(data, nns, k, radius, y)
  }

  /** Spectral Clustering. Given a set of data points, the similarity matrix may
    * be defined as a matrix S where S<sub>ij</sub> represents a measure of the
    * similarity between points. Spectral clustering techniques make use of the
    * spectrum of the similarity matrix of the data to perform dimensionality
    * reduction for clustering in fewer dimensions. Then the clustering will
    * be performed in the dimension-reduce space, in which clusters of non-convex
    * shape may become tight. There are some intriguing similarities between
    * spectral clustering methods and kernel PCA, which has been empirically
    * observed to perform clustering.
    *
    * ====References:====
    *  - A.Y. Ng, M.I. Jordan, and Y. Weiss. On Spectral Clustering: Analysis and an algorithm. NIPS, 2001.
    *  - Marina Maila and Jianbo Shi. Learning segmentation by random walks. NIPS, 2000.
    *  - Deepak Verma and Marina Meila. A Comparison of Spectral Clustering Algorithms. 2003.
    *
    * @param W the adjacency matrix of graph.
    * @param k the number of clusters.
    */
  def specc(W: Array[Array[Double]], k: Int): SpectralClustering = {
    new SpectralClustering(W, k)
  }

  /** Spectral clustering.
    * @param data the dataset for clustering.
    * @param k the number of clusters.
    * @param sigma the smooth/width parameter of Gaussian kernel, which
    *              is a somewhat sensitive parameter. To search for the best setting,
    *              one may pick the value that gives the tightest clusters (smallest
    *              distortion, see { @link #distortion()}) in feature space.
    */
  def specc(data: Array[Array[Double]], k: Int, sigma: Double): SpectralClustering = {
    new SpectralClustering(data, k, sigma)
  }

  /** Spectral clustering with Nystrom approximation.
    * @param data the dataset for clustering.
    * @param k the number of clusters.
    * @param l the number of random samples for Nystrom approximation.
    * @param sigma the smooth/width parameter of Gaussian kernel, which
    *              is a somewhat sensitive parameter. To search for the best setting,
    *              one may pick the value that gives the tightest clusters (smallest
    *              distortion, see { @link #distortion()}) in feature space.
    */
  def specc(data: Array[Array[Double]], k: Int, l: Int, sigma: Double): SpectralClustering = {
    new SpectralClustering(data, k, l, sigma)
  }

  /** Self-Organizing Map. An SOM is a unsupervised learning method to produce
    * a low-dimensional (typically two-dimensional) discretized representation
    * (called a map) of the input space of the training samples. The model was
    * first described as an artificial neural network by Teuvo Kohonen, and is
    * sometimes called a Kohonen map.
    *
    * While it is typical to consider SOMs as related to feed-forward networks where
    * the nodes are visualized as being attached, this type of architecture is
    * fundamentally different in arrangement and motivation because SOMs use a
    * neighborhood function to preserve the topological properties of the input
    * space. This makes SOMs useful for visualizing low-dimensional views of
    * high-dimensional data, akin to multidimensional scaling.
    *
    * SOMs belong to a large family of competitive learning process and vector
    * quantization. An SOM consists of components called nodes or neurons.
    * Associated with each node is a weight vector of the same dimension as
    * the input data vectors and a position in the map space. The usual arrangement
    * of nodes is a regular spacing in a hexagonal or rectangular grid. The
    * self-organizing map describes a mapping from a higher dimensional input
    * space to a lower dimensional map space. During the (iterative) learning,
    * the input vectors are compared to the weight vector of each neuron. Neurons
    * who most closely match the input are known as the best match unit (BMU) of
    * the system. The weight vector of the BMU and those of nearby neurons are
    * adjusted to be closer to the input vector by a certain step size.
    *
    * There are two ways to interpret a SOM. Because in the training phase weights
    * of the whole neighborhood are moved in the same direction, similar items
    * tend to excite adjacent neurons. Therefore, SOM forms a semantic map where
    * similar samples are mapped close together and dissimilar apart.
    * The other way is to think of neuronal weights as pointers to the input space.
    * They form a discrete approximation of the distribution of training samples.
    * More neurons point to regions with high training sample concentration and
    * fewer where the samples are scarce.
    *
    * SOM may be considered a nonlinear generalization of Principal components
    * analysis (PCA). It has been shown, using both artificial and real
    * geophysical data, that SOM has many advantages over the conventional feature
    * extraction methods such as Empirical Orthogonal Functions (EOF) or PCA.
    *
    * It has been shown that while SOMs with a small number of nodes behave in a
    * way that is similar to K-means. However, larger SOMs rearrange data
    * in a way that is fundamentally topological in character and display properties
    * which are emergent. Therefore, large maps are preferable to smaller ones.
    * In maps consisting of thousands of nodes, it is possible to perform cluster
    * operations on the map itself.
    *
    * A common way to display SOMs is the heat map of U-matrix. The U-matrix value
    * of a particular node is the minimum/maximum/average distance between the node
    * and its closest neighbors. In a rectangular grid for instance, we might
    * consider the closest 4 or 8 nodes.
    *
    * ====References:====
    *  - Teuvo KohonenDan. Self-organizing maps. Springer, 3rd edition, 2000. </li>
    *
    * @param data the dataset for clustering.
    * @param k the number of clusters.
    * @param width the width of map.
    * @param height the height of map.
    */
  def som(data: Array[Array[Double]], k: Int, width: Int, height: Int): SOM = {
    val cluster = new SOM(data, width, height)
    cluster.partition(k)
    cluster
  }
}