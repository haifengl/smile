package smile.math.distance

import scala.math.pow

/**
 * @author Beck Gaël
 *
 **/
class MeanMahanttan extends BinaryDistance {
	
	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a,b,c,d) = BinaryUtils.contingencyTable(vector1, vector2)
		(b + c).toDouble / (a + b + c + d)
	}
	
}