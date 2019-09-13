/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.data.type;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * To get a specific data type, users should use singleton objects
 * and factory methods in this class.
 *
 * @author Haifeng Li
 */
public class DataTypes {
    /** Boolean data type. */
    public static smile.data.type.BooleanType BooleanType = smile.data.type.BooleanType.instance;
    /** Char data type. */
    public static smile.data.type.CharType CharType = smile.data.type.CharType.instance;
    /** Byte data type. */
    public static smile.data.type.ByteType ByteType = smile.data.type.ByteType.instance;
    /** Short data type. */
    public static smile.data.type.ShortType ShortType = smile.data.type.ShortType.instance;
    /** Integer data type. */
    public static IntegerType IntegerType = smile.data.type.IntegerType.instance;
    /** Long data type. */
    public static smile.data.type.LongType LongType = smile.data.type.LongType.instance;
    /** Float data type. */
    public static smile.data.type.FloatType FloatType = smile.data.type.FloatType.instance;
    /** Double data type. */
    public static smile.data.type.DoubleType DoubleType = smile.data.type.DoubleType.instance;
    /** Decimal data type. */
    public static smile.data.type.DecimalType DecimalType = smile.data.type.DecimalType.instance;
    /** String data type. */
    public static smile.data.type.StringType StringType = smile.data.type.StringType.instance;
    /** Date data type with ISO format. */
    public static smile.data.type.DateType DateType = smile.data.type.DateType.instance;
    /** DateTime data type with ISO format. */
    public static smile.data.type.DateTimeType DateTimeType = smile.data.type.DateTimeType.instance;
    /** Time data type with ISO format. */
    public static smile.data.type.TimeType TimeType = smile.data.type.TimeType.instance;
    /** Plain Object data type. */
    public static smile.data.type.ObjectType ObjectType = smile.data.type.ObjectType.instance;
    /** Boolean Object data type. */
    public static smile.data.type.ObjectType BooleanObjectType = smile.data.type.ObjectType.BooleanObjectType;
    /** Char Object data type. */
    public static smile.data.type.ObjectType CharObjectType = smile.data.type.ObjectType.CharObjectType;
    /** Byte Object data type. */
    public static smile.data.type.ObjectType ByteObjectType = smile.data.type.ObjectType.ByteObjectType;
    /** Short Object data type. */
    public static smile.data.type.ObjectType ShortObjectType = smile.data.type.ObjectType.ShortObjectType;
    /** Integer Object data type. */
    public static smile.data.type.ObjectType IntegerObjectType = smile.data.type.ObjectType.IntegerObjectType;
    /** Long Object data type. */
    public static smile.data.type.ObjectType LongObjectType = smile.data.type.ObjectType.LongObjectType;
    /** Float Object data type. */
    public static smile.data.type.ObjectType FloatObjectType = smile.data.type.ObjectType.FloatObjectType;
    /** Double Object data type. */
    public static smile.data.type.ObjectType DoubleObjectType = smile.data.type.ObjectType.DoubleObjectType;
    /** Boolean Array data type. */
    public static smile.data.type.ArrayType BooleanArrayType = smile.data.type.ArrayType.BooleanArrayType;
    /** Char Array data type. */
    public static smile.data.type.ArrayType CharArrayType = smile.data.type.ArrayType.CharArrayType;
    /** Byte Array data type. */
    public static smile.data.type.ArrayType ByteArrayType = smile.data.type.ArrayType.ByteArrayType;
    /** Short Array data type. */
    public static smile.data.type.ArrayType ShortArrayType = smile.data.type.ArrayType.ShortArrayType;
    /** Integer Array data type. */
    public static smile.data.type.ArrayType IntegerArrayType = smile.data.type.ArrayType.IntegerArrayType;
    /** Long Array data type. */
    public static smile.data.type.ArrayType LongArrayType = smile.data.type.ArrayType.LongArrayType;
    /** Float Array data type. */
    public static smile.data.type.ArrayType FloatArrayType = smile.data.type.ArrayType.FloatArrayType;
    /** Double Array data type. */
    public static smile.data.type.ArrayType DoubleArrayType = smile.data.type.ArrayType.DoubleArrayType;

    /** Date data type with customized format. */
    public static smile.data.type.DateType date(String pattern) {
        return new smile.data.type.DateType(pattern);
    }

    /** ime data type with customized format. */
    public static smile.data.type.TimeType time(String pattern) {
        return new smile.data.type.TimeType(pattern);
    }

    /** DateTime data type with customized format. */
    public static smile.data.type.DateTimeType datetime(String pattern) {
        return new smile.data.type.DateTimeType(pattern);
    }

    /** Creates an object data type of a given class. */
    public static DataType object(Class clazz) {
        if (clazz == Integer.class) return IntegerObjectType;
        if (clazz == Long.class) return LongObjectType;
        if (clazz == Float.class) return FloatObjectType;
        if (clazz == Double.class) return DoubleObjectType;
        if (clazz == Boolean.class) return BooleanObjectType;
        if (clazz == Character.class) return CharObjectType;
        if (clazz == Byte.class) return ByteObjectType;
        if (clazz == Short.class) return ShortObjectType;
        if (clazz == BigDecimal.class) return DecimalType;
        if (clazz == String.class) return StringType;
        if (clazz == LocalDate.class) return DateType;
        if (clazz == LocalTime.class) return TimeType;
        if (clazz == LocalDateTime.class) return DateTimeType;
        return new ObjectType(clazz);
    }

    /** Creates an array data type. */
    public static ArrayType array(DataType type) {
        if (type == IntegerType) return IntegerArrayType;
        if (type == LongType) return LongArrayType;
        if (type == FloatType) return FloatArrayType;
        if (type == DoubleType) return DoubleArrayType;
        if (type == BooleanType) return BooleanArrayType;
        if (type == CharType) return CharArrayType;
        if (type == ByteType) return ByteArrayType;
        if (type == ShortType) return ShortArrayType;
        return new ArrayType(type);
    }

    /** Creates a struct data type. */
    public static StructType struct(StructField... fields) {
        return new StructType(fields);
    }

    /** Creates a struct data type. */
    public static StructType struct(List<StructField> fields) {
        return new StructType(fields);
    }

    /** Creates a struct data type from JDBC result set meta data. */
    public static StructType struct(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        String dbms = rs.getStatement().getConnection().getMetaData().getDatabaseProductName();
        return struct(meta, dbms);
    }

    /** Creates a struct data type from JDBC result set meta data. */
    public static StructType struct(ResultSetMetaData meta, String dbms) throws SQLException {
        int ncols = meta.getColumnCount();
        StructField[] fields = new StructField[ncols];
        for (int i = 1; i <= ncols; i++) {
            String name = meta.getColumnName(i);
            DataType type = DataType.of(
                    JDBCType.valueOf(meta.getColumnTypeName(i)),
                    meta.isNullable(i) != ResultSetMetaData.columnNoNulls,
                    dbms);
            fields[i-1] = new StructField(name, type);
        }

        return struct(fields);
    }
}
