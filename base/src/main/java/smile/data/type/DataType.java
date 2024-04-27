/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.type;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import smile.util.Regex;
import smile.util.Strings;

/**
 * The interface of data types.
 *
 * @author Haifeng Li
 */
public interface DataType extends Serializable {
    /**
     * Data type ID.
     */
    enum ID {
        /** Boolean type ID. */
        Boolean,
        /** Byte type ID. */
        Byte,
        /** Char type ID. */
        Char,
        /** Short type ID. */
        Short,
        /** Integer type ID. */
        Integer,
        /** Long type ID. */
        Long,
        /** Float type ID. */
        Float,
        /** Double type ID. */
        Double,
        /** Decimal type ID. */
        Decimal,
        /** String type ID. */
        String,
        /** Date type ID. */
        Date,
        /** Time type ID. */
        Time,
        /** DateTime type ID. */
        DateTime,
        /** Object type ID. */
        Object,
        /** Array type ID. */
        Array,
        /** Struct type ID. */
        Struct
    }

    /**
     * Returns the type name used in external catalogs.
     * DataType.of(name()) should return the same type.
     * @return the type name used in external catalogs.
     */
    String name();

    /**
     * Returns the type ID enum.
     * @return the type ID enum.
     */
    ID id();

    /**
     * Returns the value from its string representation.
     * @param s the string representation of a value of this type.
     * @return the value.
     */
    Object valueOf(String s);

    /**
     * Returns the string representation of a value of the type.
     * @param o the value.
     * @return the string representation
     */
    default String toString(Object o) {
        // no check the type of o.
        return o == null ? "null" : o.toString();
    }

    /**
     * Returns true if this is a primitive data type.
     * @return true if this is a primitive data type.
     */
    default boolean isPrimitive() {
        return switch (id()) {
            case Integer, Long, Float, Double, Boolean, Char, Byte, Short -> true;
            default -> false;
        };
    }

    /**
     * Returns true if the type is float or double.
     * @return true if the type is float or double.
     */
    default boolean isFloating() {
        return isFloat() || isDouble();
    }

    /**
     * Returns true if the type is int, long, short or byte.
     * @return true if the type is int, long, short or byte.
     */
    default boolean isIntegral() {
        return isInt() || isLong() || isShort() || isByte();
    }

    /**
     * Returns true if the type is numeric (integral or floating).
     * @return true if the type is numeric (integral or floating).
     */
    default boolean isNumeric() {
        return isFloating() || isIntegral();
    }

    /**
     * Returns true if the type is boolean or Boolean.
     * @return true if the type is boolean or Boolean.
     */
    default boolean isBoolean() {
        return false;
    }

    /**
     * Returns true if the type is char or Char.
     * @return true if the type is char or Char.
     */
    default boolean isChar() {
        return false;
    }

    /**
     * Returns true if the type is byte or Byte.
     * @return true if the type is byte or Byte.
     */
    default boolean isByte() {
        return false;
    }

    /**
     * Returns true if the type is short or Short.
     * @return true if the type is short or Short.
     */
    default boolean isShort() {
        return false;
    }

    /**
     * Returns true if the type is int or Integer.
     * @return true if the type is int or Integer.
     */
    default boolean isInt() {
        return false;
    }

    /**
     * Returns true if the type is long or Long.
     * @return true if the type is long or Long.
     */
    default boolean isLong() {
        return false;
    }

    /**
     * Returns true if the type is float or Float.
     * @return true if the type is float or Float.
     */
    default boolean isFloat() {
        return false;
    }

    /**
     * Returns true if the type is double or Double.
     * @return true if the type is double or Double.
     */
    default boolean isDouble() {
        return false;
    }

    /**
     * Returns true if the type is String.
     * @return true if the type is String.
     */
    default boolean isString() { return false; }

    /**
     * Returns true if the type is ObjectType.
     * @return true if the type is ObjectType.
     */
    default boolean isObject() {
        return false;
    }

