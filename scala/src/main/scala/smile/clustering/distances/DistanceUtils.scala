package smile.clustering.distances

object DistanceUtils
{
	def chooseBinaryDistance(distanceAsStr: String): Binary_Distance =
	{
		distanceAsStr match
		{
	        case "Hamming" => new HammingDistance
	        case "MeanMahanttan" => new MeanMahanttan
	        case "PatternDifference" => new PatternDifference
	        case "ShapeDifference" => new ShapeDifference
	        case "SizeDifference" => new SizeDifference
	        case "Vari" => new Vari 
	        case _ => { println("Default choice => Hamming"); new HammingDistance }
    	}
	}
}