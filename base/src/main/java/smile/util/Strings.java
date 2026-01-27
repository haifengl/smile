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
package smile.util;

import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * String utility functions.
 *
 * @author Haifeng Li
 */
public interface Strings {
    /** Decimal format for floating numbers. */
    DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.####");

    /**
     * Returns true if the string is null or empty.
     * @param s the string.
     * @return true if the string is null or empty.
     */
    static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Unescapes a string that contains standard Java escape sequences.
     * @param s the string.
     * @return the translated string.
     */
    static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == s.length() - 1) ? '\\' : s.charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = String.valueOf(nextChar);
                    i++;
                    if ((i < s.length() - 1) && s.charAt(i + 1) >= '0' && s.charAt(i + 1) <= '7') {
                        code += s.charAt(i + 1);
                        i++;
                        if ((i < s.length() - 1) && s.charAt(i + 1) >= '0' && s.charAt(i + 1) <= '7') {
                            code += s.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }

                switch (nextChar) {
                    case '\\':
                        ch = '\\';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    // Hex Unicode: u????
                    case 'u':
                        if (i >= s.length() - 5) {
                            ch = 'u';
                            break;
                        }
                        int code = Integer.parseInt(s.substring(i+2, i+6), 16);
                        sb.append(Character.toChars(code));
                        i += 5;
                        continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Turn special characters into HTML character references.
     *
     * @param input the (unescaped) input string
     * @return the escaped string
     */
    static String htmlEscape(String input) {
        return htmlEscape(input, HtmlCharacter.DEFAULT_ENCODING);
    }

    /**
     * Turn special characters into HTML character references.
     *
     * @param input    the (unescaped) input string
     * @param encoding the name of a supported {@link java.nio.charset.Charset charset}
     * @return the escaped string
     */
    static String htmlEscape(String input, String encoding) {
        HtmlCharacter html = new HtmlCharacter();
        StringBuilder escaped = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            String reference = html.escape(character, encoding);
            if (reference != null) {
                escaped.append(reference);
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    /**
     * Returns the string representation of ordinal number with suffix.
     * @param i the ordinal number.
     * @return the string representation of ordinal number with suffix.
     */
    static String ordinal(int i) {
        final String[] suffixes = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
        return switch (i % 100) {
            case 11, 12, 13 -> i + "th";
            default -> i + suffixes[i % 10];
        };
    }

    /**
     * Left pad a string with a specified character.
     *
     * @param s  the string to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    static String leftPad(String s, int size, char padChar) {
        if (s == null)
            return null;

        int pads = size - s.length();
        if (pads <= 0)
            return s; // returns original String when possible

        return fill(padChar, pads).concat(s);
    }

    /**
     * Right pad a string with a specified character.
     *
     * @param s  the string to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    static String rightPad(String s, int size, char padChar) {
        if (s == null)
            return null;

        int pads = size - s.length();
        if (pads <= 0)
            return s; // returns original String when possible

        return s.concat(fill(padChar, pads));
    }

    /**
     * Returns the string with a single repeated character to a specific length.
     * @param ch the character.
     * @param len the length of string.
     * @return the string.
     */
    static String fill(char ch, int len) {
        char[] chars = new char[len];
        Arrays.fill(chars, ch);
        return new String(chars);
    }

    /**
     * Returns the string representation of a floating number with the
     * minimum necessary precision.
     * @param x a real number.
     * @return the string representation.
     */
    static String format(float x) {
        return x == (long) x ? String.format("%d", (long) x) : String.format("%s", x);
    }

    /**
     * Returns the string representation of a floating number with the
     * minimum necessary precision.
     * @param x a real number.
     * @return the string representation.
     */
    static String format(double x) {
        return x == (long) x ? String.format("%d", (long) x) : String.format("%s", x);
    }

    /**
     * Parses an integer array in format '[1, 2, 3]'.
     * Returns null if s is null or empty.
     * @param s the string.
     * @return the array.
     */
    static int[] parseIntArray(String s) {
        if (isNullOrEmpty(s)) return null;

        s = s.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) {
            throw new IllegalArgumentException("Invalid string: " + s);
        }

        String[] tokens = s.substring(1, s.length() - 1).split(",");
        return Arrays.stream(tokens).map(String::trim).mapToInt(Integer::parseInt).toArray();
    }

    /**
     * Parses a double array in format '[1.0, 2.0, 3.0]'.
     * Returns null if s is null or empty.
     * @param s the string.
     * @return the array.
     */
    static double[] parseDoubleArray(String s) {
        if (isNullOrEmpty(s)) return null;

        s = s.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) {
            throw new IllegalArgumentException("Invalid string: " + s);
        }

        String[] tokens = s.substring(1, s.length() - 1).split(",");
        return Arrays.stream(tokens).map(String::trim).mapToDouble(Double::parseDouble).toArray();
    }
}