    /**
     * Returns the boxed data type if this is a primitive type.
     * Otherwise, return this type.
     * @return the boxed data type.
     */
    default DataType boxed() {
        return switch (id()) {
            case Boolean -> DataTypes.BooleanObjectType;
            case Char -> DataTypes.CharObjectType;
            case Byte -> DataTypes.ByteObjectType;
            case Short -> DataTypes.ShortObjectType;
            case Integer -> DataTypes.IntegerObjectType;
            case Long -> DataTypes.LongObjectType;
            case Float -> DataTypes.FloatObjectType;
            case Double -> DataTypes.DoubleObjectType;
            default -> this;
        };
    }

    /**
     * Returns the unboxed data type if this is a boxed primitive type.
     * Otherwise, return this type.
     * @return the unboxed data type.
     */
    default DataType unboxed() {
        if (isObject()) {
            if (isBoolean()) return DataTypes.BooleanType;
            if (isChar()) return DataTypes.CharType;
            if (isByte()) return DataTypes.ByteType;
            if (isShort()) return DataTypes.ShortType;
            if (isInt()) return DataTypes.IntegerType;
            if (isLong()) return DataTypes.LongType;
            if (isFloat()) return DataTypes.FloatType;
            if (isDouble()) return DataTypes.DoubleType;
        }

        return this;
    }

    /**
     * Infers the type of string.
     * @param s the string value.
     * @return the inferred data type of string value.
     */
    static DataType infer(String s) {
        if (Strings.isNullOrEmpty(s)) return null;
        if (Regex.DATETIME.matcher(s).matches()) return DataTypes.DateTimeType;
        if (Regex.DATE.matcher(s).matches()) return DataTypes.DateType;
        if (Regex.TIME.matcher(s).matches()) return DataTypes.TimeType;
        if (Regex.INTEGER.matcher(s).matches()) return DataTypes.IntegerType;
        if (Regex.LONG.matcher(s).matches()) return DataTypes.LongType;
        if (Regex.DOUBLE.matcher(s).matches()) return DataTypes.DoubleType;
        if (Regex.BOOLEAN.matcher(s).matches()) return DataTypes.BooleanType;
        return DataTypes.StringType;
    }

    /**
     * Returns a DataType from its string representation.
     * @param s the string representation of data type.
     * @throws ClassNotFoundException when fails to load a class.
     * @return the data type.
     */
    static DataType of(String s) throws ClassNotFoundException {
        switch (s) {
            case "boolean": return DataTypes.BooleanType;
            case "char": return DataTypes.CharType;
            case "byte": return DataTypes.ByteType;
            case "short": return DataTypes.ShortType;
            case "int": return DataTypes.IntegerType;
            case "long": return DataTypes.LongType;
            case "float": return DataTypes.FloatType;
            case "double": return DataTypes.DoubleType;
            case "Decimal": return DataTypes.DecimalType;
            case "String": return DataTypes.StringType;
            case "Date": return DataTypes.DateType;
            case "DateTime": return DataTypes.DateTimeType;
            case "Time": return DataTypes.TimeType;
            default:
                if (s.startsWith("Date[") && s.endsWith("]"))
                    return DataTypes.date(s.substring(5, s.length() - 1));
                else if (s.startsWith("DateTime[") && s.endsWith("]"))
                    return DataTypes.datetime(s.substring(9, s.length() - 1));
                else if (s.startsWith("Time[") && s.endsWith("]"))
                    return DataTypes.datetime(s.substring(5, s.length() - 1));
                else if (s.startsWith("Object[") && s.endsWith("]"))
                    return DataTypes.object(Class.forName(s.substring(7, s.length() - 1)));
                else if (s.startsWith("Array[") && s.endsWith("]"))
                    return DataTypes.array(DataType.of(s.substring(6, s.length() - 1).trim()));
                else if (s.startsWith("Struct[") && s.endsWith("]")) {
                    String[] elements = s.substring(7, s.length() - 1).split(",");
                    StructField[] fields = new StructField[elements.length];
                    for (int i = 0; i < fields.length; i++) {
                        String[] item = elements[i].split(":");
                        fields[i] = new StructField(item[0].trim(), DataType.of(item[1].trim()));
                    }
                    return DataTypes.struct(fields);
                }
        }
        throw new IllegalArgumentException(String.format("Unknown data type: %s", s));
    }

