/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.spark

import org.apache.spark.sql.{DataFrame, Encoder, Encoders, SparkSession}
import org.specs2.mutable.*
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.data.`type`.{DataTypes, StructField}
import smile.io.Paths

case class Person(name: String, age: Int, friends: Array[String])

class SparkDataFrameSpec extends Specification with BeforeAll with AfterAll{

  var spark: SparkSession = _
  var sparkMushrooms: DataFrame = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate()
    sparkMushrooms = spark.read.format("libsvm").load(Paths.getTestData("libsvm/mushrooms.svm").normalize().toString)
  }

  "Spark DataFrame" should {
    "convert to smile DataFrame for simple libSVM format" in {
      val smileMushrooms = SparkDataFrame(sparkMushrooms)
      val smileSchema = new smile.data.`type`.StructType(
        Seq(new StructField("label", DataTypes.NullableDoubleType),
            new StructField("features", DataTypes.DoubleArrayType))*)

      smileMushrooms.schema() mustEqual smileSchema
    }

    "convert to smile DataFrame for typed datasets" in {
      val person1 = Person("smith", 25, Array.empty)
      val person2 = Person("emma", 28, Array(person1.name))

      implicit val personEncoder: Encoder[Person] = Encoders.product[Person]

      val sparkPersons = spark.createDataset(Seq(person1,person2))
      val smilePersons = SparkDataFrame(sparkPersons.toDF())

      val smileSchema = new smile.data.`type`.StructType(
        Seq(new StructField("name", DataTypes.StringType),
            new StructField("age", DataTypes.IntType),
            new StructField("friends", DataTypes.array(DataTypes.StringType)))*)

      smilePersons.schema() mustEqual smileSchema
    }

    "converted DataFrame should have the same data" in {
      val person1 = Person("smith",25,Array.empty)
      val person2 = Person("emma",28,Array(person1.name))

      implicit val personEncoder: Encoder[Person] = Encoders.product[Person]

      val sparkPersons = spark.createDataset(Seq(person1,person2))
      val smilePersons = SparkDataFrame(sparkPersons.toDF())

      val names = Set("smith", "emma")
      names must contain(smilePersons("name")(0)) and (names must contain(smilePersons("name")(1)))
    }

    "using object or implicit is equal" in {
      val objectSmileMushrooms = SparkDataFrame(sparkMushrooms)
      val implicitSmileMushrooms = sparkMushrooms.toSmile

      objectSmileMushrooms.schema() mustEqual implicitSmileMushrooms.schema()
    }
  }

  def afterAll(): Unit = {
    spark.stop()
  }
}

