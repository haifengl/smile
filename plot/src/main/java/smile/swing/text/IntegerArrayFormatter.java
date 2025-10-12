/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.swing.text;

import javax.swing.text.DefaultFormatter;
import java.text.ParseException;
import java.util.Arrays;

/**
 * Text formatter for integer array values.
 *
 * @author Haifeng Li
 */
public class IntegerArrayFormatter extends DefaultFormatter {
    /** Constructor. */
    public IntegerArrayFormatter() {

    }

    @Override
    public Object stringToValue(String string) throws ParseException {
        string = string.trim();
        if (string.isEmpty()) {
            throw new ParseException("Empty string", 0);
        }

        int begin = 0;
        char ch = string.charAt(0);
        if (ch == '[' || ch == '{' || ch == '<') {
            begin = 1;
        }

        int end = string.length();
        ch = string.charAt(end - 1);
        if (ch == ']' || ch == '}' || ch == '>') {
            end -= 1;
        }

        string = string.substring(begin, end);
        String[] items = string.split("\\s*[ ,;:]\\s*");

        int[] data = new int[items.length];
        for (int i = 0; i < data.length; i++) {
            data[i] = Integer.parseInt(items[i].trim());
        }

        return data;
    }

    @Override
    public String valueToString(Object value) throws ParseException {
        if (value == null) {
            return "";
        }

        return switch (value) {
            case byte[] data -> Arrays.toString(data);
            case short[] data -> Arrays.toString(data);
            case int[] data -> Arrays.toString(data);
            case long[] data -> Arrays.toString(data);
            default -> throw new ParseException("Unsupported data type: " + value.getClass(), 0);
        };
    }
}
