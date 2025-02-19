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
package org.apache.spark.ml

import org.apache.spark.sql.types.*
import org.apache.spark.ml.linalg.VectorUDT
import org.apache.spark.mllib.linalg.VectorUDT as OldVectorUDT
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import smile.data.`type`.{DataType, DataTypes, StructField, StructType}

/**
  * A collection of convert methods between Smile DataType and SparkSQL DataType.
  */
object DataTypeOps {
  /**
    * Converts a Spark schema to a Smile schema.
    *
    * @param schema Spark schema
    * @return Smile schema
    */
  def toSmileSchema(schema: org.apache.spark.sql.types.StructType): StructType = {
    new StructType(schema.map(toSmileField)*)
  }

  /**
    * Converts a Spark field to a Smile field.
    *
    * @param field Spark field
    * @return Smile field
    */
  def toSmileField(field: org.apache.spark.sql.types.StructField): StructField = {
    new StructField(field.name, toSmileType(field.dataType, field.nullable))
  }

  /**
    * Converts a SparkSQL DataType to a Smile DataType.
    * Deals with nested type or even user defined types.
    *
    * @param dtype Spark data type
    * @return Smile data type
    */
  def toSmileType(dtype: org.apache.spark.sql.types.DataType, nullable: Boolean): DataType = {
    dtype match {
      case BooleanType => if (nullable) DataTypes.NullableBooleanType else DataTypes.BooleanType
      case ByteType => if (nullable) DataTypes.NullableByteType else DataTypes.ByteType
      case BinaryType => DataTypes.ByteArrayType
      case ShortType => if (nullable) DataTypes.NullableShortType else DataTypes.ShortType
      case IntegerType => if (nullable) DataTypes.NullableIntType else DataTypes.IntType
      case LongType => if (nullable) DataTypes.NullableLongType else DataTypes.LongType
      case FloatType => if (nullable) DataTypes.NullableFloatType else DataTypes.FloatType
      case DoubleType => if (nullable) DataTypes.NullableDoubleType else DataTypes.DoubleType
      case _: DecimalType => DataTypes.DecimalType
      case StringType => DataTypes.StringType
      case TimestampType => DataTypes.DateTimeType
      case DateType => DataTypes.DateType
      case ArrayType(elementType, nullable) => DataTypes.array(toSmileType(elementType, nullable))
      case org.apache.spark.sql.types.StructType(fields) => new StructType(fields.map(toSmileField)*)
      case MapType(keyType, valueType, nullable) => DataTypes.array(new StructType(Seq(
          new StructField("key", toSmileType(keyType, nullable)),
          new StructField("value", toSmileType(valueType, nullable)))*))
      case ObjectType(cls) => DataTypes.`object`(cls)
      case _: NullType => DataTypes.StringType
      case _: VectorUDT => DataTypes.array(DataTypes.DoubleType)
      case _: OldVectorUDT => DataTypes.array(DataTypes.DoubleType)
      case definedType: UserDefinedType[?] => DataTypes.`object`(definedType.userClass)
    }
  }

  /**
    * Converts a Smile schema to a Spark schema
    *
    * @param schema Smile schema
    * @return Spark schema
    */
  def toSparkSchema(schema: StructType): org.apache.spark.sql.types.StructType = {
    org.apache.spark.sql.types.StructType(schema.fields().stream().map(toSparkField).toList)
  }

  /**
    * Converts a Smile field to a Spark field.
    *
    * @param field Smile field
    * @return Spark field
    */
  def toSparkField(field: StructField): org.apache.spark.sql.types.StructField = {
    val sparkType = toSparkType(field.dtype)
    val nullable = field.dtype.isNullable
    val builder = new MetadataBuilder
    if (field.measure != null) {
      builder.putString("measure", field.measure.toString)
    }
    org.apache.spark.sql.types.StructField(field.name, sparkType, nullable, builder.build())
  }

  /**
    * Convert a Smile DataType to a SparkSQL DataType.
    * Deals with nested type or even user defined types.
    *
    * @param dtype Smile data type
    * @return Spark data type
    */
  def toSparkType(dtype: DataType): org.apache.spark.sql.types.DataType = {
    dtype.id match {
      case DataType.ID.Boolean => BooleanType
      case DataType.ID.Byte => ByteType
      case DataType.ID.Char => StringType
      case DataType.ID.Short => ShortType
      case DataType.ID.Int => IntegerType
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
          .javaBean(dtype.asInstanceOf[smile.data.`type`.ObjectType].getObjectClass)
          .schema
      case DataType.ID.Array =>
        new ArrayType(
          toSparkType(dtype.asInstanceOf[smile.data.`type`.ArrayType].getComponentType),false)
      case DataType.ID.Struct =>
        org.apache.spark.sql.types.StructType(
          dtype.asInstanceOf[smile.data.`type`.StructType]
            .fields().stream()
            .map(f => org.apache.spark.sql.types.StructField(f.name, toSparkType(f.dtype), f.dtype.isNullable))
            .toList)
    }
  }
}
