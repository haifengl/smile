package smile.math.distance

import scala.math.pow

/**
 * @author Beck Gaël
 * Vari distance class extract from this survey : http://www.iiisci.org/journal/CV$/sci/pdfs/GS315JG.pdf
 **/
class Vari extends BinaryDistance {

	override def distance(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a,b,c,d) = BinaryUtils.contingencyTable(vector1, vector2)
		(b + c).toDouble / (4 * (a + b + c + d))
	}
	
}