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
    /** String data type. */
    public static smile.data.type.StringType StringType = smile.data.type.StringType.instance;
    /** Date data type with ISO format. */
    public static smile.data.type.DateType DateType = smile.data.type.DateType.instance;
    /** DateTime data type with ISO format. */
    public static smile.data.type.DateTimeType DateTimeType = smile.data.type.DateTimeType.instance;
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

    /** DateTime data type with customized format. */
    public static smile.data.type.DateTimeType datetime(String pattern) {
        return new smile.data.type.DateTimeType(pattern);
    }

    /** Creates an object data type of a given class. */
    public static ObjectType object(Class clazz) {
        if (clazz == Integer.class) return IntegerObjectType;
        if (clazz == Long.class) return LongObjectType;
        if (clazz == Float.class) return FloatObjectType;
        if (clazz == Double.class) return DoubleObjectType;
        if (clazz == Boolean.class) return BooleanObjectType;
        if (clazz == Character.class) return CharObjectType;
        if (clazz == Byte.class) return ByteObjectType;
        if (clazz == Short.class) return ShortObjectType;
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
}
