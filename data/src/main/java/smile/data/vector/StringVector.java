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

package smile.data.vector;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import smile.data.measure.DiscreteMeasure;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;

/**
 * An immutable string vector.
 *
 * @author Haifeng Li
 */
public interface StringVector extends Vector<String> {

    /**
     * Returns a vector of LocalDate. This method assumes that this is a string vector and
     * uses the given date format pattern to parse strings.
     */
    default Vector<LocalDate> toDate(String pattern) {
        return toDate(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Returns a vector of LocalDate. This method assumes that this is a string vector and
     * uses the given date format pattern to parse strings.
     */
    Vector<LocalDate> toDate(DateTimeFormatter format);

    /**
     * Returns a vector of LocalTime. This method assumes that this is a string vector and
     * uses the given time format pattern to parse strings.
     */
    default Vector<LocalTime> toTime(String pattern) {
        return toTime(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Returns a vector of LocalDate. This method assumes that this is a string vector and
     * uses the given time format pattern to parse strings.
     */
    Vector<LocalTime> toTime(DateTimeFormatter format);

    /**
     * Returns a vector of LocalDateTime. This method assumes that this is a string vector and
     * uses the given date time format pattern to parse strings.
     */
    default Vector<LocalDateTime> toDateTime(String pattern) {
        return toDateTime(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Returns a vector of LocalDateTime. This method assumes that this is a string vector and
     * uses the given date time format pattern to parse strings.
     */
    Vector<LocalDateTime> toDateTime(DateTimeFormatter format);

    /**
     * Returns a nominal scale of measure based on distinct values in
     * the vector.
     */
    NominalScale nominal();

    /**
     * Converts strings to discrete measured values. Depending on how many levels
     * in the nominal scale, the type of returned vector may be byte, short
     * or integer. The missing values/nulls will be converted to -1.
     */
    BaseVector factorize(DiscreteMeasure scale);

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).collect(Collectors.joining(", ", "[", suffix));
    }

    /**
     * Creates a named string vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static StringVector of(String name, String... vector) {
        return new StringVectorImpl(name, vector);
    }

    /** Creates a named string vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     */
    static StringVector of(StructField field, String... vector) {
        return new StringVectorImpl(field, vector);
    }
}