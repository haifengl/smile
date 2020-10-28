package smile.data

import java.util
import java.util.stream.Collectors

import org.apache.spark.smile.SparkDataTypes
import org.apache.spark.sql.{Row, SparkSession}

import scala.collection.JavaConverters._

object SmileDataFrame {
  /** Returns a distributed Spark DataFrame. */
  def apply(df: DataFrame, spark:SparkSession): org.apache.spark.sql.DataFrame = {
    val schema = SparkDataTypes.sparkSchema(df.schema)
    spark.createDataFrame(df.stream().collect(Collectors.toList()).asScala.map(tuple => SmileTupleSparkRow(tuple,schema).asInstanceOf[Row]).asJava,schema)
  }

}

case class SmileTupleSparkRow(tuple:Tuple, override val schema:org.apache.spark.sql.types.StructType) extends org.apache.spark.sql.Row {
  override def length: Int = tuple.length()
  override def get(i: Int): Any = tuple.get(i)
  override def copy(): Row = this
}
