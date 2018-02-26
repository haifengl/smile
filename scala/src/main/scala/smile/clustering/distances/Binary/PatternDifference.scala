package smile.clustering.distances

import scala.math.pow

class PatternDifference extends BinaryDistance {
	
	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a,b,c,d) = contingencyTable(vector1, vector2)
		(4D * b * c) / pow(a + b + c + d, 2)
	}
	
}