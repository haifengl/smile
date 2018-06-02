/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.math

/** Distance helper functions.
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