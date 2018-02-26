package smile.clustering

trait ScalaClusteringTypes
{
	type BinaryVector = Array[Int]
	type Mode = Array[Int]
	type ClusterID = Int
	type ID = Int
	type ClusterizedData = Array[(ClusterID, ID, BinaryVector)]
}