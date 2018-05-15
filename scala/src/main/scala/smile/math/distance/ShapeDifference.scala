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

import scala.math.pow
/**
 * @author Beck Gaël
 * Shape Difference distance class extract from this survey : http://www.iiisci.org/journal/CV$/sci/pdfs/GS315JG.pdf
 **/
class ShapeDifference extends Distance[Array[Int]] {
	def d(vector1: Array[Int], vector2: Array[Int]) : Double = {
		val (a, b, c, d) = BinaryUtils.contingencyTable(vector1, vector2)
		((a + b + c + d) * (b + c) - pow(b - c, 2)) / pow(a + b + c + d,2)
	}
}