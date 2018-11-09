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
    public static smile.data.type.IntegerType IntegerType = smile.data.type.IntegerType.instance;
    /** Long data type. */
    public static smile.data.type.LongType LongType = smile.data.type.LongType.instance;
    /** Float data type. */
    public static smile.data.type.FloatType FloatType = smile.data.type.FloatType.instance;
    /** Double data type. */
    public static smile.data.type.DoubleType DoubleType = smile.data.type.DoubleType.instance;
    /** String data type. */
    public static smile.data.type.StringType StringType = smile.data.type.StringType.instance;
    /** Date data type with ISO format. */
    public static smile.data.type.DateType DateType = new smile.data.type.DateType();
    /** DateTime data type with ISO format. */
    public static smile.data.type.DateTimeType DateTimeType = new smile.data.type.DateTimeType();

    /** Date data type with customized format. */
    public static smile.data.type.DateType date(String pattern) {
        return new smile.data.type.DateType(pattern);
    }

    /** DateTime data type with customized format. */
    public static smile.data.type.DateTimeType datetime(String pattern) {
        return new smile.data.type.DateTimeType(pattern);
    }

    /** Creates a nominal data type. */
    public static NominalType nominal(String... values) {
        return new NominalType(values);
    }

    /** Creates an ordinal data type. */
    public static OrdinalType ordinal(String... values) {
        return new OrdinalType(values);
    }

    /** Creates an object data type. */
    public static ObjectType object(Class clazz) {
        return new ObjectType(clazz);
    }

    /** Creates an array data type. */
    public static ArrayType array(DataType type) {
        return new ArrayType(type);
    }

    /** Creates a struct data type. */
    public static StructType struct(StructField... fields) {
        return new StructType(fields);
    }
}
