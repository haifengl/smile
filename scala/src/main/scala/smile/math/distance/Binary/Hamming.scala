package smile.math.distance

/**
 * @author Beck Gaël
 * Class which implement the Hamming distance which measure difference between each pair of bite.
 * https://en.wikipedia.org/wiki/Hamming_distance
 **/
class Hamming extends Distance[Array[Int]] {
	/**
	  * The famous hamming distance
	  */
	def d(vector1: Array[Int], vector2: Array[Int]) : Double = {
		var dh = 0D
		for( idx <- 0 until vector1.size ) dh += vector1(idx) ^ vector2(idx)
		dh
	}
}