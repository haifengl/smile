package smile.clustering.kmodes

import _root_.scala.collection.mutable
import _root_.smile.clustering.clusteringTypes.BinaryClusteringTypes
import _root_.smile.math.distance.{Distance, Hamming}

/**
 * @author Beck Gaël
 * KMeans Model that keeps cluster centroids and cardinalities. And offer prediction functionalities
 **/
class KModesModel(val centroids: mutable.HashMap[Int, Array[Int]], val cardinalities: mutable.HashMap[Int, Int], val metric: Distance[Array[Int]]) extends BinaryClusteringTypes {
	/**
	 * Return the nearest mode for a specific point
	 **/
	def predict(v: Array[Int]): ClusterID = {
		centroids.toArray.map{ case(clusterID, centroid) => (clusterID, metric.d(centroid, v)) }.sortBy(_._2).head._1
	}

	/**
	 * Return the nearest mode for a dataset
	 **/
	def predict(data: Seq[Array[Int]]): Seq[(ClusterID, BinaryVector)] = {
		val centroidsAsArray = centroids.toArray

		def predictCluster(v: Array[Int]) = {
			centroidsAsArray.map{ case(clusterID, centroid) => (clusterID, metric.d(centroid, v)) }.sortBy(_._2).head._1
		}

		data.map( v => (predictCluster(v), v) )
	}
}