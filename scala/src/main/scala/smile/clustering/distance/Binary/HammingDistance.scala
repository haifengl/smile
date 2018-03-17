package smile.math.distance

/**
 * @author Beck Gaël
 * Class which implement the Hamming distance which measure difference between each pair of bite.
 * https://en.wikipedia.org/wiki/Hamming_distance
 **/
class HammingDistance extends BinaryDistance {
	/**
	  * The famous hamming distance
	  */
	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double = {
		var dh = 0D
		for( idx <- 0 until vector1.size ) dh += vector1(idx) ^ vector2(idx)
		dh
	}
	
}