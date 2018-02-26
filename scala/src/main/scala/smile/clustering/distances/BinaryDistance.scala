package smile.clustering.distances

abstract class BinaryDistance extends Distance {
	
	type T = Int
	def distance(vector1: Array[T], vector2: Array[T]) : Double

	def contingencyTable(vector1: Array[Int], vector2: Array[Int]) = {

	  val oneByte = 1
	  val zeroByte = 0

	  var i = 0
	  var (a,b,c,d) = (0, 0, 0, 0)
	  while( i < vector1.size ) {
	  	
	    if( vector1(i) == oneByte ) {
	    	if( vector2(i) == oneByte ) a += 1
	    	else b += 1
	    }
	    else if( vector2(i) == zeroByte ) d += 1
	    else c += 1

	    i += 1
	  }
	  (a, b, c, d)
	}

	def countOccFeat(data:Array[Array[Int]]) : Array[(Int, Int)] = {
		val nbTotData = data.size
		val nbOne = data.map(_.map(_.toInt)).reduce( _.zip(_).map( x => x._1 + x._2) )
		val nbZero = nbOne.map(nbTotData - _)
		nbZero.zip(nbOne)
	}

	def genProb2Feat(nbOccFeatTab:Array[(Int, Int)], nbTotData:Int) : Array[(Double, Double)] = {
		nbOccFeatTab.map{ case (a, b) => ((a * (a - 1D)) / (nbTotData * (nbTotData - 1D)), (b * (b - 1D)) / (nbTotData * (nbTotData - 1D)))}
	}

}