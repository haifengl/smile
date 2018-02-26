package smile.math.distance

/**
 * @author Beck Gaël
 *
 **/
class HammingDistance extends BinaryDistance {
	/**
	  * The famous hamming distance implemented in its fast mono thread scala version
	  */
	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double = {
		var dh = 0D
		for( idx <- 0 until vector1.size ) dh += vector1(idx) ^ vector2(idx)
		dh
	}
	
}