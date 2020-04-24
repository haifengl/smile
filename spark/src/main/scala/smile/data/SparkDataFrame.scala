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

import scala.language.implicitConversions
import scala.collection.JavaConverters._
import smile.data.`type`.{DataType, DataTypes, StructField, StructType}
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{
  ArrayType => SparkArrayType,
  DataType => SparkDataType,
  StructField => SparkStructField,
  StructType => SparkStructType,
  _
}

case class SparkRowTuple(row: org.apache.spark.sql.Row, val schema: StructType) extends Tuple {
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
  override def getStruct(i: Int): SparkRowTuple = {
    val tuple = row.getStruct(i)
    SparkRowTuple(tuple, SparkDataTypes.smileSchema(tuple.schema))
  }
}

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

object SparkDataTypes {
  implicit def smileSchema(schema: SparkStructType): StructType = {
    DataTypes.struct(schema.map(smileField): _*)
  }

  implicit def smileField(field: SparkStructField): StructField = {
    new StructField(field.name, field.dataType)
  }

  implicit def smileType(`type`: SparkDataType): DataType = {
    `type` match {
      case BooleanType => DataTypes.BooleanType
      case ByteType => DataTypes.ByteType
      case BinaryType => DataTypes.ByteArrayType
      case ShortType => DataTypes.ShortType
      case IntegerType => DataTypes.IntegerType
      case LongType => DataTypes.LongType
      case FloatType => DataTypes.FloatType
      case DoubleType => DataTypes.DoubleType
      case _: DecimalType => DataTypes.DecimalType
      case StringType => DataTypes.StringType
      case TimestampType => DataTypes.DateTimeType
      case DateType => DataTypes.DateType
      case SparkArrayType(elementType, _) => DataTypes.array(elementType)
      case SparkStructType(fields) => DataTypes.struct(fields.map(smileField): _*)
      case MapType(keyType, valueType, _) =>
        DataTypes.array(DataTypes.struct(Seq(new StructField("key", keyType), new StructField("value", valueType)): _*))
      case ObjectType(cls) => DataTypes.`object`(cls)
      case _: NullType => DataTypes.StringType
    }
  }
}