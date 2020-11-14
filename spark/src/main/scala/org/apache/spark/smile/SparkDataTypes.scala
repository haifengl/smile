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

package org.apache.spark.smile

import smile.data.`type`.{DataType, DataTypes, StructField, StructType}
import org.apache.spark.sql.types._
import org.apache.spark.ml.linalg.VectorUDT
import org.apache.spark.mllib.linalg.{VectorUDT => OldVectorUDT}
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder

/**
  * SparkDataTypes is a collection of internal helper methods to convert back and forth
  * between [[smile.data.`type`.DataType]] and [[org.apache.spark.sql.types.DataType]].
  */
object SparkDataTypes {
  /**
    * Convert a Spark schema to a smile schema
    *
    * @param schema spark schema
    * @return smile schema
    */
  def smileSchema(schema: org.apache.spark.sql.types.StructType): StructType = {
    DataTypes.struct(schema.map(smileField): _*)
  }

  /**
    * Convert a Spark field to a smile field
    *
    * @param field spark field
    * @return smile field
    */
  def smileField(field: org.apache.spark.sql.types.StructField): StructField = {
    new StructField(field.name, smileType(field.dataType))
  }

  /**
    * Convert a [[org.apache.spark.sql.types.DataType]] to a smile [[DataType]].
    * Deals with nested type or even user defined types.
    *
    * @param `type` spark datatype
    * @return smile datatype
    */
  def smileType(`type`: org.apache.spark.sql.types.DataType): DataType = {
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
      case ArrayType(elementType, _) => DataTypes.array(smileType(elementType))
      case org.apache.spark.sql.types.StructType(fields) => DataTypes.struct(fields.map(smileField): _*)
      case MapType(keyType, valueType, _) =>
        DataTypes.array(DataTypes.struct(Seq(new StructField("key", smileType(keyType)), new StructField("value", smileType(valueType))): _*))
      case ObjectType(cls) => DataTypes.`object`(cls)
      case _: NullType => DataTypes.StringType
      case _: VectorUDT => DataTypes.array(DataTypes.DoubleType)
      case _: OldVectorUDT => DataTypes.array(DataTypes.DoubleType)
      case definedType: UserDefinedType[_] => DataTypes.`object`(definedType.userClass)
    }
  }

  /**
    * Convert a smile schema to a Spark schema
    *
    * @param schema smile schema
    * @return spark schema
    */
  def sparkSchema(schema: StructType): org.apache.spark.sql.types.StructType = {
    org.apache.spark.sql.types.StructType(schema.fields().map(sparkField))
  }

  /**
    * Convert a smile field to a Spark field
    *
    * @param field smile field
    * @return spark field
    */
  def sparkField(field: StructField): org.apache.spark.sql.types.StructField = {
    //TODO: add metadata for measure
    org.apache.spark.sql.types.StructField(field.name, sparkType(field.`type`))
  }

  /**
    * Convert a smile [[DataType]] to a [[org.apache.spark.sql.types.DataType]].
    * Deals with nested type or even user defined types.
    *
    * @param `type` smile datatype
    * @return spark datatype
    */
  def sparkType(`type`: DataType): org.apache.spark.sql.types.DataType = {
    `type`.id match {
      case DataType.ID.Boolean => BooleanType
      case DataType.ID.Byte => ByteType
      case DataType.ID.Char => StringType
      case DataType.ID.Short => ShortType
      case DataType.ID.Integer => IntegerType
      case DataType.ID.Long => LongType
      case DataType.ID.Float => FloatType
      case DataType.ID.Double => DoubleType
      case DataType.ID.Decimal => org.apache.spark.sql.types.DataTypes.createDecimalType()
      case DataType.ID.String => StringType
      case DataType.ID.Date => DateType
      case DataType.ID.Time => StringType
      case DataType.ID.DateTime => TimestampType
      case DataType.ID.Object =>
        ExpressionEncoder
          .javaBean(`type`.asInstanceOf[smile.data.`type`.ObjectType].getObjectClass)
          .schema
      case DataType.ID.Array =>
        new ArrayType(
          sparkType(`type`.asInstanceOf[smile.data.`type`.ArrayType].getComponentType),false)
      case DataType.ID.Struct =>
        org.apache.spark.sql.types.StructType(
          `type`.asInstanceOf[smile.data.`type`.StructType]
            .fields()
            .map(f => org.apache.spark.sql.types.StructField(f.name, sparkType(f.`type`)))
            .toSeq)
    }
  }
}