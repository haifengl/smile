package smile.clustering.distances

import scala.math.pow

class Vari extends Binary_Distance
{
	override def distance(vector1: Array[Int], vector2: Array[Int], specialArg: String = "") : Double =
	{
		val (a,b,c,d) = contingencyTable(vector1, vector2)
		(b + c).toDouble / (4 * (a + b + c + d))
	}
}