/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
 
package smile.math.distance

/**
  * Hamming distance between two bit strings. Note that we treat each integer
	* in the input array as a bit string. In contrast, HammingDistance treats
	* each integer in the input array as a single value.
	*
	* @author Beck Gaël
  */
class Hamming extends Distance[Array[Int]] {
	override def d(x: Array[Int], y: Array[Int]): Double = {
		require(x.length == y.length, "Arrays have different length")
		x.zip(y).foldLeft(0) { (acc, pair) => acc + (pair._1 ^ pair._2) }
	}
}