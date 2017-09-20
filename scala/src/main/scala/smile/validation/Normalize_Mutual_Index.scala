import scala.math.{max, log, sqrt}

/**
 * @author Beck Gaël
 */
object Externals_Indexes
{
	/**
	 * Normalize Sequences in order to prevent construction of a to big 'count' matrix
	 * Ex: [4,5,6,6] -> [0,1,2,2]
	 **/
	private def prepareList(x: Array[Int]) =
	{
		val indexedValuesMap = x.distinct.zipWithIndex.toMap
		x.map(indexedValuesMap)
	}

	private def mutualInformationInteral(x: Array[Int], y:Array[Int]) =
	{
		require( x.size == y.size )
		val n = x.size
		val xx = prepareList(x)
		val yy = prepareList(y)
		val maxX = xx.max
		val maxY = yy.max

		val maxOneIndices = (0 to maxX).toArray
		val maxTwoIndices = (0 to maxY).toArray

		val count = for( m <- maxOneIndices ) yield( for( l <- maxTwoIndices ) yield(0D) )
		for( i <- xx.indices ) count(xx(i))(yy(i)) += 1D

		val bj = new Array[Double](maxY + 1)
		val ai = new Array[Double](maxX + 1)

		for( m <- maxOneIndices ) for( l <- maxTwoIndices ) ai(m) += count(m)(l)
		for( m <- maxTwoIndices ) for( l <- maxOneIndices ) bj(m) += count(l)(m)


		val nN = ai.reduce(_ + _)
		var hu = 0D
		ai.foreach( v => { val c = v / nN; if( c > 0 ) hu -= c * log(c) } )

		var hv = 0D
		bj.foreach( v => { val c = v / nN; if( c > 0) hv -= c * log(c) } ) 

		var huStrichV = 0D
		for( i <- maxOneIndices ) for( j <- maxTwoIndices ) if( count(i)(j) > 0 ) huStrichV -= count(i)(j) / nN * log( (count(i)(j)) / bj(j) )

		val mi = hu - huStrichV
		(mi, hu, hv)
	}

	def mutualInformation(x: Array[Int], y:Array[Int]) = mutualInformationInteral(x, y)._1

	def nmi(x: Array[Int], y: Array[Int], normalization: String = "sqrt") =
	{
		val (mi, hu, hv) = mutualInformationInteral(x, y)
		val nmi = normalization match
		{
			case "sqrt" => mi / sqrt(hu * hv)
			case "max" => mi / max(hu, hv)
			case _ => mi / sqrt(hu * hv)
		}
		nmi
	}
}