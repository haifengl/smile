/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data;

import java.io.Serializable;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.stream.IntStream;

import smile.data.measure.CategoricalMeasure;
import smile.data.measure.Measure;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * A tuple is an immutable finite ordered list (sequence) of elements.
 * Allows both generic access by ordinal, which will incur boxing overhead
 * for primitives, as well as native primitive access.
 * <p>
 * It is invalid to use the native primitive interface to retrieve a value
 * that is null, instead a user must check `isNullAt` before attempting
 * to retrieve a value that might be null.
 *
 * @author Haifeng Li
 */
public interface Tuple extends Serializable {
    /**
     * Returns the schema of tuple.
     * @return the schema of tuple.
     */
    StructType schema();

    /**
     * Returns the number of elements in the Tuple.
     * @return the number of elements in the Tuple.
     */
    default int length() {
        return schema().length();
    }

    /**
     * Returns the tuple as an array of doubles.
     * @return the tuple as an array of doubles.
     */
    default double[] toArray() {
        return toArray(false, CategoricalEncoder.LEVEL);
    }

    /**
     * Return an array obtained by converting all the variables
     * in a data frame to numeric mode. Missing values/nulls will be
     * encoded as Double.NaN.
     *
     * @param bias if true, add the first column of all 1's.
     * @param encoder the categorical variable encoder.
     * @return the tuple as an array of doubles.
     */
    default double[] toArray(boolean bias, CategoricalEncoder encoder) {
        int ncol = length();
        StructType schema = schema();

        int n = bias ? 1 : 0;
        for (int i = 0; i < ncol; i++) {
            StructField field = schema.field(i);

            Measure measure = field.measure;
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure) {
                CategoricalMeasure cat = (CategoricalMeasure) measure;

                if (encoder == CategoricalEncoder.DUMMY) {
                    n += cat.size() - 1;
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    n += cat.size();
                }
            } else {
                n++;
            }
        }

        double[] array = new double[n];

        int j = 0;
        if (bias) {
            array[j++] = 1.0;
        }

