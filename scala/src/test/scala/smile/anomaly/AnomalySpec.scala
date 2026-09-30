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
package smile.anomaly

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import smile.math.MathEx
import smile.math.distance.EuclideanDistance
import smile.math.kernel.GaussianKernel

class AnomalySpec extends AnyWordSpec with Matchers {

  "anomaly shims" should {
    "fit Isolation Forest" in {
      MathEx.setSeed(19650218)
      val data = Array.ofDim[Double](100, 4)
      for (i <- data.indices) {
        for (j <- 0 until 4) {
          data(i)(j) = MathEx.random()
        }
      }

      val iforest = isolationforest(data, trees = 50)
      val scores = iforest.score(data)
      scores.length must be (100)
      for (s <- scores) {
        s must be >= 0.0
        s must be <= 1.0
      }
    }

    "fit LOF" in {
      MathEx.setSeed(19650218)
      val inliers = Array.fill(50)(Array(MathEx.random(), MathEx.random()))
      val outlier = Array(Array(10.0, 10.0))
      val data = inliers ++ outlier

      val model = lof(data, k = 10)
      val scores = model.score(data)
      scores.length must be (51)

      // The outlier at (10, 10) should have a significantly higher LOF score
      val outlierScore = scores.last
      outlierScore must be > 1.5

      // Generic metric LOF
      val modelMetric = lof(data, new EuclideanDistance(), k = 10)
      val metricScores = modelMetric.score(data)
      metricScores.last must be > 1.5
    }

    "fit One-Class SVM" in {
      MathEx.setSeed(19650218)
      val data = Array.fill(50)(Array(MathEx.random(), MathEx.random()))
      val kernel = new GaussianKernel(1.0)
      val model = ocsvm(data, kernel, nu = 0.1)
      val scores = model.score(data)
      scores.length must be (50)
    }
  }
}
