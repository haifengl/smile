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

package smile.util;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * String utility functions.
 *
 * @author Haifeng Li
 */
public interface Strings {

    /** Decimal format for floating numbers. */
    DecimalFormat decimalFormat = new DecimalFormat("#.######");

    /** Returns true if the string is null or empty. */
    static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /** Returns the string representation of ordinal number with suffix. */
    static String ordinal(int i) {
        final String[] suffixes = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
        switch (i % 100) {
            case 11:
            case 12:
            case 13:
                return i + "th";
            default:
                return i + suffixes[i % 10];
        }
    }

    /** Left pad a String with a specified character.
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    static String leftPad(String str, int size, char padChar) {
        if (str == null)
            return null;

        int pads = size - str.length();
        if (pads <= 0)
            return str; // returns original String when possible

        return fill(padChar, pads).concat(str);
    }

    /** Right pad a String with a specified character.
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    static String rightPad(String str, int size, char padChar) {
        if (str == null)
            return null;

        int pads = size - str.length();
        if (pads <= 0)
            return str; // returns original String when possible

        return str.concat(fill(padChar, pads));
    }

    /** Returns a string with a single repeated character to a specific length. */
    static String fill(char ch, int len) {
        char[] chars = new char[len];
        Arrays.fill(chars, ch);
        return new String(chars);
    }

    /** Returns the decimal string representation of a floating number. */
    static String decimal(double x) {
        return decimalFormat.format(x);
    }

    /** Returns the string representation of array in format '[1, 2, 3]'." */
    static String toString(int[] a) {
        return Arrays.stream(a).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }

    /** Returns the string representation of array in format '[1.0, 2.0, 3.0]'." */
    static String toString(float[] a) {
        return IntStream.range(0, a.length).mapToObj(i -> String.valueOf(a[i])).collect(Collectors.joining(", ", "[", "]"));
    }

    /** Returns the string representation of array in format '[1.0, 2.0, 3.0]'." */
    static String toString(double[] a) {
        return Arrays.stream(a).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Parses a double array in format '[1.0, 2.0, 3.0]'.
     * Returns null if s is null or empty.
     */
    static int[] parseIntArray(String s) {
        if (isNullOrEmpty(s)) return null;

        String[] tokens = s.trim().substring(1, s.length() - 1).split(",");
        return Arrays.stream(tokens).map(String::trim).mapToInt(Integer::parseInt).toArray();
    }

    /**
     * Parses a double array in format '[1.0, 2.0, 3.0]'.
     * Returns null if s is null or empty.
     */
    static double[] parseDoubleArray(String s) {
        if (isNullOrEmpty(s)) return null;

        String[] tokens = s.trim().substring(1, s.length() - 1).split(",");
        return Arrays.stream(tokens).map(String::trim).mapToDouble(Double::parseDouble).toArray();
    }
}
