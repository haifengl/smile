package smile.math.distance

import scala.math.pow
/**
 * @author Beck Gaël
 *
 **/
class ShapeDifference extends BinaryDistance {
	
	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a, b, c, d) = contingencyTable(vector1, vector2)
		((a + b + c + d) * (b + c) - pow(b - c, 2)) / pow(a + b + c + d,2)
	}	
}