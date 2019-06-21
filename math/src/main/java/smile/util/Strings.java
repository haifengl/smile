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

import java.util.Arrays;

/**
 * String utility functions.
 *
 * @author Haifeng Li
 */
public interface Strings {
    /** Returns true if the string is null or empty. */
    static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
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
}
