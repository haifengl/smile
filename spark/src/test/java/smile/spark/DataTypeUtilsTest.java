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

import java.util.List;
import org.apache.spark.ml.linalg.VectorUDT;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.junit.jupiter.api.Test;
import smile.data.type.DataType;
import smile.data.type.StructType;

import static org.junit.jupiter.api.Assertions.*;

class DataTypeUtilsTest {

    @Test
    void toSmileTypePrimitives() {
        assertEquals(smile.data.type.DataTypes.BooleanType, DataTypeUtils.toSmileType(DataTypes.BooleanType, false));
        assertEquals(smile.data.type.DataTypes.NullableBooleanType, DataTypeUtils.toSmileType(DataTypes.BooleanType, true));

        assertEquals(smile.data.type.DataTypes.ByteType, DataTypeUtils.toSmileType(DataTypes.ByteType, false));
        assertEquals(smile.data.type.DataTypes.NullableByteType, DataTypeUtils.toSmileType(DataTypes.ByteType, true));

        assertEquals(smile.data.type.DataTypes.ShortType, DataTypeUtils.toSmileType(DataTypes.ShortType, false));
        assertEquals(smile.data.type.DataTypes.NullableShortType, DataTypeUtils.toSmileType(DataTypes.ShortType, true));

        assertEquals(smile.data.type.DataTypes.IntType, DataTypeUtils.toSmileType(DataTypes.IntegerType, false));
        assertEquals(smile.data.type.DataTypes.NullableIntType, DataTypeUtils.toSmileType(DataTypes.IntegerType, true));

        assertEquals(smile.data.type.DataTypes.LongType, DataTypeUtils.toSmileType(DataTypes.LongType, false));
        assertEquals(smile.data.type.DataTypes.NullableLongType, DataTypeUtils.toSmileType(DataTypes.LongType, true));

        assertEquals(smile.data.type.DataTypes.FloatType, DataTypeUtils.toSmileType(DataTypes.FloatType, false));
        assertEquals(smile.data.type.DataTypes.NullableFloatType, DataTypeUtils.toSmileType(DataTypes.FloatType, true));

        assertEquals(smile.data.type.DataTypes.DoubleType, DataTypeUtils.toSmileType(DataTypes.DoubleType, false));
        assertEquals(smile.data.type.DataTypes.NullableDoubleType, DataTypeUtils.toSmileType(DataTypes.DoubleType, true));

        assertEquals(smile.data.type.DataTypes.StringType, DataTypeUtils.toSmileType(DataTypes.StringType, true));
        assertEquals(smile.data.type.DataTypes.DateType, DataTypeUtils.toSmileType(DataTypes.DateType, true));
        assertEquals(smile.data.type.DataTypes.DateTimeType, DataTypeUtils.toSmileType(DataTypes.TimestampType, true));
        assertEquals(smile.data.type.DataTypes.DecimalType, DataTypeUtils.toSmileType(DataTypes.createDecimalType(10, 2), true));
        assertEquals(smile.data.type.DataTypes.ByteArrayType, DataTypeUtils.toSmileType(DataTypes.BinaryType, false));
        assertEquals(smile.data.type.DataTypes.DoubleArrayType, DataTypeUtils.toSmileType(new VectorUDT(), false));
    }

    @Test
    void toSparkTypePrimitives() {
        assertEquals(DataTypes.BooleanType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.BooleanType));
        assertEquals(DataTypes.ByteType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.ByteType));
        assertEquals(DataTypes.ShortType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.ShortType));
        assertEquals(DataTypes.IntegerType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.IntType));
        assertEquals(DataTypes.LongType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.LongType));
        assertEquals(DataTypes.FloatType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.FloatType));
        assertEquals(DataTypes.DoubleType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.DoubleType));
        assertEquals(DataTypes.StringType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.StringType));
        assertEquals(DataTypes.DateType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.DateType));
        assertEquals(DataTypes.TimestampType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.DateTimeType));
        assertEquals(DataTypes.BinaryType, DataTypeUtils.toSparkType(smile.data.type.DataTypes.ByteArrayType));
    }

    @Test
    void schemaConversion() {
        org.apache.spark.sql.types.StructType sparkSchema = new org.apache.spark.sql.types.StructType(new StructField[]{
                DataTypes.createStructField("id", DataTypes.IntegerType, false),
                DataTypes.createStructField("name", DataTypes.StringType, true),
                DataTypes.createStructField("features", new VectorUDT(), false)
        });

        StructType smileSchema = DataTypeUtils.toSmileSchema(sparkSchema);
        assertEquals(3, smileSchema.length());
        assertEquals("id", smileSchema.field(0).name());
        assertEquals(smile.data.type.DataTypes.IntType, smileSchema.field(0).dtype());
        assertEquals("name", smileSchema.field(1).name());
        assertEquals(smile.data.type.DataTypes.StringType, smileSchema.field(1).dtype());
        assertEquals("features", smileSchema.field(2).name());
        assertEquals(smile.data.type.DataTypes.DoubleArrayType, smileSchema.field(2).dtype());

        org.apache.spark.sql.types.StructType back = DataTypeUtils.toSparkSchema(smileSchema);
        assertEquals(3, back.length());
        assertEquals("id", back.fields()[0].name());
        assertEquals(DataTypes.IntegerType, back.fields()[0].dataType());
        assertEquals("name", back.fields()[1].name());
        assertEquals(DataTypes.StringType, back.fields()[1].dataType());
    }
}
