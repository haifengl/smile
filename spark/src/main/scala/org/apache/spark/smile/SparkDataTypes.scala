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

object SparkDataTypes {

  def smileSchema(schema:  org.apache.spark.sql.types.StructType): StructType = {
    DataTypes.struct(schema.map(smileField): _*)
  }

  def smileField(field: org.apache.spark.sql.types.StructField): StructField = {
    new StructField(field.name, smileType(field.dataType))
  }

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

  def sparkSchema(schema: StructType): org.apache.spark.sql.types.StructType = {
    org.apache.spark.sql.types.StructType(schema.fields().map(sparkField))
  }

  def sparkField(field: StructField): org.apache.spark.sql.types.StructField = {
    //TODO: add metadata for measure
    org.apache.spark.sql.types.StructField(field.name, sparkType(field.`type`))
  }

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
          sparkType(`type`.asInstanceOf[smile.data.`type`.ArrayType].getComponentType),
          false)
      case DataType.ID.Struct =>
        org.apache.spark.sql.types.StructType(
          `type`.asInstanceOf[smile.data.`type`.StructType]
            .fields()
            .map(f => org.apache.spark.sql.types.StructField(f.name, sparkType(f.`type`)))
            .toSeq)
    }
  }

}