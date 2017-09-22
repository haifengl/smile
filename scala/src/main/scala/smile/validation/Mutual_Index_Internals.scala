/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.validation

import scala.math.{max, log, sqrt}

/**
 * @author Beck Gaël
 */

 /**
  * Computte the mutual information
  **/
abstract class MutualInformationInternal extends ClusterMeasure
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

	def mutualInformationInternal(x: Array[Int], y:Array[Int]) =
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

		val ai = new Array[Double](maxX + 1)
		val bj = new Array[Double](maxY + 1)

		for( m <- maxOneIndices ) for( l <- maxTwoIndices ) ai(m) += count(m)(l)
		for( m <- maxTwoIndices ) for( l <- maxOneIndices ) bj(m) += count(l)(m)


		val nN = ai.reduce(_ + _)
		// Entropy for input data
		var hu = 0D
		ai.foreach( v => { val c = v / nN; if( c > 0 ) hu -= c * log(c) } )
		// Entropy for true labeled data
		var hv = 0D
		bj.foreach( v => { val c = v / nN; if( c > 0) hv -= c * log(c) } ) 

		var huStrichV = 0D
		for( i <- maxOneIndices ) for( j <- maxTwoIndices ) if( count(i)(j) > 0 ) huStrichV -= count(i)(j) / nN * log( (count(i)(j)) / bj(j) )
	    maxOneIndices.foreach( i => maxTwoIndices.foreach( j => if( count(i)(j) > 0 ) huStrichV -= count(i)(j) / nN * log( (count(i)(j)) / bj(j) ) ) )

		val mi = hu - huStrichV
		(mi, hu, hv)
	}
}