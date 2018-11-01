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
package smile.data;

import java.util.Arrays;

/**
 * An immutable collection of data organized into named columns.
 *
 * @author Haifeng Li
 */
public class Utils {
    /** Left pad a String with a specified character.
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    public static String leftPad(String str, int size, char padChar) {
        if (str == null)
            return null;

        int pads = size - str.length();
        if (pads <= 0)
            return str; // returns original String when possible

        return string(padChar, pads).concat(str);
    }

    /** Right pad a String with a specified character.
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    public static String rightPad(String str, int size, char padChar) {
        if (str == null)
            return null;

        int pads = size - str.length();
        if (pads <= 0)
            return str; // returns original String when possible

        return str.concat(string(padChar, pads));
    }

    /** Returns a string with a single repeated character to a specific length. */
    public static String string(char ch, int len) {
        char[] chars = new char[len];
        Arrays.fill(chars, ch);
        return new String(chars);
    }
}
