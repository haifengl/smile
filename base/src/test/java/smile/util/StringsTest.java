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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author SMILE Agent
 */
public class StringsTest {
    @Test
    void testIsNullOrEmpty() {
        assertTrue(Strings.isNullOrEmpty(null));
        assertTrue(Strings.isNullOrEmpty(""));
        assertFalse(Strings.isNullOrEmpty(" "));
        assertFalse(Strings.isNullOrEmpty("abc"));
    }

    @Test
    void testIsNullOrBlank() {
        assertTrue(Strings.isNullOrBlank(null));
        assertTrue(Strings.isNullOrBlank(""));
        assertTrue(Strings.isNullOrBlank("   "));
        assertFalse(Strings.isNullOrBlank("abc"));
    }

    @Test
    void testKebab() {
        assertEquals("hello-world", Strings.kebab("HelloWorld"));
        assertEquals("hello-world", Strings.kebab("helloWorld"));
        assertEquals("hello-world", Strings.kebab("hello_world"));
        assertEquals("hello-world", Strings.kebab("hello world"));
        assertEquals("h-e-l-l-o", Strings.kebab("H E L L O"));
        assertEquals("hello", Strings.kebab("hello"));
    }

    @Test
    void testInterpolate() {
        String envVar = System.getenv().keySet().stream().findFirst().orElse("PATH");
        String value = System.getenv(envVar);
        String input = "Value: ${" + envVar + "}";
        assertEquals("Value: " + (value != null ? value : ""), Strings.interpolate(input));
        assertEquals("NoVar", Strings.interpolate("NoVar"));
        assertEquals("", Strings.interpolate("${NOT_A_VAR}"));
        assertNull(Strings.interpolate(null));
    }

    @Test
    void testUnescape() {
        assertEquals("hello\nworld", Strings.unescape("hello\\nworld"));
        assertEquals("tab\tchar", Strings.unescape("tab\\tchar"));
        assertEquals("quote\"test", Strings.unescape("quote\\\"test"));
        assertEquals("backslash\\end", Strings.unescape("backslash\\\\end"));
        assertEquals("unicode\u1234", Strings.unescape("unicode\\u1234"));
        assertEquals("octalA", Strings.unescape("octal\\101"));
    }

    @Test
    void testHtmlEscape() {
        assertEquals("&lt;div&gt;", Strings.htmlEscape("<div>"));
        assertEquals("&amp;", Strings.htmlEscape("&"));
        assertEquals("abc", Strings.htmlEscape("abc"));
    }

    @Test
    void testOrdinal() {
        assertEquals("1st", Strings.ordinal(1));
        assertEquals("2nd", Strings.ordinal(2));
        assertEquals("3rd", Strings.ordinal(3));
        assertEquals("4th", Strings.ordinal(4));
        assertEquals("11th", Strings.ordinal(11));
        assertEquals("12th", Strings.ordinal(12));
        assertEquals("13th", Strings.ordinal(13));
        assertEquals("21st", Strings.ordinal(21));
        assertEquals("22nd", Strings.ordinal(22));
        assertEquals("23rd", Strings.ordinal(23));
        assertEquals("101st", Strings.ordinal(101));
    }

    @Test
    void testLeftPad() {
        assertEquals("  abc", Strings.leftPad("abc", 5, ' '));
        assertEquals("abc", Strings.leftPad("abc", 2, ' '));
        assertNull(Strings.leftPad(null, 5, ' '));
    }

    @Test
    void testRightPad() {
        assertEquals("abc  ", Strings.rightPad("abc", 5, ' '));
        assertEquals("abc", Strings.rightPad("abc", 2, ' '));
        assertNull(Strings.rightPad(null, 5, ' '));
    }

    @Test
    void testFill() {
        assertEquals("aaa", Strings.fill('a', 3));
        assertEquals("", Strings.fill('x', 0));
    }

    @Test
    void testFormatFloat() {
        assertEquals("1", Strings.format(1.0f));
        assertEquals("1.5", Strings.format(1.5f));
    }

    @Test
    void testFormatDouble() {
        assertEquals("2", Strings.format(2.0));
        assertEquals("2.25", Strings.format(2.25));
    }

    @Test
    void testParseIntArray() {
        assertArrayEquals(new int[]{1,2,3}, Strings.parseIntArray("[1, 2, 3]"));
        assertNull(Strings.parseIntArray(null));
        assertNull(Strings.parseIntArray(""));
        assertThrows(IllegalArgumentException.class, () -> Strings.parseIntArray("1,2,3"));
        assertThrows(NumberFormatException.class, () -> Strings.parseIntArray("[a, b, c]"));
    }

    @Test
    void testParseDoubleArray() {
        assertArrayEquals(new double[]{1.0,2.0,3.0}, Strings.parseDoubleArray("[1.0, 2.0, 3.0]"), 1e-6);
        assertNull(Strings.parseDoubleArray(null));
        assertNull(Strings.parseDoubleArray(""));
        assertThrows(IllegalArgumentException.class, () -> Strings.parseDoubleArray("1.0,2.0,3.0"));
        assertThrows(NumberFormatException.class, () -> Strings.parseDoubleArray("[a, b, c]"));
    }
}
