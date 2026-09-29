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
package smile.feature.extraction

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.data.DataFrame
import smile.data.Tuple
import smile.data.type.DataTypes
import smile.data.type.StructField
import smile.data.type.StructType
import smile.datasets.USArrests
import smile.datasets.WeatherNominal
import smile.math.MathEx
import smile.math.kernel.LinearKernel
import smile.util.function.TimeFunction

class ExtractionTest {

    @Test
    fun testPCA() {
        val arrests = USArrests()
        val data = arrests.data().drop(0)
        val x = arrests.x()

        // DataFrame variant
        val pcaDf = pca(data)
        assertEquals(4, pcaDf.loadings().nrow())
        assertEquals(4, pcaDf.loadings().ncol())
        assertTrue(pcaDf.varianceProportion().get(0) > 0.90)

        // Correlation variant
        val pcaCor = pca(data, cor = true)
        assertNotNull(pcaCor)

        // With explicit columns on full dataframe (including state name)
        val pcaCols = pca(arrests.data(), false, "Murder", "Assault", "UrbanPop", "Rape")
        assertEquals(4, pcaCols.loadings().nrow())

        // Raw array variant
        val pcaArray = pca(x)
        assertEquals(4, pcaArray.loadings().nrow())
        assertEquals(4, pcaArray.loadings().ncol())
        assertEquals(pcaDf.varianceProportion().get(0), pcaArray.varianceProportion().get(0), 1E-6)

        // Projection
        val projected = pcaArray.apply(x[0])
        assertNotNull(projected)
        assertEquals(pcaArray.projection.nrow(), projected.size)

        val pca2 = pcaArray.getProjection(2)
        assertEquals(2, pca2.apply(x[0]).size)
    }

    @Test
    fun testPPCA() {
        val arrests = USArrests()
        val data = arrests.data().drop(0)
        val x = arrests.x()

        // DataFrame variant
        val ppcaDf = ppca(data, 2)
        assertEquals(4, ppcaDf.loadings().nrow())
        assertEquals(2, ppcaDf.loadings().ncol())
        assertTrue(ppcaDf.variance() > 0.0)

        // With explicit columns
        val ppcaCols = ppca(arrests.data(), 2, "Murder", "Assault", "UrbanPop", "Rape")
        assertEquals(2, ppcaCols.loadings().ncol())

        // Raw array variant
        val ppcaArray = ppca(x, 2)
        assertEquals(4, ppcaArray.loadings().nrow())
        assertEquals(2, ppcaArray.loadings().ncol())
        assertEquals(ppcaDf.variance(), ppcaArray.variance(), 1E-6)
    }

    @Test
    fun testKPCA() {
        val schema = StructType(
            StructField("noise", DataTypes.DoubleType),
            StructField("x1", DataTypes.DoubleType),
            StructField("x2", DataTypes.DoubleType)
        )
        val data = DataFrame.of(schema, listOf(
            Tuple.of(schema, arrayOf(9.0, 1.0, 2.0)),
            Tuple.of(schema, arrayOf(8.0, 2.0, 1.0)),
            Tuple.of(schema, arrayOf(7.0, -1.0, -2.0)),
            Tuple.of(schema, arrayOf(6.0, -2.0, -1.0))
        ))

        val kpcaModel = kpca(data, LinearKernel(), 2, 0.0001, "x1", "x2")
        val transformed = kpcaModel.apply(data)
        assertEquals(2, transformed.ncol())
        assertEquals(4, transformed.nrow())
        assertEquals("KPCA1", transformed.schema().field(0).name())
        assertEquals("KPCA2", transformed.schema().field(1).name())
    }

    @Test
    fun testGHA() {
        val arrests = USArrests()
        val x = arrests.x()
        val mu = MathEx.colMeans(x)
        val centered = Array(x.size) { i ->
            DoubleArray(x[i].size) { j -> x[i][j] - mu[j] }
        }

        // With dimension and small constant learning rate
        val gha1 = gha(centered, 2, 0.000001)
        assertEquals(2, gha1.projection.nrow())
        assertEquals(4, gha1.projection.ncol())

        // With dimension and linear learning rate
        val gha2 = gha(centered, 2, TimeFunction.linear(0.00001, 100000.0, 0.000001))
        assertEquals(2, gha2.projection.nrow())
        assertEquals(4, gha2.projection.ncol())

        // With explicit initial weight matrix
        val w = Array(2) { DoubleArray(4) { 0.01 } }
        val gha3 = gha(centered, w, 0.000001)
        assertEquals(2, gha3.projection.nrow())
        assertEquals(4, gha3.projection.ncol())
    }

    @Test
    fun testRandomProjection() {
        val rp = randomProjection(10, 3, sparse = false)
        assertEquals(3, rp.projection.nrow())
        assertEquals(10, rp.projection.ncol())

        val projected = rp.apply(DoubleArray(10) { 1.0 })
        assertEquals(3, projected.size)

        val srp = randomProjection(10, 3, sparse = true)
        assertEquals(3, srp.projection.nrow())
        assertEquals(10, srp.projection.ncol())
    }

    @Test
    fun testBinaryEncoder() {
        val weather = WeatherNominal()
        val data = weather.data()

        val encoder = binaryEncoder(data.schema(), "outlook", "temperature", "humidity", "windy")
        val encoded = encoder.apply(data)
        assertEquals(14, encoded.size)
        assertEquals(4, encoded[0].size)

        // DataFrame extension
        val dfEncoder = data.binaryEncoder("outlook", "temperature", "humidity", "windy")
        val dfEncoded = dfEncoder.apply(data)
        assertArrayEquals(encoded[0], dfEncoded[0])
    }

    @Test
    fun testSparseEncoder() {
        val weather = WeatherNominal()
        val data = weather.data()

        val encoder = sparseEncoder(data.schema(), "outlook", "temperature", "humidity", "windy")
        val encoded = encoder.apply(data)
        assertEquals(14, encoded.size)
        assertTrue(encoded[0].size() > 0)

        // DataFrame extension
        val dfEncoder = data.sparseEncoder("outlook", "temperature", "humidity", "windy")
        val dfEncoded = dfEncoder.apply(data)
        assertEquals(encoded[0].size(), dfEncoded[0].size())
    }

    @Test
    fun testHashEncoder() {
        val hasher = hashEncoder(100) { it.split(" ").toTypedArray() }
        val result = hasher.apply("quick brown fox jumps over the lazy dog")
        assertNotNull(result)
        assertTrue(result.size() > 0)
    }

    @Test
    fun testBagOfWords() {
        val words = arrayOf("quick", "brown", "fox", "lazy", "dog")
        val bow = bagOfWords(words, binary = true) { it.split(" ").toTypedArray() }
        val vector = bow.apply("quick brown fox")
        assertEquals(5, vector.size)
        assertEquals(1, vector[0]) // quick
        assertEquals(1, vector[1]) // brown
        assertEquals(1, vector[2]) // fox
        assertEquals(0, vector[3]) // lazy

        // DataFrame variant
        val schema = StructType(StructField("text", DataTypes.StringType))
        val df = DataFrame.of(schema, listOf(
            Tuple.of(schema, arrayOf("quick brown fox")),
            Tuple.of(schema, arrayOf("lazy dog"))
        ))
        val bowDf = bagOfWords(df, 3, "text") { it.split(" ").toTypedArray() }
        val transformed = bowDf.apply(df)
        assertEquals(3, transformed.ncol())
        assertEquals(2, transformed.nrow())
    }
}
