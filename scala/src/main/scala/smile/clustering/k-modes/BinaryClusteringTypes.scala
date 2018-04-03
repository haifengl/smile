package smile.clustering.clusteringTypes

trait BinaryClusteringTypes
{
	type ClusterID = Int
	type ID = Int
	type BinaryVector = Array[Int]
	type ClusterizedData = Array[(ClusterID, ID, BinaryVector)]
}