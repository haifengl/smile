package smile.math.distance

/**
 * @author Beck Gaël
 * Trait which defines binary distance
 * Here you can find a survey on binary distance that inherit from BinaryDistance : http://www.iiisci.org/journal/CV$/sci/pdfs/GS315JG.pdf
 **/
trait BinaryDistance extends Distance {
	
	type T = Int
	def distance(vector1: Array[T], vector2: Array[T]) : Double
}