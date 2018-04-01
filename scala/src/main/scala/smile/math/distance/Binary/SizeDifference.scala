package smile.math.distance

import scala.math.pow

/**
 * @author Beck Gaël
 * Size Difference distance class extract from this survey : http://www.iiisci.org/journal/CV$/sci/pdfs/GS315JG.pdf
 **/
class SizeDifference extends Distance[Array[Int]] {
	def d(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a,b,c,d) = BinaryUtils.contingencyTable(vector1, vector2)
		pow(b + c, 2) / pow(a + b + c + d, 2)
	}
}