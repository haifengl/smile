/*
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
 */

package smile.spark

import org.specs2.mutable._
import org.specs2.specification.{AfterAll, BeforeAll}
import org.apache.spark.sql.SparkSession
import smile.data.`type`.{StructField,StructType}
import smile.io.Read
import smile.util.Paths

class SmileDataFrameSpec extends Specification with BeforeAll with AfterAll{

  implicit var spark: SparkSession = _
  private val smileMushrooms = Read.arff(Paths.getTestData("weka/mushrooms.arff")).omitNullRows()

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate
  }

  "Smile DataFrame" should {
    "conversions should be idempotent but lose smile measures" in {
      val schema = new StructType(smileMushrooms.schema().fields().map(field => new StructField(field.name,field.`type`)):_*)
      schema mustEqual SparkDataFrame(SmileDataFrame(smileMushrooms)).schema()
    }

    "using object or implicit is equal" in {
      val objectSparkMushrooms = SmileDataFrame(smileMushrooms)
      val implicitSparkMushrooms = smileMushrooms.toSpark(spark)

      objectSparkMushrooms.schema mustEqual implicitSparkMushrooms.schema
    }
  }

  def afterAll(): Unit = {
    spark.stop()
  }
}

