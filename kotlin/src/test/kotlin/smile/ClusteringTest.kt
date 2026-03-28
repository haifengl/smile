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

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import smile.clustering.CentroidClustering
import smile.clustering.KModes
import smile.datasets.USPS
import smile.datasets.WeatherNominal
import smile.math.MathEx
import smile.validation.metric.AdjustedRandIndex
import smile.validation.metric.RandIndex

/**
 *
 * @author Haifeng Li
 */
class ClusteringTest {

    @Test
    fun testHClust() {
        println("Hierarchical Clustering")
        val usps = USPS()
        val x = usps.x()
        val y = usps.y()

        val model = hclust(x, "complete")
        val label = model.partition(10)
        val r = RandIndex.of(y, label)
        val r2 = AdjustedRandIndex.of(y, label)
        println(String.format(
            "CompleteLinkage rand index = %.2f%%, adjusted rand index = %.2f%%",
            100.0 * r,
            100.0 * r2
        ))
        assertEquals(0.8346, r, 1E-4)
        assertEquals(0.2930, r2, 1E-4)
    }

    @Test
    fun testKModes() {
        println("KModes")
        MathEx.setSeed(19650218) // to get repeatable results.
        val weather = WeatherNominal()
        val data = weather.level()
        val y = weather.y()

        val n = data.size
        val d = data[0].size
        val x = Array<IntArray>(n) { IntArray(d) }
        for (i in 0..<n) {
            for (j in 0..<d) {
                x[i][j] = data[i][j].toInt()
            }
        }

        val model = kmodes(x, 2, 10)
        println(model)

        val r = RandIndex.of(y, model.group)
        val r2 = AdjustedRandIndex.of(y, model.group)
        println(String.format(
            "Training rand index = %.2f%%, adjusted rand index = %.2f%%",
            100.0 * r,
            100.0 * r2
        ))
        assertEquals(0.5055, r, 1E-4)
        assertEquals(0.0116, r2, 1E-4)
    }
}
