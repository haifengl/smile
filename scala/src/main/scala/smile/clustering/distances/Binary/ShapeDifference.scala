package smile.clustering.distances

import scala.math.pow

class ShapeDifference extends Binary_Distance
{
	override def distance(vector1: Array[Int], vector2: Array[Int], specialArg: String = "") : Double =
	{
		val (a, b, c, d) = contingencyTable(vector1, vector2)
		((a + b + c + d) * (b + c) - pow(b - c, 2)) / pow(a + b + c + d,2)
	}	
}