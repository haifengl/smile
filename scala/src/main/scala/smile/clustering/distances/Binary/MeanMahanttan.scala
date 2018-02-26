package smile.clustering.distances

import scala.math.pow

class MeanMahanttan extends BinaryDistance {
	
	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double =
	{
		val (a,b,c,d) = contingencyTable(vector1, vector2)
		(b + c).toDouble / (a + b + c + d)
	}
	
}