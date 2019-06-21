/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
 
package smile.math.distance

/** Pattern difference distance.
	*
	* <h2>References</h2>
	*  - Seung-Seok Choi, et al. A Survey of Binary Similarity and Distance Measures.
	*    http://www.iiisci.org/journal/CV$/sci/pdfs/GS315JG.pdf
	*
	* @author Beck Gaël
  */
class PatternDifference extends Distance[Array[Int]] {
	override def d(x: Array[Int], y: Array[Int]): Double = {
		val (a, b, c, d) = contingency(x, y)
		(4D * b * c) / math.pow(a + b + c + d, 2)
	}
}