    /**
     * Returns the DataType of a class.
     * @param clazz the Class object.
     * @return Smile data type.
     */
    static DataType of(Class<?> clazz) {
        if (clazz == int.class)
            return DataTypes.IntegerType;
        else if (clazz == double.class)
            return DataTypes.DoubleType;
        else if (clazz == long.class)
            return DataTypes.LongType;
        else if (clazz == float.class)
            return DataTypes.FloatType;
        else if (clazz == boolean.class)
            return DataTypes.BooleanType;
        else if (clazz == short.class)
            return DataTypes.ShortType;
        else if (clazz == byte.class)
            return DataTypes.ByteType;
        else if (clazz == char.class)
            return DataTypes.CharType;
        else if (clazz == Integer.class)
            return DataTypes.IntegerObjectType;
        else if (clazz == Double.class)
            return DataTypes.DoubleObjectType;
        else if (clazz == Long.class)
            return DataTypes.LongObjectType;
        else if (clazz == Float.class)
            return DataTypes.FloatObjectType;
        else if (clazz == Boolean.class)
            return DataTypes.BooleanObjectType;
        else if (clazz == Short.class)
            return DataTypes.ShortObjectType;
        else if (clazz == Byte.class)
            return DataTypes.ByteObjectType;
        else if (clazz == Character.class)
            return DataTypes.CharObjectType;
        else if (clazz == String.class)
            return DataTypes.StringType;
        else if (clazz == BigDecimal.class)
            return DataTypes.DecimalType;
        else if (clazz == LocalDate.class)
            return DataTypes.DateType;
        else if (clazz == LocalDateTime.class)
            return DataTypes.DateTimeType;
        else if (clazz == LocalTime.class)
            return DataTypes.TimeType;
        else if (clazz.isArray())
            return DataTypes.array(DataType.of(clazz.getComponentType()));
        else if (clazz.isEnum()) {
            int levels = clazz.getEnumConstants().length;
            if (levels < Byte.MAX_VALUE + 1) {
                return DataTypes.ByteType;
            } else if (levels < Short.MAX_VALUE + 1) {
                return DataTypes.ShortType;
            } else {
                return DataTypes.IntegerType;
            }
        }
        else
            return DataTypes.object(clazz);
    }

    /**
     * Returns the DataType of a JDBC type.
     * @param type the JDBC data type.
     * @param nullable true if the column value may be null.
     * @param dbms The database product name.
     * @return Smile data type.
     */
    static DataType of(java.sql.JDBCType type, boolean nullable, String dbms) {
        switch (type) {
            case BOOLEAN:
            case BIT:
                return nullable ? DataTypes.BooleanObjectType : DataTypes.BooleanType;
            case TINYINT:
                return nullable ? DataTypes.ByteObjectType : DataTypes.ByteType;
            case SMALLINT:
                return nullable ? DataTypes.ShortObjectType : DataTypes.ShortType;
            case INTEGER:
                return nullable ? DataTypes.IntegerObjectType : DataTypes.IntegerType;
            case BIGINT:
                return nullable ? DataTypes.LongObjectType : DataTypes.LongType;
            case NUMERIC:
                // Numeric should be like Decimal.
                // But SQLite treats Numeric very differently.
                if ("SQLite".equals(dbms))
                    return nullable ? DataTypes.DoubleObjectType : DataTypes.DoubleType;
                else
                    return DataTypes.DecimalType;
            case DECIMAL:
                return DataTypes.DecimalType;
            case REAL:
            case FLOAT:
                return nullable ? DataTypes.FloatObjectType : DataTypes.FloatType;
            case DOUBLE:
                return nullable ? DataTypes.DoubleObjectType : DataTypes.DoubleType;
            case CHAR:
            case NCHAR:
            case VARCHAR:
            case NVARCHAR:
            case LONGVARCHAR:
            case LONGNVARCHAR:
            case CLOB:
                return DataTypes.StringType;
            case DATE:
                return DataTypes.DateType;
            case TIME:
                return DataTypes.TimeType;
            case TIMESTAMP:
                return DataTypes.DateTimeType;
            case BINARY:
            case VARBINARY:
            case LONGVARBINARY:
            case BLOB:
                return DataTypes.ByteArrayType;
            default:
                throw new UnsupportedOperationException(String.format("Unsupported JDBCType: %s", type));
        }
    }

