package smile.data

import org.apache.spark.sql.{Encoder, Encoders, SparkSession}
import org.specs2.mutable._
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.data.`type`.{DataTypes, StructField}

case class Person(name:String,age:Int,friends:Array[String])

class SparkDataFrameSpec extends Specification with BeforeAll with AfterAll{

  var spark:SparkSession = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate
  }

  "Spark DataFrame" should {
    "convert to smile DataFrame for simple libSVM format" in {

      val sparkMushrooms = spark.read.format("libsvm").load("spark/src/test/resources/mushrooms.svm")

      val smileMushrooms = SparkDataFrame(sparkMushrooms)

      val smileSchema =DataTypes.struct(Seq(new StructField("label",DataTypes.DoubleType),new StructField("features", DataTypes.DoubleArrayType)):_*)

      smileMushrooms.schema() mustEqual smileSchema
    }

    "convert to smile DataFrame for typed datasets" in {

      val person1 = Person("smith",25,Array.empty)
      val person2 = Person("emma",28,Array(person1.name))

      implicit val personEncoder: Encoder[Person] = Encoders.product[Person]

      val sparkPersons = spark.createDataset(Seq(person1,person2))

      val smilePersons = SparkDataFrame(sparkPersons.toDF())

      val smileSchema = DataTypes.struct(Seq(new StructField("name",DataTypes.StringType),
                                            new StructField("age", DataTypes.IntegerType),
                        new StructField("friends", DataTypes.array(DataTypes.StringType))):_*)

      smilePersons.schema() mustEqual smileSchema

    }

    "converted DataFrame should have the same data" in {

      val person1 = Person("smith",25,Array.empty)
      val person2 = Person("emma",28,Array(person1.name))

      implicit val personEncoder: Encoder[Person] = Encoders.product[Person]

      val sparkPersons = spark.createDataset(Seq(person1,person2))

      val smilePersons = SparkDataFrame(sparkPersons.toDF())

      val names = Set("smith","emma")

      names must contain(smilePersons("name")(0)) and (names must contain(smilePersons("name")(1)))

    }

    "using object or implicit is equal" in {

      val sparkMushrooms = spark.read.format("libsvm").load("spark/src/test/resources/mushrooms.svm")

      val objectSmileMushrooms = SparkDataFrame(sparkMushrooms)
      val implicitSmileMushrooms = sparkMushrooms.toSmileDF

      objectSmileMushrooms.schema() mustEqual implicitSmileMushrooms.schema()
    }

  }

  def afterAll(): Unit = {
    spark.stop()
  }


}

