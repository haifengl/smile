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

package smile.data.vector;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import smile.data.type.DataType;
import smile.data.type.StructField;

/**
 * An immutable generic vector.
 *
 * @author Haifeng Li
 */
public interface Vector<T> extends BaseVector<T, T, Stream<T>> {
    @Override
    Vector<T> get(int... index);

    /**
     * Returns the array of elements.
     * @return the array of elements.
     */
    T[] toArray();

    /**
     * Returns a vector of LocalDate. If the vector is of strings, it uses the default
     * ISO date formatter that parses a date without an offset, such as '2011-12-03'.
     * If the vector is of other time related objects such as Instant, java.util.Date,
     * java.sql.Timestamp, etc., do a proper conversion.
     * @return the date vector.
     */
    Vector<LocalDate> toDate();

    /**
     * Returns a vector of LocalTime. If the vector is of strings, it uses the default
     * ISO time formatter that parses a time without an offset, such as '10:15' or '10:15:30'.
     * If the vector is of other time related objects such as Instant, java.util.Date,
     * java.sql.Timestamp, etc., do a proper conversion.
     * @return the time vector.
     */
    Vector<LocalTime> toTime();

    /**
     * Returns a vector of LocalDateTime. If the vector is of strings, it uses the default
     * ISO date time formatter that parses a date without an offset, such as '2011-12-03T10:15:30'.
     * If the vector is of other time related objects such as Instant, java.util.Date,
     * java.sql.Timestamp, etc., do a proper conversion.
     * @return the datetime vector.
     */
    Vector<LocalDateTime> toDateTime();

    /**
     * Returns the distinct values.
     * @return the distinct values.
     */
    default List<T> distinct() {
        return stream().distinct().collect(Collectors.toList());
    }

    @Override
    default byte getByte(int i) {
        return ((Number) get(i)).byteValue();
    }

    @Override
    default short getShort(int i) {
        return ((Number) get(i)).shortValue();
    }

    @Override
    default int getInt(int i) {
        return ((Number) get(i)).intValue();
    }

    @Override
    default long getLong(int i) {
        return ((Number) get(i)).longValue();
    }

    @Override
    default float getFloat(int i) {
        Number x = (Number) get(i);
        return x == null ? Float.NaN : x.floatValue();
    }

    @Override
    default double getDouble(int i) {
        Number x = (Number) get(i);
        return x == null ? Double.NaN : x.doubleValue();
    }

    /**
     * Checks if the value at position i is null.
     * @param i the index.
     * @return true if the value is null.
     */
    default boolean isNullAt(int i) {
        return get(i) == null;
    }

    /**
     * Returns true if there are any NULL values in this row.
     * @return true if there are any NULL values in this row.
     */
    default boolean anyNull() {
        return stream().anyMatch(Objects::isNull);
    }

    /**
     * Returns the string representation of vector.
     * @param n the number of elements to show.
     * @return the string representation of vector.
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).map(field()::toString).collect(Collectors.joining(", ", "[", suffix));
    }

    /**
     * Creates a named vector.
     *
     * @param name the name of vector.
     * @param clazz the class of data type.
     * @param vector the data of vector.
     * @param <T> the data type of vector elements.
     * @return the vector.
     */
    static <T> Vector<T> of(String name, Class<?> clazz, T[] vector) {
        return new VectorImpl<>(name, clazz, vector);
    }

    /**
     * Creates a named vector.
     *
     * @param name the name of vector.
     * @param type the data type of vector.
     * @param vector the data of vector.
     * @param <T> the data type of vector elements.
     * @return the vector.
     */
    static <T> Vector<T> of(String name, DataType type, T[] vector) {
        return new VectorImpl<>(name, type, vector);
    }

    /** Creates a named vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @param <T> the data type of vector elements.
     * @return the vector.
     */
    static <T> Vector<T> of(StructField field, T[] vector) {
        return new VectorImpl<>(field, vector);
    }
}