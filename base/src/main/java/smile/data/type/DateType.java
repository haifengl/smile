/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
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
    static final DateType instance = new DateType();

    /**
     * Constructor with the ISO date formatter that formats
     * or parses a date without an offset, such as '2011-12-03'.
     */
    DateType() {
    }

    @Override
    public String name() {
        return "Date";
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
        if (o instanceof LocalDate d) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(d);
        }
        return o.toString();
    }

    @Override
    public LocalDate valueOf(String s) {
        return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DateType;
    }
}
