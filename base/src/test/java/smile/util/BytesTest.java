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

public class BytesTest {

    @Test
    public void testStringRoundTrip() {
        Bytes b = new Bytes("hello");
        assertEquals("hello", b.toString());
    }

    @Test
    public void testLength() {
        Bytes b = new Bytes("abc");
        assertEquals(3, b.length());
    }

    @Test
    public void testSlice() {
        Bytes b = new Bytes("abcdef");
        Bytes s = b.slice(1, 4);
        assertEquals("bcd", s.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        Bytes a = new Bytes("test");
        Bytes b = new Bytes("test");
        Bytes c = new Bytes("other");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void testEqualsWithNonBytes() {
        Bytes b = new Bytes("x");
        assertNotEquals("x", b);
        assertNotNull(b);
    }

    @Test
    public void testEmptyString() {
        Bytes b = new Bytes("");
        assertEquals(0, b.length());
        assertEquals("", b.toString());
    }

    @Test
    public void testUnicodeRoundTrip() {
        String unicode = "こんにちは";
        Bytes b = new Bytes(unicode);
        assertEquals(unicode, b.toString());
    }

    @Test
    public void testByteArrayConstructor() {
        byte[] raw = {72, 105};   // "Hi"
        Bytes b = new Bytes(raw);
        assertEquals("Hi", b.toString());
    }
}

