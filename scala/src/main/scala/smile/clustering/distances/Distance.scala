package smile.clustering.distances

trait Distance extends Serializable
{
	type T
	def distance(vector1: Array[T], vector2: Array[T]) : Double
}
