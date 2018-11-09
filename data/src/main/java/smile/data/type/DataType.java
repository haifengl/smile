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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The base type of DataFrame columns.
 *
 * @author Haifeng Li
 */
public interface DataType extends Serializable {
    /** Returns the string representation for the type in external catalogs. */
    String name();

    /**
     * Returns the value of a string of this attribute.
     * @param s a string value of this attribute.
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
                Pattern pattern = Pattern.compile("(date|datetime|nominal|ordinal|object|array|product)\\[([^\\[\\]]*)\\]");
                Matcher matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    String type = matcher.group(1);
                    String value = matcher.group(2);
                    switch (type) {
                        case "date": return DataTypes.date(value);
                        case "datetime": return DataTypes.datetime(value);
                        case "nominal": return DataTypes.nominal(value.split(","));
                        case "ordinal": return DataTypes.ordinal(value.split(","));
                        case "array": return DataTypes.array(value);
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
}
