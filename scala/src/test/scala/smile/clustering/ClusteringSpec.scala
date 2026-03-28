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
package smile.clustering

import org.specs2.mutable.*
import smile.datasets.{USPS, WeatherNominal}
import smile.math.MathEx
import smile.validation.metric.{AdjustedRandIndex, RandIndex}

/**
  *
  * @author Haifeng Li
  */
class ClusteringSpec extends Specification {

  "clustering" should {
    "Hierarchical Clustering" in {
      val usps = new USPS()
      val x = usps.x()
      val y = usps.y()

      val model = hclust(x, "complete")
      val label = model.partition(10)
      val r = RandIndex.of(y, label)
      val r2 = AdjustedRandIndex.of(y, label)
      println(f"CompleteLinkage rand index = ${100 * r}%.2f%%, adjusted rand index = ${100 * r2}%.2f%%")
      r must beCloseTo(0.8346, 1E-4)
      r2 must beCloseTo(0.2930, 1E-4)
    }

    "KModes" in {
      MathEx.setSeed(19650218) // to get repeatable results.
      val weather = new WeatherNominal()
      val data = weather.level()
      val y = weather.y()

      val n = data.length
      val d = data(0).length
      val x = Array.ofDim[Int](n, d)
      for (i <- 0 until n) {
        for (j <- 0 until d) {
          x(i)(j) = data(i)(j).toInt
        }
      }

      val model = kmodes(x, 2, 10)
      println(model)

      val r = RandIndex.of(y, model.group)
      val r2 = AdjustedRandIndex.of(y, model.group)
      println(f"CompleteLinkage rand index = ${100 * r}%.2f%%, adjusted rand index = ${100 * r2}%.2f%%")
      r must beCloseTo(0.5055, 1E-4)
      r2 must beCloseTo(0.0116, 1E-4)
    }
  }
}