    /**
     * Type promotion when apply to expressions. First, all byte, short,
     * and char values are promoted to int. Then, if one operand is a long,
     * the whole expression is promoted to long. If one operand is a float,
     * the entire expression is promoted to float. If any of the operands
     * is double, the result is double.
     * @param a the data type.
     * @param b the data type.
     * @return the promoted type.
     */
    static DataType prompt(DataType a, DataType b) {
        if (!a.isInt() && !a.isLong() && !a.isFloat() && !a.isDouble())
            throw new IllegalArgumentException(String.format("Invalid data type for type promotion: %s", a));

        if (!b.isInt() && !b.isLong() && !b.isFloat() && !b.isDouble())
            throw new IllegalArgumentException(String.format("Invalid data type for type promotion: %s", b));

        if (a.isDouble() || b.isDouble()) {
            if (a.isObject() || b.isObject())
                return DataTypes.DoubleObjectType;
            else
                return DataTypes.DoubleType;
        }

        if (a.isFloat() || b.isFloat()) {
            if (a.isObject() || b.isObject())
                return DataTypes.FloatObjectType;
            else
                return DataTypes.FloatType;
        }

        if (a.isLong() || b.isLong()) {
            if (a.isObject() || b.isObject())
                return DataTypes.LongObjectType;
            else
                return DataTypes.LongType;
        }

        if (a.isObject() || b.isObject())
            return DataTypes.IntegerObjectType;
        else
            return DataTypes.IntegerType;
    }

    /**
     * Returns the common type. This method is intended to be
     * used for inferring the schema from text files.
     * This is NOT a general implementation of type coercion.
     * @param a the data type.
     * @param b the data type.
     * @return the common type.
     */
    static DataType coerce(DataType a, DataType b) {
        if (a == null) return b;
        if (b == null) return a;

        if (a.id() == b.id()) return a;

        if ((a.id() == DataType.ID.Integer && b.id() == DataType.ID.Double) ||
            (b.id() == DataType.ID.Integer && a.id() == DataType.ID.Double))
            return DataTypes.DoubleType;

        if ((a.id() == DataType.ID.Date && b.id() == DataType.ID.DateTime) ||
            (b.id() == DataType.ID.Date && a.id() == DataType.ID.DateTime))
            return DataTypes.DateTimeType;

        return DataTypes.StringType;
    }

    /**
     * Returns true if the given type is of int, short, byte, char,
     * either primitive or boxed.
     * @param t the data type.
     * @return true if the given type is of int.
     */
    static boolean isInt(DataType t) {
        return switch (t.id()) {
            case Integer, Short, Byte -> true;
            case Object -> {
                Class<?> clazz = ((ObjectType) t).getObjectClass();
                yield clazz == Integer.class || clazz == Short.class || clazz == Byte.class;
            }
            default -> false;
        };
    }

    /**
     * Returns true if the given type is of long,
     * either primitive or boxed.
     * @param t the data type.
     * @return true if the given type is of long.
     */
    static boolean isLong(DataType t) {
        return (t.id() == ID.Long) ||
               (t.id() == ID.Object && ((ObjectType) t).getObjectClass() == Long.class);
    }

    /**
     * Returns true if the given type is of float,
     * either primitive or boxed.
     * @param t the data type.
     * @return true if the given type is of float.
     */
    static boolean isFloat(DataType t) {
        return (t.id () == ID.Float) ||
               (t.id() == ID.Object && ((ObjectType) t).getObjectClass() == Float.class);
    }

    /**
     * Returns true if the given type is of double,
     * either primitive or boxed.
     * @param t the data type.
     * @return true if the given type is of double.
     */
    static boolean isDouble(DataType t) {
        return (t.id() == ID.Double) ||
               (t.id() == ID.Object && ((ObjectType) t).getObjectClass() == Double.class);
    }
}
