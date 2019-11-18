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
 
package smile.math

/** Distance functions.
	*
	* @author Beck Gaël
	*/
package object distance {

	/**
	  * Build the contingency matrix (a, b, c, d) where for each bite i, j of vector 1 and 2 :
	  *   - a is incremented if i = 1, j = 1
	  *   - b is incremented if i = 1, j = 0
	  *   - c is incremented if i = 0, j = 1
	  *   - d is incremented if i = 0, j = 0
	  */
	def contingency(x: Array[Int], y: Array[Int]) = {
		require(x.length == y.length, "Arrays have different length")

	  var (a,b,c,d) = (0, 0, 0, 0)

		x.zip(y).foreach {
			case (1, 1) => a += 1
			case (1, 0) => b += 1
			case (0, 1) => c += 1
			case (0, 0) => d += 1
			case (x, y) => throw new IllegalArgumentException("Invalid value ($x, $y)")
		}

	  (a, b, c, d)
	}
}