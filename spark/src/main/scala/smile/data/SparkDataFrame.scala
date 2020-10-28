package smile.data

import org.apache.spark.smile.SparkDataTypes
import smile.data.`type`.StructType

import scala.collection.JavaConverters._

object SparkDataFrame {
  /** Returns a local Smile DataFrame. */
  def apply(df: org.apache.spark.sql.DataFrame): DataFrame = {
    val schema = SparkDataTypes.smileSchema(df.schema)
    DataFrame.of(
      df.collect()
        .map(row => SparkRowTuple(row, schema))
        .toList
        .asJava)
  }
}

case class SparkRowTuple(row: org.apache.spark.sql.Row, override val schema:StructType) extends Tuple {
  override def length: Int = row.size
  override def fieldIndex(name: String): Int = row.fieldIndex(name)
  override def isNullAt(i: Int): Boolean = row.isNullAt(i)
  override def get(i: Int): AnyRef = row.get(i).asInstanceOf[AnyRef]
  override def getBoolean(i: Int): Boolean = row.getBoolean(i)
  override def getByte(i: Int): Byte = row.getByte(i)
  override def getShort(i: Int): Short = row.getShort(i)
  override def getInt(i: Int): Int = row.getInt(i)
  override def getLong(i: Int): Long = row.getLong(i)
  override def getFloat(i: Int): Float = row.getFloat(i)
  override def getDouble(i: Int): Double = row.getDouble(i)
  override def getDecimal(i: Int): java.math.BigDecimal = row.getDecimal(i)
  override def getString(i: Int): String = row.getString(i)
  override def getDate(i: Int): java.time.LocalDate = row.getDate(i).toLocalDate()
  override def getDateTime(i: Int): java.time.LocalDateTime = row.getTimestamp(i).toLocalDateTime()
  override def getTime(i: Int): java.time.LocalTime = row.getTimestamp(i).toLocalDateTime().toLocalTime()
  override def getStruct(i: Int): SparkRowTuple = {
    val tuple = row.getStruct(i)
    SparkRowTuple(tuple, SparkDataTypes.smileSchema(tuple.schema))
  }
}