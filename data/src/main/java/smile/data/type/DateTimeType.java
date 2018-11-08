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

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/**
 * DateTime data type.
 *
 * @author Haifeng Li
 */
public class DateTimeType implements DataType {
    /** Date format pattern. */
    private String pattern;
    /** Date formatter. */
    private DateTimeFormatter formatter;

    /**
     * Constructor with the ISO date time formatter that formats
     * or parses a date without an offset, such as '2011-12-03T10:15:30'.
     */
    public DateTimeType() {
        pattern = "uuuu-MM-dd'T'HH:mm:ss";
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
        return String.format("datetime[%s]", pattern);
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public String toString(Object o) {
        return formatter.format((LocalDateTime) o);
    }

    @Override
    public LocalDateTime valueOf(String s) throws ParseException {
        return LocalDateTime.parse(s, formatter);
    }
}
