package smile.math.distance

/**
 * @author Beck Gaël
 * General trait for scala distance measures
 **/
trait Distance extends Serializable
{
	type T
	def distance(vector1: Array[T], vector2: Array[T]) : Double
}
