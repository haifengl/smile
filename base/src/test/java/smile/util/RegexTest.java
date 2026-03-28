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
public class RegexTest {
    @Test
    void testIntegerRegex() {
        assertTrue(smile.util.Regex.INTEGER.matcher("123").matches());
        assertTrue(smile.util.Regex.INTEGER.matcher("-456").matches());
        assertFalse(smile.util.Regex.INTEGER.matcher("12.3").matches());
    }

    @Test
    void testDoubleRegex() {
        assertTrue(smile.util.Regex.DOUBLE.matcher("123.45").matches());
        assertTrue(smile.util.Regex.DOUBLE.matcher("-1.23e10").matches());
        assertFalse(smile.util.Regex.DOUBLE.matcher("abc").matches());
    }

    @Test
    void testBooleanRegex() {
        assertTrue(smile.util.Regex.BOOLEAN.matcher("true").matches());
        assertTrue(smile.util.Regex.BOOLEAN.matcher("FALSE").matches());
        assertFalse(smile.util.Regex.BOOLEAN.matcher("yes").matches());
    }

    @Test
    void testEmailRegex() {
        assertTrue(smile.util.Regex.EMAIL_ADDRESS.matcher("test@example.com").matches());
        assertFalse(smile.util.Regex.EMAIL_ADDRESS.matcher("test@.com").matches());
    }

    @Test
    void testUrlRegex() {
        assertTrue(smile.util.Regex.URL.matcher("http://example.com").matches());
        assertFalse(smile.util.Regex.URL.matcher("example.com").matches());
    }
}
