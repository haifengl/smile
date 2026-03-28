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

import org.specs2.mutable.*
import smile.io.Paths

/**
  *
  * @author Haifeng Li
  */
class ReadSpec extends Specification {
  "read" should {
    "arff" in {
      val weather = read.arff(Paths.getTestData("weka/weather.nominal.arff"))
      println(weather)
      14 === weather.nrow
      5 === weather.ncol
    }

    "avro" in {
      val df = read.avro(
        Paths.getTestData("kylo/userdata1.avro"),
        Paths.getTestData("kylo/userdata.avsc")
      )
      println(df)
      1000 === df.nrow
      13 === df.ncol
    }

    "json" in {
      val df = read.json(Paths.getTestData("kylo/books.json"))
      println(df)
      7 === df.nrow
      10 === df.ncol
    }

    "sas" in {
      val df = read.sas(Paths.getTestData("sas/airline.sas7bdat"))
      println(df)
      32 === df.nrow
      6 === df.ncol
    }

    "parquet" in {
      var path = Paths.getTestData("kylo/userdata1.parquet").toAbsolutePath().toString()
      // prefix slash on Windows
      if (!path.startsWith("/")) path = "/" + path
      val df = read.parquet("file://" + path)
      println(df)
      1000 === df.nrow
      13 === df.ncol
    }

    "csv zip" in {
      val usps = read.csv(Paths.getTestData("usps/zip.train").toString, header = false, delimiter = " ")
      println(usps)
      7291 === usps.nrow
      257 === usps.ncol
    }

    "csv gdp" in {
      val gdp = read.csv(Paths.getTestData("regression/gdp.csv").toString, header = true, comment = '%')
      println(gdp)
      68 === gdp.nrow
      4 === gdp.ncol
    }
  }
}
