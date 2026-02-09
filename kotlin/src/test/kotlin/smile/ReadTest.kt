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

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import smile.io.Paths

/**
 *
 * @author Haifeng Li
 */
class ReadTest {
    @Test
    fun testReadArff() {
        println("arff")
        val weather = read.arff(Paths.getTestData("weka/weather.nominal.arff"))
        println(weather)
        assertEquals(14, weather.nrow())
        assertEquals(5, weather.ncol())
    }

    @Test
    fun testReadAvro() {
        println("avro")
        val df = read.avro(
            Paths.getTestData("kylo/userdata1.avro"),
            Paths.getTestData("kylo/userdata.avsc")
        )
        println(df)
        assertEquals(1000, df.nrow())
        assertEquals(13, df.ncol())
    }

    @Test
    fun testReadJson() {
        println("json")
        val df = read.json(Paths.getTestData("kylo/books.json"))
        println(df)
        assertEquals(7, df.nrow())
        assertEquals(10, df.ncol())
    }

    @Test
    fun testReadSas() {
        println("sas")
        val df = read.sas(Paths.getTestData("sas/airline.sas7bdat"))
        println(df)
        assertEquals(32, df.nrow())
        assertEquals(6, df.ncol())
    }

    @Test
    fun testReadParquet() {
        println("parquet")
        var path = Paths.getTestData("kylo/userdata1.parquet").toAbsolutePath().toString()
        // prefix slash on Windows
        if (!path.startsWith("/")) path = "/" + path
        val df = read.parquet("file://" + path)
        println(df)
        assertEquals(1000, df.nrow())
        assertEquals(13, df.ncol())
    }

    @Test
    fun testReadCsvZip() {
        println("csv zip")
        val usps = read.csv(Paths.getTestData("usps/zip.train"), header = false, delimiter = " ")
        println(usps)
        assertEquals(7291, usps.nrow())
        assertEquals(257, usps.ncol())
    }

    @Test
    fun testReadCsvGdp() {
        println("csv gdp")
        val gdp = read.csv(Paths.getTestData("regression/gdp.csv"), header = true, comment = '%')
        println(gdp)
        assertEquals(68, gdp.nrow())
        assertEquals(4, gdp.ncol())
    }
}