        for (int i = 0; i < ncol; i++) {
            StructField field = schema.field(i);

            Measure measure = field.measure;
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure) {
                CategoricalMeasure cat = (CategoricalMeasure) measure;
                if (encoder == CategoricalEncoder.DUMMY) {
                    int k = cat.factor(getInt(i));
                    if (k > 0) array[j + k - 1] = 1.0;
                    j += cat.size() - 1;
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    int k = cat.factor(getInt(i));
                    array[j + k] = 1.0;
                    j += cat.size();
                }
            } else {
                array[j++] = getDouble(i);
            }
        }

        return array;
    }

    /**
     * Returns the value at position i. The value may be null.
     * @param i the index of field.
     * @return the field value.
     */
    default Object apply(int i) {
        return get(i);
    }

    /**
     * Returns the value by field name. The value may be null.
     * @param field the name of field.
     * @return the field value.
     */
    default Object apply(String field) {
        return get(field);
    }

    /**
     * Returns the value at position i. The value may be null.
     * @param i the index of field.
     * @return the field value.
     */
    Object get(int i);

    /**
     * Returns the value by field name. The value may be null.
     * @param field the name of field.
     * @return the field value.
     */
    default Object get(String field) {
        return get(indexOf(field));
    }

    /**
     * Checks whether the value at position i is null.
     * @param i the index of field.
     * @return true if the field value is null.
     */
    default boolean isNullAt(int i) {
        return get(i) == null;
    }

    /**
     * Checks whether the field value is null.
     * @param field the name of field.
     * @return true if the field value is null.
     */
    default boolean isNullAt(String field) {
        return get(field) == null;
    }

    /**
     * Returns true if the tuple has null/missing values.
     * @return true if the tuple has null/missing values.
     */
    default boolean hasNull() {
        return IntStream.range(0, length()).anyMatch(this::isNullAt);
    }

    /**
     * Returns the value at position i as a primitive boolean.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default boolean getBoolean(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive boolean.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default boolean getBoolean(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive byte.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default char getChar(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default char getChar(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive byte.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default byte getByte(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default byte getByte(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive short.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default short getShort(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive short.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default short getShort(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive int.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default int getInt(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive int.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default int getInt(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive long.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default long getLong(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive long.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default long getLong(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default float getFloat(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default float getFloat(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive double.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default double getDouble(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive double.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the field value.
     */
    default double getDouble(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a String object.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default String getString(int i) {
        Object obj = get(i);
        return obj == null ? null : schema().field(i).toString(obj);
    }

    /**
     * Returns the field value as a String object.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default String getString(String field) {
        return getString(indexOf(field));
    }

    /**
     * Returns the string representation of the value at position i.
     * @param i the index of field.
     * @return the string representation of field value.
     */
    default String toString(int i) {
        Object o = get(i);
        if (o == null) return "null";

        if (o instanceof String) {
            return (String) o;
        } else {
            return schema().field(i).toString(o);
        }
    }

    /**
     * Returns the string representation of the field value.
     * @param field the name of field.
     * @return the string representation of field value.
     */
    default String toString(String field) {
        return toString(indexOf(field));
    }

    /**
     * Returns the value at position i of decimal type as java.math.BigDecimal.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.math.BigDecimal getDecimal(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of decimal type as java.math.BigDecimal.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.math.BigDecimal getDecimal(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of date type as java.time.LocalDate.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.time.LocalDate getDate(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of date type as java.time.LocalDate.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.time.LocalDate getDate(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of date type as java.time.LocalTime.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.time.LocalTime getTime(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of date type as java.time.LocalTime.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.time.LocalTime getTime(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of date type as java.time.LocalDateTime.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.time.LocalDateTime getDateTime(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of date type as java.time.LocalDateTime.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default java.time.LocalDateTime getDateTime(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of NominalScale or OrdinalScale.
     * @param i the index of field.
     * @return the field scale.
     */
    default String getScale(int i) {
        int x = getInt(i);
        Measure measure = schema().field(i).measure;
        return (measure instanceof CategoricalMeasure) ? ((CategoricalMeasure) measure).toString(x) : String.valueOf(x);
    }

    /**
     * Returns the field value of NominalScale or OrdinalScale.
     *
     * @param field the name of field.
     * @throws ClassCastException when the data is not nominal or ordinal.
     * @return the field scale.
     */
    default String getScale(String field) {
        return getScale(indexOf(field));
    }

    /**
     * Returns the value at position i of array type.
     *
     * @param i the index of field.
     * @param <T> the data type of array elements.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default <T> T[] getArray(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of array type.
     *
     * @param field the name of field.
     * @param <T> the data type of array elements.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default <T> T[] getArray(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of struct type.
     *
     * @param i the index of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default Tuple getStruct(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of struct type.
     *
     * @param field the name of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default Tuple getStruct(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i.
     * For primitive types if value is null it returns 'zero value' specific for primitive
     * ie. 0 for Int - use isNullAt to ensure that value is not null
     *
     * @param i the index of field.
     * @param <T> the data type of field.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    @SuppressWarnings("unchecked")
    default <T> T getAs(int i) {
        return (T) get(i);
    }

    /**
     * Returns the value of a given fieldName.
     * For primitive types if value is null it returns 'zero value' specific for primitive
     * ie. 0 for Int - use isNullAt to ensure that value is not null
     *
     * @param field the name of field.
     * @param <T> the data type of field.
     * @throws UnsupportedOperationException when schema is not defined.
     * @throws IllegalArgumentException when fieldName do not exist.
     * @throws ClassCastException when data type does not match.
     * @return the field value.
     */
    default <T> T getAs(String field) {
        return getAs(indexOf(field));
    }

    /**
     * Returns the index of a given field name.
     *
     * @param field the name of field.
     * @throws IllegalArgumentException when a field `name` does not exist.
     * @return the field index.
     */
    default int indexOf(String field) {
        return schema().indexOf(field);
    }

    /**
     * Returns true if there are any NULL values in this tuple.
     * @return true if there are any NULL values in this tuple.
     */
    default boolean anyNull() {
        for (int i = 0; i < length(); i++) {
            if (isNullAt(i)) return true;
        }
        return false;
    }

    /**
     * Returns an object array based tuple.
     * @param row the object array.
     * @param schema the schema of tuple.
     * @return the tuple.
     */
    static Tuple of(Object[] row, StructType schema) {
        return new AbstractTuple() {
            @Override
            public Object get(int i) {
                return row[i];
            }

            @Override
            public StructType schema() {
                return schema;
            }
        };
    }

    /**
     * Returns a double array based tuple.
     * @param row the double array.
     * @param schema the schema of tuple.
     * @return the tuple.
     */
    static Tuple of(double[] row, StructType schema) {
        return new AbstractTuple() {
            @Override
            public Object get(int i) {
                return row[i];
            }

            @Override
            public double getDouble(int i) {
                return row[i];
            }

            @Override
            public StructType schema() {
                return schema;
            }
        };
    }

    /**
     * Returns the current row of a JDBC ResultSet as a tuple.
     * @param rs the JDBC ResultSet.
     * @param schema the schema of tuple.
     * @throws SQLException when JDBC operation fails.
     * @return the tuple.
     */
    static Tuple of(ResultSet rs, StructType schema) throws SQLException {
        final Object[] row = new Object[rs.getMetaData().getColumnCount()];
        for(int i = 0; i < row.length; ++i){
            row[i] = rs.getObject(i+1);

            if (row[i] instanceof Date) {
                row[i] = ((Date) row[i]).toLocalDate();
            } else if (row[i] instanceof Time) {
                row[i] = ((Time) row[i]).toLocalTime();
            } else if (row[i] instanceof Timestamp) {
                row[i] = ((Timestamp) row[i]).toLocalDateTime();
            }
        }

        return of(row, schema);
    }
}