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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Date data type.
 *
 * @author Haifeng Li
 */
public class DateType implements DataType {
    /** Default instance. */
    static DateType instance = new DateType();

    /** Date format pattern. */
    private String pattern;
    /** Date formatter. */
    private DateTimeFormatter formatter;

    /**
     * Constructor with the ISO date formatter that formats
     * or parses a date without an offset, such as '2011-12-03'.
     */
    DateType() {
        pattern = "uuuu-MM-dd";
        formatter = DateTimeFormatter.ISO_LOCAL_DATE;
    }

    /**
     * Constructor.
     * @param pattern Patterns for formatting and parsing. Patterns are
     *                based on a simple sequence of letters and symbols.
     *                For example, "d MMM uuuu" will format 2011-12-03
     *                as '3 Dec 2011'.
     */
    public DateType(String pattern) {
        this.pattern = pattern;
        formatter = DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public String name() {
        return String.format("Date[%s]", pattern);
    }

    @Override
    public ID id() {
        return ID.Date;
    }

    @Override
    public String toString() {
        return "Date";
    }

    @Override
    public String toString(Object o) {
        return formatter.format((LocalDate) o);
    }

    @Override
    public LocalDate valueOf(String s) {
        return LocalDate.parse(s, formatter);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DateType;
    }}
