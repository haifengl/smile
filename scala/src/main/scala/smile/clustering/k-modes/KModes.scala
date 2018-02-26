package smile.clustering

import scala.collection.mutable
import scala.util.Random
import smile.clustering.distances.Binary_Distance

class KModes(data: Array[(Int, Array[Int])], k: Int, epsilon: Double, jmax: Int, metric: Binary_Distance) extends ScalaClusteringTypes
{
	type ClusterID = Int
	type ID = Int
	type BinaryVector = Array[Int]
	type ClusterizedData = Array[(ClusterID, ID, BinaryVector)]

	val dim = data.head._2.size

	def sumTwoBinaryVector(vector1: Array[Int], vector2: Array[Int]) = for( i <- vector1.indices.toArray ) yield( vector1(i) + vector2(i) )

	def apply : (ClusterizedData, mutable.HashMap[Int, Array[Int]]) = {
		// Random initialization of modes and set their cardinalities to 0
		val kmodes = mutable.HashMap((for( clusterID <- 0 until k ) yield( (clusterID, Array.fill(dim)(Random.nextInt(2))) )):_*)
		val kmodesCpt = kmodes.map{ case (clusterID, _) => (clusterID, 0) }
		/**
		 * Return the nearest mode for a specific point
		 **/
		def obtainNearestModID(v: Array[Int]): ClusterID = kmodes.toArray.map{ case(clusterID, mod) => (clusterID, metric.distance(mod, v)) }.sortBy(_._2).head._1

		val zeroMod = Array.fill(dim)(0)
		var cpt = 0
		var allModsHaveConverged = false
		while( cpt < jmax && ! allModsHaveConverged )
		{
			// Allocation to modes
			val clusterized = data.map{ case (id, v) => (id, v, obtainNearestModID(v)) }
			// Cloning modes to later comparaison
			val kModesBeforeUpdate = kmodes.clone
			// Reinitialization of modes
			kmodes.foreach{ case (clusterID, _) => kmodes(clusterID) = zeroMod }
			kmodesCpt.foreach{ case (clusterID, _) => kmodesCpt(clusterID) = 0 }
			// Updatating Modes
			clusterized.foreach{ case (_, v, clusterID) =>
			{
				kmodes(clusterID) = sumTwoBinaryVector(kmodes(clusterID), v)
				kmodesCpt(clusterID) += 1
			}}
			// Updating modes
			kmodes.foreach{ case (clusterID, mod) => kmodes(clusterID) = mod.map( v => if( v * 2 >= kmodesCpt(clusterID) ) 1 else 0 ) }
			// Check if every mode have converged
			allModsHaveConverged = kModesBeforeUpdate.forall{ case (clusterID, previousMod) => metric.distance(previousMod, kmodes(clusterID)) <= epsilon }

			cpt += 1
		}

		val finalClustering = data.map{ case (id, v) =>
		{
			val clusterID = obtainNearestModID(v)
			(clusterID, id, v)
		}}
		(finalClustering, kmodes)
	}
}

object KModes extends ScalaClusteringTypes
{
	def run(data: Array[(ID, BinaryVector)], k: Int, epsilon: Double, jmax: Int, metric: Binary_Distance): (ClusterizedData, mutable.HashMap[Int, Array[Int]]) = {
		val kmodes = new KModes(data, k, epsilon, jmax, metric)
		kmodes.apply
	}
}