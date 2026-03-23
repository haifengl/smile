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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DateTime data type.
 *
 * @author Haifeng Li
 */
public class DateTimeType implements DataType {
    /** Default instance. */
    static final DateTimeType instance = new DateTimeType();

    /**
     * Constructor with the ISO date time formatter that formats
     * or parses a date without an offset, such as '2011-12-03T10:15:30'.
     */
    DateTimeType() {
    }

    @Override
    public String name() {
        return "DateTime";
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
        return switch (o) {
            case Instant d -> DateTimeFormatter.ISO_INSTANT.format(d);
            case LocalDateTime d -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(d);
            case ZonedDateTime d -> DateTimeFormatter.ISO_ZONED_DATE_TIME.format(d);
            case OffsetDateTime d -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(d);
            case Timestamp d -> DateTimeFormatter.ISO_INSTANT.format(d.toInstant());
            default -> o.toString();
        };
    }

    @Override
    public LocalDateTime valueOf(String s) {
        return LocalDateTime.parse(s, DateTimeFormatter.ISO_INSTANT);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DateTimeType;
    }
}
