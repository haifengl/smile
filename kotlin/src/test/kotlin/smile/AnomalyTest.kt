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
package smile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.anomaly.isolationForest
import smile.anomaly.lof
import smile.anomaly.ocsvm
import smile.math.MathEx
import smile.math.distance.EuclideanDistance
import smile.math.kernel.GaussianKernel

class AnomalyTest {

    @Test
    fun testIsolationForest() {
        MathEx.setSeed(19650218)
        val data = Array(100) { DoubleArray(4) { MathEx.random() } }
        val iforest = isolationForest(data, trees = 50)
        val scores = iforest.score(data)
        assertEquals(100, scores.size)
        for (s in scores) {
            assertTrue(s in 0.0..1.0)
        }
    }

    @Test
    fun testLOF() {
        MathEx.setSeed(19650218)
        val inliers = Array(50) { doubleArrayOf(MathEx.random(), MathEx.random()) }
        val outlier = arrayOf(doubleArrayOf(10.0, 10.0))
        val data = inliers + outlier

        val model = lof(data, k = 10)
        val scores = model.score(data)
        assertEquals(51, scores.size)
        assertTrue(scores.last() > 1.5, "Outlier score should be > 1.5")

        val metricModel = lof(data, EuclideanDistance(), k = 10)
        val metricScores = metricModel.score(data)
        assertTrue(metricScores.last() > 1.5, "Outlier score with metric should be > 1.5")
    }

    @Test
    fun testOCSVM() {
        MathEx.setSeed(19650218)
        val data = Array(50) { doubleArrayOf(MathEx.random(), MathEx.random()) }
        val kernel = GaussianKernel(1.0)
        val model = ocsvm(data, kernel, nu = 0.1)
        val scores = model.score(data)
        assertEquals(50, scores.size)
    }
}
