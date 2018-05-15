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