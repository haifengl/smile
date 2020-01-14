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

package smile.data;

import java.io.Serializable;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.stream.IntStream;
import smile.data.measure.DiscreteMeasure;
import smile.data.measure.Measure;
import smile.data.type.StructType;

/**
 * A tuple is an immutable finite ordered list (sequence) of elements.
 * Allows both generic access by ordinal, which will incur boxing overhead
 * for primitives, as well as native primitive access.
 *
 * It is invalid to use the native primitive interface to retrieve a value
 * that is null, instead a user must check `isNullAt` before attempting
 * to retrieve a value that might be null.
 *
 * @author Haifeng Li
 */
public interface Tuple extends Serializable {
    /** Returns the schema of tuple. */
    StructType schema();

    /** Number of elements in the Tuple. */
    default int length() {
        return schema().length();
    }

    /** Returns the tuple as an array of doubles. */
    default double[] toArray() {
        double[] x = new double[length()];
        for (int i = 0; i < x.length; i++) {
            x[i] = getDouble(i);
        }
        return x;
    }

    /**
     * Returns the value at position i. The value may be null.
     */
    default Object apply(int i) {
        return get(i);
    }

    /**
     * Returns the value by field name. The value may be null.
     */
    default Object apply(String field) {
        return get(field);
    }

    /**
     * Returns the value at position i. The value may be null.
     */
    Object get(int i);

    /**
     * Returns the value by field name. The value may be null.
     */
    default Object get(String field) {
        return get(fieldIndex(field));
    }

    /** Checks whether the value at position i is null. */
    default boolean isNullAt(int i) {
        return get(i) == null;
    }

    /** Checks whether the field value is null. */
    default boolean isNullAt(String field) {
        return get(field) == null;
    }

    /** Returns true if the tuple has null/missing values. */
    default boolean hasNull() {
        return IntStream.range(0, length()).anyMatch(i -> isNullAt(i));
    }

    /**
     * Returns the value at position i as a primitive boolean.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default boolean getBoolean(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive boolean.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default boolean getBoolean(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default char getChar(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default char getChar(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default byte getByte(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default byte getByte(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive short.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default short getShort(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive short.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default short getShort(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive int.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default int getInt(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive int.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default int getInt(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive long.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default long getLong(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive long.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default long getLong(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default float getFloat(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default float getFloat(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a primitive double.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default double getDouble(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value as a primitive double.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default double getDouble(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i as a String object.
     *
     * @throws ClassCastException when data type does not match.
     */
    default String getString(int i) {
        Object obj = get(i);
        return obj == null ? null : schema().field(i).toString(obj);
    }

    /**
     * Returns the field value as a String object.
     *
     * @throws ClassCastException when data type does not match.
     */
    default String getString(String field) {
        return getString(fieldIndex(field));
    }

    /**
     * Returns the string representation of the value at position i.
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
     */
    default String toString(String field) {
        return toString(fieldIndex(field));
    }

    /**
     * Returns the value at position i of decimal type as java.math.BigDecimal.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.math.BigDecimal getDecimal(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of decimal type as java.math.BigDecimal.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.math.BigDecimal getDecimal(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of date type as java.time.LocalDate.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDate getDate(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of date type as java.time.LocalDate.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDate getDate(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of date type as java.time.LocalTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalTime getTime(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of date type as java.time.LocalTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalTime getTime(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of date type as java.time.LocalDateTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDateTime getDateTime(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of date type as java.time.LocalDateTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDateTime getDateTime(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of NominalScale or OrdinalScale.
     */
    default String getScale(int i) {
        int x = getInt(i);
        Measure measure = schema().field(i).measure;
        return (measure instanceof DiscreteMeasure) ? ((DiscreteMeasure) measure).toString(x) : String.valueOf(x);
    }

    /**
     * Returns the field value of NominalScale or OrdinalScale.
     *
     * @throws ClassCastException when the data is not nominal or ordinal.
     */
    default String getScale(String field) {
        return getScale(fieldIndex(field));
    }

    /**
     * Returns the value at position i of array type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default <T> T[] getArray(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of array type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default <T> T[] getArray(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i of struct type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default Tuple getStruct(int i) {
        return getAs(i);
    }

    /**
     * Returns the field value of struct type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default Tuple getStruct(String field) {
        return getAs(field);
    }

    /**
     * Returns the value at position i.
     * For primitive types if value is null it returns 'zero value' specific for primitive
     * ie. 0 for Int - use isNullAt to ensure that value is not null
     *
     * @throws ClassCastException when data type does not match.
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
     * @throws UnsupportedOperationException when schema is not defined.
     * @throws IllegalArgumentException when fieldName do not exist.
     * @throws ClassCastException when data type does not match.
     */
    default <T> T getAs(String fieldName) {
        return getAs(fieldIndex(fieldName));
    }

    /**
     * Returns the index of a given field name.
     *
     * @throws IllegalArgumentException when a field `name` does not exist.
     */
    default int fieldIndex(String name) {
        return schema().fieldIndex(name);
    }

    /** Returns true if there are any NULL values in this tuple. */
    default boolean anyNull() {
        for (int i = 0; i < length(); i++) {
            if (isNullAt(i)) return true;
        }
        return false;
    }

    /** Returns an object array based tuple. */
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

    /** Returns a double array based tuple. */
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

    /** Returns the current row of a JDBC ResultSet as a tuple. */
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