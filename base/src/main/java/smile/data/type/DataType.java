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

import java.io.Serializable;
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
        Int,
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
     * Returns the type ID enum.
     * @return the type ID enum.
     */
    ID id();

    /**
     * Returns the type name used in external catalogs.
     * DataType.of(name()) should return the same type.
     * @return the type name used in external catalogs.
     */
    String name();

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
     * Returns true if the data may be null.
     * @return true if the data may be null.
     */
    default boolean isNullable() {
        return true;
    }

    /**
     * Returns true if this is a primitive data type.
     * @return true if this is a primitive data type.
     */
    default boolean isPrimitive() {
        return switch (id()) {
            case Int, Long, Float, Double, Boolean, Char, Byte, Short -> true;
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
        return isFloating() || isIntegral() || isDecimal();
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
     * Returns true if the type is decimal.
     * @return true if the type is decimal.
     */
    default boolean isDecimal() {
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
            return a.isNullable() || b.isNullable() ? DataTypes.NullableDoubleType : DataTypes.DoubleType;
        }

        if (a.isFloat() || b.isFloat()) {
            return a.isNullable() || b.isNullable() ? DataTypes.NullableFloatType : DataTypes.FloatType;
        }

        if (a.isLong() || b.isLong()) {
            return a.isNullable() || b.isNullable() ? DataTypes.NullableLongType : DataTypes.LongType;
        }

        return a.isNullable() || b.isNullable() ? DataTypes.NullableIntType : DataTypes.IntType;
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

        if ((a.id() == DataType.ID.Int && b.id() == DataType.ID.Double) ||
            (b.id() == DataType.ID.Int && a.id() == DataType.ID.Double))
            return a.isNullable() || b.isNullable() ? DataTypes.NullableDoubleType : DataTypes.DoubleType;

        if ((a.id() == DataType.ID.Date && b.id() == DataType.ID.DateTime) ||
            (b.id() == DataType.ID.Date && a.id() == DataType.ID.DateTime))
            return DataTypes.DateTimeType;

        return DataTypes.StringType;
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
        if (Regex.INTEGER.matcher(s).matches()) return DataTypes.IntType;
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
        return switch (s) {
            case "boolean" -> DataTypes.BooleanType;
            case "char" -> DataTypes.CharType;
            case "byte" -> DataTypes.ByteType;
            case "short" -> DataTypes.ShortType;
            case "int" -> DataTypes.IntType;
            case "long" -> DataTypes.LongType;
            case "float" -> DataTypes.FloatType;
            case "double" -> DataTypes.DoubleType;
            case "Boolean" -> DataTypes.NullableBooleanType;
            case "Char" -> DataTypes.NullableCharType;
            case "Byte" -> DataTypes.NullableByteType;
            case "Short" -> DataTypes.NullableShortType;
            case "Int" -> DataTypes.NullableIntType;
            case "Long" -> DataTypes.NullableLongType;
            case "Float" -> DataTypes.NullableFloatType;
            case "Double" -> DataTypes.NullableDoubleType;
            case "Decimal" -> DataTypes.DecimalType;
            case "String" -> DataTypes.StringType;
            case "Date" -> DataTypes.DateType;
            case "DateTime" -> DataTypes.DateTimeType;
            case "Time" -> DataTypes.TimeType;
            default -> {
                if (s.startsWith("Class<") && s.endsWith(">"))
                    yield DataTypes.object(Class.forName(s.substring(6, s.length() - 1)));
                else if (s.startsWith("Array[") && s.endsWith("]"))
                    yield DataTypes.array(DataType.of(s.substring(6, s.length() - 1).trim()));
                else if (s.startsWith("Struct(") && s.endsWith(")")) {
                    String[] elements = s.substring(7, s.length() - 1).split(",");
                    StructField[] fields = new StructField[elements.length];
                    for (int i = 0; i < fields.length; i++) {
                        String[] item = elements[i].split(":");
                        fields[i] = new StructField(item[0].trim(), DataType.of(item[1].trim()));
                    }
                    yield new StructType(fields);
                } else {
                    throw new IllegalArgumentException(String.format("Unknown data type: %s", s));
                }
            }
        };
    }

    /**
     * Returns the DataType of a class.
     * @param clazz the Class object.
     * @return Smile data type.
     */
    static DataType of(Class<?> clazz) {
        if (clazz == int.class) {
            return DataTypes.IntType;
        } else if (clazz == double.class) {
            return DataTypes.DoubleType;
        } else if (clazz == long.class) {
            return DataTypes.LongType;
        } else if (clazz == float.class) {
            return DataTypes.FloatType;
        } else if (clazz == boolean.class) {
            return DataTypes.BooleanType;
        } else if (clazz == short.class) {
            return DataTypes.ShortType;
        } else if (clazz == byte.class) {
            return DataTypes.ByteType;
        } else if (clazz == char.class) {
            return DataTypes.CharType;
        } else if (clazz == Integer.class) {
            return DataTypes.NullableIntType;
        } else if (clazz == Double.class) {
            return DataTypes.NullableDoubleType;
        } else if (clazz == Long.class) {
            return DataTypes.NullableLongType;
        } else if (clazz == Float.class) {
            return DataTypes.NullableFloatType;
        } else if (clazz == Boolean.class) {
            return DataTypes.NullableBooleanType;
        } else if (clazz == Short.class) {
            return DataTypes.NullableShortType;
        } else if (clazz == Byte.class) {
            return DataTypes.NullableByteType;
        } else if (clazz == Character.class) {
            return DataTypes.NullableCharType;
        } else if (clazz.isArray()) {
            return DataTypes.array(DataType.of(clazz.getComponentType()));
        } else if (clazz.isEnum()) {
            return DataTypes.category(clazz.getEnumConstants().length);
        } else {
            return DataTypes.object(clazz);
        }
    }

    /**
     * Returns the DataType of a JDBC type.
     * @param type the JDBC data type.
     * @param nullable true if the column value may be null.
     * @param dbms The database product name.
     * @return Smile data type.
     */
    static DataType of(java.sql.JDBCType type, boolean nullable, String dbms) {
        return switch (type) {
            case BOOLEAN, BIT -> nullable ? DataTypes.NullableBooleanType : DataTypes.BooleanType;
            case TINYINT -> nullable ? DataTypes.NullableByteType : DataTypes.ByteType;
            case SMALLINT -> nullable ? DataTypes.NullableShortType : DataTypes.ShortType;
            case INTEGER -> nullable ? DataTypes.NullableIntType : DataTypes.IntType;
            case BIGINT -> nullable ? DataTypes.NullableLongType : DataTypes.LongType;
            case NUMERIC -> {
                // Numeric should be like Decimal.
                // But SQLite treats Numeric very differently.
                if ("SQLite".equals(dbms))
                    yield nullable ? DataTypes.NullableDoubleType : DataTypes.DoubleType;
                else
                    yield DataTypes.DecimalType;
            }
            case DECIMAL -> DataTypes.DecimalType;
            case REAL, FLOAT -> nullable ? DataTypes.NullableFloatType : DataTypes.FloatType;
            case DOUBLE -> nullable ? DataTypes.NullableDoubleType : DataTypes.DoubleType;
            case CHAR, NCHAR, VARCHAR, NVARCHAR, LONGVARCHAR, LONGNVARCHAR, CLOB -> DataTypes.StringType;
            case DATE -> DataTypes.DateType;
            case TIME -> DataTypes.TimeType;
            case TIMESTAMP -> DataTypes.DateTimeType;
            case BINARY, VARBINARY, LONGVARBINARY, BLOB -> DataTypes.ByteArrayType;
            default ->
                throw new UnsupportedOperationException(String.format("Unsupported JDBCType: %s", type));
        };
    }
}
