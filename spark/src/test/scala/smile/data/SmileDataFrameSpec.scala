/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.data

import org.apache.spark.sql.SparkSession
import org.specs2.mutable._
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.data.`type`.StructField
import smile.io.Read

class SmileDataFrameSpec extends Specification with BeforeAll with AfterAll{

  var spark:SparkSession = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate
  }

  "Smile DataFrame" should {
    "conversions should be idempotent but lose measures" in {

      val smileMushrooms = Read.arff("spark/src/test/resources/mushrooms.arff").omitNullRows()

      smileMushrooms.schema().fields().map(field => new StructField(field.name,field.`type`)) mustEqual SparkDataFrame(SmileDataFrame(smileMushrooms,spark)).schema()
    }

    "using object or implicit is equal" in {

      val smileMushrooms = Read.arff("spark/src/test/resources/mushrooms.arff").omitNullRows()

      val objectSparkMushrooms = SmileDataFrame(smileMushrooms,spark)
      val implicitSparkMushrooms = smileMushrooms.toSparkDF(spark)

      objectSparkMushrooms.schema mustEqual implicitSparkMushrooms.schema
    }

  }

  def afterAll(): Unit = {
    spark.stop()
  }


}

