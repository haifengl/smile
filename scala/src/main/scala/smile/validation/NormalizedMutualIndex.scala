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
  * Computte the normalized mutual information
  **/
class NormalizedMutualInformation(normalization: String = "sqrt") extends MutualInformationInternal
{
	override def measure(x: Array[Int], y: Array[Int]) =
	{
		val (mi, hu, hv) = mutualInformationInternal(x, y)
		val nmi = normalization match
		{
			case "sqrt" => mi / sqrt(hu * hv)
			case "max" => mi / max(hu, hv)
			case _ => mi / sqrt(hu * hv)
		}
		nmi
	}
}