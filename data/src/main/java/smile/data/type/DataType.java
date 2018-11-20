/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data.type;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The data type in the sense of .
 *
 * @see DiscreteMeasure
 * @see ContinuousMeasure
 *
 * @author Haifeng Li
 */
public interface DataType extends Serializable {
    /**
     * Returns the type name used in external catalogs.
     * DataType.of(name()) should returns the same type.
     */
    String name();

    /**
     * Returns the value from its string representation.
     * @param s the string representation of a value of this type.
     */
    Object valueOf(String s) throws ParseException;

    /** Returns the string representation of a value of the type. */
    default String toString(Object o) {
        // no check the type of o.
        return o.toString();
    }

    /** Returns true if this is a primitive data type. */
    default boolean isPrimitive() {
        return this == DataTypes.IntegerType ||
               this == DataTypes.LongType ||
               this == DataTypes.DoubleType ||
               this == DataTypes.FloatType ||
               this == DataTypes.BooleanType ||
               this == DataTypes.CharType ||
               this == DataTypes.ByteType ||
               this == DataTypes.ShortType;
    }

    /** Returns true if the type is int or Integer. */
    default boolean isInt() {
        return false;
    }

    /** Returns true if the type is long or Long. */
    default boolean isLong() {
        return false;
    }

    /** Returns true if the type is float or Float. */
    default boolean isFloat() {
        return false;
    }

    /** Returns true if the type is double or Double. */
    default boolean isDouble() {
        return false;
    }

    /** Returns true if the type is ObjectType. */
    default boolean isObject() {
        return false;
    }

    /** Returns a DataType from its string representation. */
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
            case "decimal": return DataTypes.DecimalType;
            case "string": return DataTypes.StringType;
            case "date": return DataTypes.DateType;
            case "datetime": return DataTypes.DateTimeType;
            case "time": return DataTypes.TimeType;
            default:
                if (s.startsWith("date[") && s.endsWith("]"))
                    return DataTypes.date(s.substring(5, s.length() - 1));
                else if (s.startsWith("datetime[") && s.endsWith("]"))
                    return DataTypes.datetime(s.substring(9, s.length() - 1));
                else if (s.startsWith("time[") && s.endsWith("]"))
                    return DataTypes.datetime(s.substring(5, s.length() - 1));
                else if (s.startsWith("object[") && s.endsWith("]"))
                    return DataTypes.object(Class.forName(s.substring(7, s.length() - 1)));
                else if (s.startsWith("array[") && s.endsWith("]"))
                    return DataTypes.array(DataType.of(s.substring(6, s.length() - 1).trim()));
                else if (s.startsWith("struct[") && s.endsWith("]")) {
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

    /** Returns the DataType of a class. */
    static DataType of(Class clazz) {
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
        else
            return DataTypes.object(clazz);
    }

    /**
     * Type promotion when apply to expressions. First, all byte, short,
     * and char values are promoted to int. Then, if one operand is a long,
     * the whole expression is promoted to long. If one operand is a float,
     * the entire expression is promoted to float. If any of the operands
     * is double, the result is double.
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
     * Returns true if the given type is of int, short, byte, char,
     * either primitive or boxed.
     */
    static boolean isInt(DataType t) {
        return t == DataTypes.IntegerType ||
               t == DataTypes.ShortType ||
               t == DataTypes.ByteType ||
               t == DataTypes.CharType ||
               t.equals(DataTypes.object(Integer.class)) ||
               t == DataTypes.object(Short.class) ||
               t == DataTypes.object(Byte.class) ||
               t == DataTypes.object(Character.class);
    }

    /**
     * Returns true if the given type is of long,
     * either primitive or boxed.
     */
    static boolean isLong(DataType t) {
        return t == DataTypes.LongType ||
               t == DataTypes.object(Long.class);
    }

    /**
     * Returns true if the given type is of float,
     * either primitive or boxed.
     */
    static boolean isFloat(DataType t) {
        return t == DataTypes.FloatType ||
               t == DataTypes.object(Float.class);
    }

    /**
     * Returns true if the given type is of double,
     * either primitive or boxed.
     */
    static boolean isDouble(DataType t) {
        return t == DataTypes.DoubleType ||
               t == DataTypes.object(Double.class);
    }
}
