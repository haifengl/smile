package smile.math.distance

/**
 * @author Beck Gaël
 *
 **/
trait BinaryDistance extends Distance {
	
	type T = Int
	def distance(vector1: Array[T], vector2: Array[T]) : Double
}