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
package smile.data;

/**
 * To get a specific data type, users should use singleton objects
 * and factory methods in this class.
 *
 * @author Haifeng Li
 */
public class DataTypes {
    /** Boolean data type. */
    public static smile.data.BooleanType BooleanType = smile.data.BooleanType.instance;
    /** Integer data type. */
    public static smile.data.IntegerType IntegerType = smile.data.IntegerType.instance;
    /** Long data type. */
    public static smile.data.LongType LongType = smile.data.LongType.instance;
    /** Double data type. */
    public static smile.data.DoubleType DoubleType = smile.data.DoubleType.instance;
    /** String data type. */
    public static smile.data.StringType StringType = smile.data.StringType.instance;
    /** Date data type with ISO format. */
    public static smile.data.DateType DateType = new smile.data.DateType();
    /** DateTime data type with ISO format. */
    public static smile.data.DateTimeType DateTimeType = new smile.data.DateTimeType();

    /** Date data type with customized format. */
    public static smile.data.DateType date(String pattern) {
        return new smile.data.DateType(pattern);
    }

    /** DateTime data type with customized format. */
    public static smile.data.DateTimeType datetime(String pattern) {
        return new smile.data.DateTimeType(pattern);
    }

    /** Creates a nominal data type. */
    public static NominalType nominal(String... values) {
        return new NominalType(values);
    }

    /** Creates an ordinal data type. */
    public static OrdinalType ordinal(String... values) {
        return new OrdinalType(values);
    }

    /** Creates an array data type. */
    public static ArrayType array(DataType type) {
        return new ArrayType(type);
    }

    /** Creates an array data type. */
    public static ArrayType array(String type) {
        return new ArrayType(type);
    }

    /** Creates a struct data type. */
    public static StructType struct(StructField... fields) {
        return new StructType(fields);
    }
}
