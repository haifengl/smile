/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.swing.text;

import javax.swing.text.DefaultFormatter;
import java.text.ParseException;

/**
 * Text formatter for floating array values.
 *
 * @author Haifeng Li
 */
public class FloatArrayFormatter extends DefaultFormatter {
    /** Constructor. */
    public FloatArrayFormatter() {

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

        double[] data = new double[items.length];
        for (int i = 0; i < data.length; i++) {
            data[i] = Double.parseDouble(items[i].trim());
        }

        return data;
    }

    @Override
    public String valueToString(Object value) throws ParseException {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        if (value instanceof float[] data) {
            if (data.length > 0) {
                builder.append("[").append(data[0]);
            }

            for (int i = 1; i < data.length; i++) {
                builder.append(", ").append(data[i]);
            }
            builder.append("]");

        } else if (value instanceof double[] data) {
            if (data.length > 0) {
                builder.append("[").append(data[0]);
            }

            for (int i = 1; i < data.length; i++) {
                builder.append(", ").append(data[i]);
            }
            builder.append("]");
        } else {
            throw new ParseException("Unsupported data type: " + value.getClass(), 0);
        }

        return builder.toString();
    }
}
