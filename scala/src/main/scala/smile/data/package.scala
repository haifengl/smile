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

package smile

import smile.math.MathEx

import scala.language.implicitConversions
import scala.collection.JavaConverters._

/** Data manipulation functions.
  *
  * @author Haifeng Li
  */
package object data {

  implicit def pimpDataset(data: DataFrame): DataFrameOps = DataFrameOps(data)

  def summary(x: Array[Double]): Unit = {
    println("min\t\tq1\t\tmedian\t\tmean\t\tq3\t\tmax")
    val min = MathEx.min(x)
    val q1 = MathEx.q1(x)
    val median = MathEx.median(x)
    val mean = MathEx.mean(x)
    val q3 = MathEx.q3(x)
    val max = MathEx.max(x)
    println(f"$min%-10.4f\t$q1%-10.4f\t$median%-10.4f\t$mean%-10.4f\t$q3%-10.4f\t$max%-10.4f")
  }
}
