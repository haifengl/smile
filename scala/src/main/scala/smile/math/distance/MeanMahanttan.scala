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

/** Mean Mahantan distance. Compared to regular Manhattan distance, it normalizes
	* the distance by the sum of contingency table values.
	*
	* <h2>References</h2>
	*  - Seung-Seok Choi, et al. A Survey of Binary Similarity and Distance Measures.
	*    http://www.iiisci.org/journal/CV$/sci/pdfs/GS315JG.pdf
	*
	* @author Beck Gaël
  */
class MeanMahanttan extends Distance[Array[Int]] {
	override def d(x: Array[Int], y: Array[Int]): Double = {
		val (a, b, c, d) = contingency(x, y)
		(b + c).toDouble / (a + b + c + d)
	}
}