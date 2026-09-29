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
package smile.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.data.type.DataTypes
import smile.data.type.StructField
import smile.data.type.StructType
import smile.data.vector.IntVector
import smile.data.vector.StringVector
import smile.datasets.WeatherNominal
import smile.io.Paths
import smile.read

class DataTest {

    private fun loadWeather(): DataFrame {
        return WeatherNominal().data()
    }

    @Test
    fun testSummary() {
        val ints = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        summary(ints)

        val doubles = doubleArrayOf(1.0, 2.5, 3.5, 4.0, 5.5, 6.0, 7.5, 8.0, 9.5, 10.0)
        summary(doubles)
    }

    @Test
    fun testFunctionalExtensions() {
        val df = loadWeather()
        assertEquals(14, df.size())
        assertEquals(5, df.ncol())

        // select / drop / of with IntRange
        val selected = df.select(0..2)
        assertEquals(3, selected.ncol())
        assertEquals(14, selected.size())

        val dropped = df.drop(0..1)
        assertEquals(3, dropped.ncol())

        val sliced = df.of(0 until 5)
        assertEquals(5, sliced.size())
        assertEquals(5, sliced.ncol())

        // of with step
        val stepSliced = df.of(0 until 10 step 2)
        assertEquals(5, stepSliced.size())

        // filter
        val yesDf = df.filter { it.getString("play") == "yes" }
        assertEquals(9, yesDf.size())

        // partition
        val (yes, no) = df.partition { it.getString("play") == "yes" }
        assertEquals(9, yes.size())
        assertEquals(5, no.size())

        // groupBy
        val byOutlook = df.groupBy { it.getString("outlook") }
        assertEquals(3, byOutlook.size)
        assertTrue(byOutlook.containsKey("sunny"))
        assertTrue(byOutlook.containsKey("overcast"))
        assertTrue(byOutlook.containsKey("rainy"))
        assertEquals(5, byOutlook["sunny"]!!.size())
        assertEquals(4, byOutlook["overcast"]!!.size())
        assertEquals(5, byOutlook["rainy"]!!.size())

        // find, exists, forall
        val firstYes = df.find { it.getString("play") == "yes" }
        assertNotNull(firstYes)
        assertEquals("yes", firstYes!!.getString("play"))

        assertTrue(df.exists { it.getString("outlook") == "overcast" })
        assertFalse(df.exists { it.getString("outlook") == "snowy" })

        assertTrue(df.forall { it.length() == 5 })
        assertFalse(df.forall { it.getString("play") == "yes" })

        // map
        val playList = df.map { it.getString("play") }
        assertEquals(14, playList.size)
        assertEquals(9, playList.count { it == "yes" })
    }

    @Test
    fun testDataFrameIndexingOperators() {
        val df = loadWeather()

        // df["column"]
        val outlook = df["outlook"]
        assertEquals(14, outlook.size())
        assertEquals("outlook", outlook.name())

        // df["col1", "col2"]
        val sub = df["outlook", "play"]
        assertEquals(2, sub.ncol())
        assertEquals(14, sub.size())
        assertEquals("outlook", sub.schema().field(0).name())
        assertEquals("play", sub.schema().field(1).name())

        // df[range]
        val top5 = df[0 until 5]
        assertEquals(5, top5.size())

        // df[i, "colName"]
        val cell = df[0, "outlook"]
        assertEquals(0.toByte(), cell)
        assertEquals("sunny", df.getString(0, 0))

        // df[i, "colName"] = value
        df[0, "outlook"] = 1.toByte()
        assertEquals(1.toByte(), df[0, "outlook"])
        df[0, "outlook"] = 0.toByte() // restore
    }

