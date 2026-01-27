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
package smile.data.type;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;

/**
 * To get a specific data type, users should use singleton objects
 * and factory methods in this class.
 *
 * @author Haifeng Li
 */
public interface DataTypes {
    /** Boolean data type. */
    smile.data.type.BooleanType BooleanType = new smile.data.type.BooleanType(false);
    /** Char data type. */
    smile.data.type.CharType CharType = new smile.data.type.CharType(false);
    /** Byte data type. */
    smile.data.type.ByteType ByteType = new smile.data.type.ByteType(false);
    /** Short data type. */
    smile.data.type.ShortType ShortType = new smile.data.type.ShortType(false);
    /** Integer data type. */
    smile.data.type.IntType IntType = new smile.data.type.IntType(false);
    /** Long data type. */
    smile.data.type.LongType LongType = new smile.data.type.LongType(false);
    /** Float data type. */
    smile.data.type.FloatType FloatType = new smile.data.type.FloatType(false);
    /** Double data type. */
    smile.data.type.DoubleType DoubleType = new smile.data.type.DoubleType(false);
    /** Nullable boolean data type. */
    smile.data.type.BooleanType NullableBooleanType = new smile.data.type.BooleanType(true);
    /** Nullable char data type. */
    smile.data.type.CharType NullableCharType = new smile.data.type.CharType(true);
    /** Nullable byte data type. */
    smile.data.type.ByteType NullableByteType = new smile.data.type.ByteType(true);
    /** Nullable short data type. */
    smile.data.type.ShortType NullableShortType = new smile.data.type.ShortType(true);
    /** Nullable integer data type. */
    smile.data.type.IntType NullableIntType = new smile.data.type.IntType(true);
    /** Nullable long data type. */
    smile.data.type.LongType NullableLongType = new smile.data.type.LongType(true);
    /** Nullable float data type. */
    smile.data.type.FloatType NullableFloatType = new smile.data.type.FloatType(true);
    /** Nullable double data type. */
    smile.data.type.DoubleType NullableDoubleType = new smile.data.type.DoubleType(true);
    /** Decimal data type. */
    smile.data.type.DecimalType DecimalType = smile.data.type.DecimalType.instance;
    /** String data type. */
    smile.data.type.StringType StringType = smile.data.type.StringType.instance;
    /** Date data type with ISO format. */
    smile.data.type.DateType DateType = smile.data.type.DateType.instance;
    /** DateTime data type with ISO format. */
    smile.data.type.DateTimeType DateTimeType = smile.data.type.DateTimeType.instance;
    /** Time data type with ISO format. */
    smile.data.type.TimeType TimeType = smile.data.type.TimeType.instance;
    /** Plain Object data type. */
    smile.data.type.ObjectType ObjectType = smile.data.type.ObjectType.instance;
    /** Boolean Array data type. */
    smile.data.type.ArrayType BooleanArrayType = smile.data.type.ArrayType.BooleanArrayType;
    /** Char Array data type. */
    smile.data.type.ArrayType CharArrayType = smile.data.type.ArrayType.CharArrayType;
    /** Byte Array data type. */
    smile.data.type.ArrayType ByteArrayType = smile.data.type.ArrayType.ByteArrayType;
    /** Short Array data type. */
    smile.data.type.ArrayType ShortArrayType = smile.data.type.ArrayType.ShortArrayType;
    /** Integer Array data type. */
    smile.data.type.ArrayType IntArrayType = smile.data.type.ArrayType.IntArrayType;
    /** Long Array data type. */
    smile.data.type.ArrayType LongArrayType = smile.data.type.ArrayType.LongArrayType;
    /** Float Array data type. */
    smile.data.type.ArrayType FloatArrayType = smile.data.type.ArrayType.FloatArrayType;
    /** Double Array data type. */
    smile.data.type.ArrayType DoubleArrayType = smile.data.type.ArrayType.DoubleArrayType;

    /**
     * Returns an object data type of given class.
     * @param clazz the object class.
     * @return the object data type.
     */
    static DataType object(Class<?> clazz) {
        if (clazz == BigDecimal.class) return DecimalType;
        if (clazz == String.class) return StringType;
        if (clazz == LocalDate.class) return DateType;
        if (clazz == LocalTime.class || clazz == OffsetTime.class) return TimeType;
        if (clazz == Timestamp.class || clazz == Instant.class || clazz == LocalDateTime.class || clazz == ZonedDateTime.class) return DateTimeType;
        return new ObjectType(clazz);
    }

    /**
     * Returns a data type of categorical variable.
     * @param levels the number of categorical measurement levels.
     * @return the categorical data type.
     */
    static DataType category(int levels) {
        if (levels <= Byte.MAX_VALUE + 1) {
            return DataTypes.ByteType;
        } else if (levels <= Short.MAX_VALUE + 1) {
            return DataTypes.ShortType;
        } else {
            return DataTypes.IntType;
        }
    }

    /**
     * Returns an array data type.
     * @param type the data type of array elements.
     * @return the array data type.
     */
    static ArrayType array(DataType type) {
        if (type == IntType) return IntArrayType;
        if (type == LongType) return LongArrayType;
        if (type == FloatType) return FloatArrayType;
        if (type == DoubleType) return DoubleArrayType;
        if (type == BooleanType) return BooleanArrayType;
        if (type == CharType) return CharArrayType;
        if (type == ByteType) return ByteArrayType;
        if (type == ShortType) return ShortArrayType;
        return new ArrayType(type);
    }
}
