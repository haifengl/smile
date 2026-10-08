/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.spark;

import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.BooleanType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.DoubleType;
import org.apache.spark.sql.types.FloatType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.MetadataBuilder;
import org.apache.spark.sql.types.NullType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.TimestampNTZType;
import org.apache.spark.sql.types.TimestampType;
import org.apache.spark.sql.types.UserDefinedType;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * Bidirectional conversion utilities between Apache Spark SQL data types and SMILE data types.
 *
 * @author Haifeng Li
 */
public final class DataTypeUtils {

    private DataTypeUtils() {
    }

    /**
     * Converts an Apache Spark schema to a SMILE schema.
     *
     * @param schema the Spark schema.
     * @return the SMILE schema.
     */
    public static StructType toSmileSchema(org.apache.spark.sql.types.StructType schema) {
        List<StructField> fields = new ArrayList<>(schema.length());
        for (org.apache.spark.sql.types.StructField field : schema.fields()) {
            fields.add(toSmileField(field));
        }
        return new StructType(fields);
    }

    /**
     * Converts an Apache Spark field to a SMILE field.
     *
     * @param field the Spark field.
     * @return the SMILE field.
     */
    public static StructField toSmileField(org.apache.spark.sql.types.StructField field) {
        DataType type = toSmileType(field.dataType(), field.nullable());
        return new StructField(field.name(), type);
    }

    /**
     * Converts an Apache Spark SQL data type to a SMILE data type.
     *
     * @param dtype    the Spark data type.
     * @param nullable true if nullable.
     * @return the SMILE data type.
     */
    public static DataType toSmileType(org.apache.spark.sql.types.DataType dtype, boolean nullable) {
        if (dtype instanceof BooleanType) {
            return nullable ? DataTypes.NullableBooleanType : DataTypes.BooleanType;
        } else if (dtype instanceof ByteType) {
            return nullable ? DataTypes.NullableByteType : DataTypes.ByteType;
        } else if (dtype instanceof ShortType) {
            return nullable ? DataTypes.NullableShortType : DataTypes.ShortType;
        } else if (dtype instanceof IntegerType) {
            return nullable ? DataTypes.NullableIntType : DataTypes.IntType;
        } else if (dtype instanceof LongType) {
            return nullable ? DataTypes.NullableLongType : DataTypes.LongType;
        } else if (dtype instanceof FloatType) {
            return nullable ? DataTypes.NullableFloatType : DataTypes.FloatType;
        } else if (dtype instanceof DoubleType) {
            return nullable ? DataTypes.NullableDoubleType : DataTypes.DoubleType;
        } else if (dtype instanceof BinaryType) {
            return DataTypes.ByteArrayType;
        } else if (dtype instanceof DecimalType) {
            return DataTypes.DecimalType;
        } else if (dtype instanceof StringType) {
            return DataTypes.StringType;
        } else if (dtype instanceof DateType) {
            return DataTypes.DateType;
        } else if (dtype instanceof TimestampType || dtype instanceof TimestampNTZType) {
            return DataTypes.DateTimeType;
        } else if (dtype instanceof ArrayType arr) {
            return DataTypes.array(toSmileType(arr.elementType(), arr.containsNull()));
        } else if (dtype instanceof org.apache.spark.sql.types.StructType st) {
            return toSmileSchema(st);
        } else if (dtype instanceof MapType mt) {
            StructField key = new StructField("key", toSmileType(mt.keyType(), false));
            StructField value = new StructField("value", toSmileType(mt.valueType(), mt.valueContainsNull()));
            return DataTypes.array(new StructType(List.of(key, value)));
        } else if (dtype instanceof NullType) {
            return DataTypes.StringType;
        } else if (dtype instanceof org.apache.spark.ml.linalg.VectorUDT) {
            return DataTypes.DoubleArrayType;
        } else if (dtype instanceof org.apache.spark.mllib.linalg.VectorUDT) {
            return DataTypes.DoubleArrayType;
        } else if (dtype instanceof UserDefinedType<?> udt) {
            return DataTypes.object(udt.userClass());
        }
        return DataTypes.object(Object.class);
    }

    /**
     * Converts a SMILE schema to an Apache Spark schema.
     *
     * @param schema the SMILE schema.
     * @return the Spark schema.
     */
    public static org.apache.spark.sql.types.StructType toSparkSchema(StructType schema) {
        org.apache.spark.sql.types.StructField[] fields = new org.apache.spark.sql.types.StructField[schema.length()];
        for (int i = 0; i < schema.length(); i++) {
            fields[i] = toSparkField(schema.field(i));
        }
        return new org.apache.spark.sql.types.StructType(fields);
    }

    /**
     * Converts a SMILE field to an Apache Spark field.
     *
     * @param field the SMILE field.
     * @return the Spark field.
     */
    public static org.apache.spark.sql.types.StructField toSparkField(StructField field) {
        org.apache.spark.sql.types.DataType sparkType = toSparkType(field.dtype());
        MetadataBuilder metadata = new MetadataBuilder();
        if (field.measure() != null) {
            metadata.putString("measure", field.measure().toString());
        }
        return new org.apache.spark.sql.types.StructField(
                field.name(),
                sparkType,
                field.dtype().isNullable(),
                metadata.build()
        );
    }

    /**
     * Converts a SMILE data type to an Apache Spark SQL data type.
     *
     * @param dtype the SMILE data type.
     * @return the Spark data type.
     */
    public static org.apache.spark.sql.types.DataType toSparkType(DataType dtype) {
        return switch (dtype.id()) {
            case Boolean -> org.apache.spark.sql.types.DataTypes.BooleanType;
            case Byte -> org.apache.spark.sql.types.DataTypes.ByteType;
            case Char, String, Time -> org.apache.spark.sql.types.DataTypes.StringType;
            case Short -> org.apache.spark.sql.types.DataTypes.ShortType;
            case Int -> org.apache.spark.sql.types.DataTypes.IntegerType;
            case Long -> org.apache.spark.sql.types.DataTypes.LongType;
            case Float -> org.apache.spark.sql.types.DataTypes.FloatType;
            case Double -> org.apache.spark.sql.types.DataTypes.DoubleType;
            case Decimal -> org.apache.spark.sql.types.DataTypes.createDecimalType();
            case Date -> org.apache.spark.sql.types.DataTypes.DateType;
            case DateTime -> org.apache.spark.sql.types.DataTypes.TimestampType;
            case Array -> {
                smile.data.type.ArrayType arr = (smile.data.type.ArrayType) dtype;
                if (arr.getComponentType().isByte()) {
                    yield org.apache.spark.sql.types.DataTypes.BinaryType;
                }
                yield org.apache.spark.sql.types.DataTypes.createArrayType(toSparkType(arr.getComponentType()), true);
            }
            case Struct -> toSparkSchema((StructType) dtype);
            case Object -> org.apache.spark.sql.types.DataTypes.BinaryType;
        };
    }
}
