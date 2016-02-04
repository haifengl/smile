package smile.clustering

import smile.clustering._
import smile.clustering.linkage._
import smile.math.distance._

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
    * @param data the input data of which each row is a sample.
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
    * @param data the input data of which each row is a sample.
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
    * @param data the input data of which each row is a sample.
    * @param k the maximum number of clusters.
    */
  def gmeans(data: Array[Array[Double]], k: Int): GMeans = {
    new GMeans(data, k)
  }
}