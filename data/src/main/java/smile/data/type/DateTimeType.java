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

package smile.data.type;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DateTime data type.
 *
 * @author Haifeng Li
 */
public class DateTimeType implements DataType {
    /** Default instance. */
    static DateTimeType instance = new DateTimeType();

    /** Date format pattern. */
    private String pattern;
    /** Date formatter. */
    private DateTimeFormatter formatter;

    /**
     * Constructor with the ISO date time formatter that formats
     * or parses a date without an offset, such as '2011-12-03T10:15:30'.
     */
    DateTimeType() {
        // This is only an approximation.
        // ISO_LOCAL_DATE_TIME cannot be fully encoded by a pattern string.
        pattern = "uuuu-MM-dd'T'HH:mm[:ss]";
        formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    }

    /**
     * Constructor.
     * @param pattern Patterns for formatting and parsing. Patterns are
     *                based on a simple sequence of letters and symbols.
     *                For example, "d MMM uuuu" will format 2011-12-03
     *                as '3 Dec 2011'.
     */
    public DateTimeType(String pattern) {
        this.pattern = pattern;
        formatter = DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public String name() {
        return String.format("DateTime[%s]", pattern);
    }

    @Override
    public ID id() {
        return ID.DateTime;
    }

    @Override
    public String toString() {
        return "DateTime";
    }

    @Override
    public String toString(Object o) {
        return formatter.format((LocalDateTime) o);
    }

    @Override
    public LocalDateTime valueOf(String s) {
        return LocalDateTime.parse(s, formatter);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DateTimeType;
    }}
