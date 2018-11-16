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
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The data type in the sense of .
 *
 * @see DiscreteMeasure
 * @see ContinuousMeasure
 *
 * @author Haifeng Li
 */
public interface DataType extends Serializable {
    /** Returns the string representation for the type in external catalogs. */
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

    /** Returns a DataType from its string representation. */
    static DataType of(String s) throws ClassNotFoundException {
        switch (s) {
            case "boolean": return DataTypes.BooleanType;
            case "char": return DataTypes.CharType;
            case "byte": return DataTypes.ByteType;
            case "short": return DataTypes.ShortType;
            case "integer": return DataTypes.IntegerType;
            case "long": return DataTypes.LongType;
            case "float": return DataTypes.FloatType;
            case "double": return DataTypes.DoubleType;
            case "string": return DataTypes.StringType;
            case "date": return DataTypes.DateType;
            case "datetime": return DataTypes.DateTimeType;
            default:
                Pattern pattern = Pattern.compile("(date|datetime|nominal|ordinal|object|array|struct)\\[([^\\[\\]]*)\\]");
                Matcher matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    String type = matcher.group(1);
                    String value = matcher.group(2);
                    switch (type) {
                        case "date": return DataTypes.date(value);
                        case "datetime": return DataTypes.datetime(value);
                        case "array": return DataTypes.array(DataType.of(value));
                        case "object": return DataTypes.object(Class.forName(value));
                        case "struct":
                            String[] elements = value.split(",");
                            StructField[] fields = new StructField[elements.length];
                            for (int i = 0; i < fields.length; i++) {
                                String[] f = elements[i].split(":");
                                fields[i] = new StructField(f[0], DataType.of(f[1]));
                            }
                            return DataTypes.struct(fields);
                    }
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
        else if (clazz == LocalDate.class)
            return DataTypes.DateType;
        else if (clazz == LocalDateTime.class)
            return DataTypes.DateTimeType;
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
        if (!isInt(a) && !isLong(a) && !isFloat(a) && !isDouble(a))
            throw new IllegalArgumentException(String.format("Invalid data type for type promotion: %s", a));

        if (!isInt(b) && !isLong(b) && !isFloat(b) && !isDouble(b))
            throw new IllegalArgumentException(String.format("Invalid data type for type promotion: %s", b));

        if (isDouble(a) || isDouble(b))
            return DataTypes.DoubleType;

        if (isFloat(a) || isFloat(b))
            return DataTypes.FloatType;

        if (isLong(a) || isLong(b))
            return DataTypes.LongType;

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
               t == DataTypes.object(Integer.class) ||
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
