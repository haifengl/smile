package smile.math.distance

import scala.math.pow

/**
 * @author Beck Gaël
 * Pattern difference distance class extract from this survey : http://www.iiisci.org/journal/CV$/sci/pdfs/GS315JG.pdf
 **/
class PatternDifference extends Distance[Array[Int]] {
	def d(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a,b,c,d) = BinaryUtils.contingencyTable(vector1, vector2)
		(4D * b * c) / pow(a + b + c + d, 2)
	}
}