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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
/**
 * Time data type.
 *
 * @author Haifeng Li
 */
public class TimeType implements DataType {
    /** Default instance. */
    static TimeType instance = new TimeType();

    /** Date format pattern. */
    private String pattern;
    /** Date formatter. */
    private DateTimeFormatter formatter;

    /**
     * Constructor with the ISO date formatter that formats
     * or parses a date without an offset, such as '2011-12-03'.
     */
    TimeType() {
        // This is only an approximation.
        // ISO_LOCAL_TIME cannot be fully encoded by a pattern string.
        pattern = "HH:mm[:ss]";
        formatter = DateTimeFormatter.ISO_LOCAL_TIME;
    }

    /**
     * Constructor.
     * @param pattern Patterns for formatting and parsing. Patterns are
     *                based on a simple sequence of letters and symbols.
     *                For example, "d MMM uuuu" will format 2011-12-03
     *                as '3 Dec 2011'.
     */
    public TimeType(String pattern) {
        this.pattern = pattern;
        formatter = DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public String name() {
        return String.format("Time[%s]", pattern);
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
        return formatter.format((LocalTime) o);
    }

    @Override
    public LocalTime valueOf(String s) throws ParseException {
        return LocalTime.parse(s, formatter);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TimeType;
    }}
