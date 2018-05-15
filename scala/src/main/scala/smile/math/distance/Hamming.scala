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
package smile.math.distance

/**
 * @author Beck Gaël
 * Class which implement the Hamming distance which measure difference between each pair of bite.
 * https://en.wikipedia.org/wiki/Hamming_distance
 **/
class Hamming extends Distance[Array[Int]] {
	/**
	  * The famous hamming distance
	  */
	def d(vector1: Array[Int], vector2: Array[Int]) : Double = {
		var dh = 0D
		for( idx <- 0 until vector1.size ) dh += vector1(idx) ^ vector2(idx)
		dh
	}
}