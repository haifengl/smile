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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.type;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Time data type.
 *
 * @author Haifeng Li
 */
public class TimeType implements DataType {
    /** Default instance. */
    static final TimeType instance = new TimeType();

    /**
     * Constructor with the ISO date formatter that formats
     * or parses a date without an offset, such as '2011-12-03'.
     */
    TimeType() {
    }

    @Override
    public String name() {
        return "Time";
    }

    @Override
    public ID id() {
        return ID.Time;
    }

    @Override
    public String toString() {
        return "Time";
    }

    @Override
    public String toString(Object o) {
        return switch (o) {
            case LocalTime d -> DateTimeFormatter.ISO_LOCAL_TIME.format(d);
            case OffsetTime d -> DateTimeFormatter.ISO_OFFSET_TIME.format(d);
            default -> o.toString();
        };
    }

    @Override
    public LocalTime valueOf(String s) {
        return LocalTime.parse(s, DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TimeType;
    }
}
