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