    @Test
    fun testDataFrameInvokeOperators() {
        val df = loadWeather()

        // df(i)
        val row0 = df(0)
        assertEquals("sunny", row0.getString("outlook"))

        // df("col")
        val playCol = df("play")
        assertEquals(14, playCol.size())

        // df("col1", "col2")
        val sub = df("temperature", "humidity")
        assertEquals(2, sub.ncol())

        // df(i, j)
        val v00 = df(0, 0)
        assertEquals(0.toByte(), v00)

        // df(i, "col")
        val v0play = df(0, "play")
        assertEquals(1.toByte(), v0play) // "no" is level 1 in binary nominal
        assertEquals("no", df.get(0).getString("play"))

        // df(range)
        val rows = df(0 until 3)
        assertEquals(3, rows.size())

        // df { predicate }
        val filtered = df { it.getString("outlook") == "overcast" }
        assertEquals(4, filtered.size())
    }

    @Test
    fun testTupleOperators() {
        val df = loadWeather()
        val tuple = df[0]

        // tuple[i] and tuple["field"] return raw cell values (Byte level for nominal)
        assertEquals(0.toByte(), tuple[0])
        assertEquals(0.toByte(), tuple["outlook"])
        assertEquals(1.toByte(), tuple["play"])
        assertEquals("sunny", tuple.getString(0))
        assertEquals("sunny", tuple.getString("outlook"))
        assertEquals("no", tuple.getString("play"))

        // tuple(i) and tuple("field")
        assertEquals(0.toByte(), tuple(0))
        assertEquals(1.toByte(), tuple("play"))

        // in operator
        assertTrue("outlook" in tuple)
        assertTrue("temperature" in tuple)
        assertFalse("nonexistent" in tuple)

        // Destructuring
        val (f0, f1, f2, f3, f4) = tuple
        assertEquals(0.toByte(), f0)
        assertEquals(0.toByte(), f1)
        assertEquals(0.toByte(), f2)
        assertEquals(1.toByte(), f3)
        assertEquals(1.toByte(), f4)
        assertEquals("FALSE", tuple.getString(3))
    }

    @Test
    fun testContainmentOperators() {
        val df = loadWeather()
        assertTrue("outlook" in df)
        assertTrue("temperature" in df)
        assertTrue("humidity" in df)
        assertTrue("windy" in df)
        assertTrue("play" in df)
        assertFalse("nonexistent" in df)
    }

    @Test
    fun testArithmeticOperators() {
        val df = loadWeather()

        // df1 + df2
        val doubled = df + df
        assertEquals(28, doubled.size())
        assertEquals(5, doubled.ncol())

        // df + column
        val extraCol = IntVector("index_col", IntArray(14) { it })
        val withExtra = df + extraCol
        assertEquals(6, withExtra.ncol())
        assertEquals(14, withExtra.size())
        assertEquals(5, df.ncol()) // original unchanged

        // df - column
        val minusOne = df - "windy"
        assertEquals(4, minusOne.ncol())
        assertFalse("windy" in minusOne)
        assertTrue("windy" in df) // original unchanged

        // df - collection
        val minusTwo = df - listOf("humidity", "windy")
        assertEquals(3, minusTwo.ncol())
        assertFalse("humidity" in minusTwo)
        assertFalse("windy" in minusTwo)

        // df - array
        val minusArray = df - arrayOf("outlook", "play")
        assertEquals(3, minusArray.ncol())
        assertFalse("outlook" in minusArray)
        assertFalse("play" in minusArray)
    }

    @Test
    fun testToJSON() {
        val df = loadWeather()
        val tuple = df[0]

        val tupleJson = tuple.toJSON()
        assertTrue(tupleJson.startsWith("{"))
        assertTrue(tupleJson.endsWith("}"))
        assertTrue(tupleJson.contains("\"outlook\": \"sunny\""))
        assertTrue(tupleJson.contains("\"play\": \"no\""))

        val dfJson = df.toJSON()
        assertTrue(dfJson.startsWith("[\n"))
        assertTrue(dfJson.endsWith("\n]"))
        assertTrue(dfJson.contains("\"outlook\": \"sunny\""))
    }
}
