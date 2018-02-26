package smile.clustering.distances

import scala.math.pow

class SizeDifference extends BinaryDistance
{
	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a,b,c,d) = contingencyTable(vector1, vector2)
		pow(b + c, 2) / pow(a + b + c + d, 2)
	}
}