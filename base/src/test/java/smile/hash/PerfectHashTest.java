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
package smile.hash;

import java.util.HashSet;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PerfectHash}.
 *
 * @author Haifeng Li
 */
public class PerfectHashTest {

    public PerfectHashTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    // -----------------------------------------------------------------------
    // PerfectHash — basic
    // -----------------------------------------------------------------------

    @Test
    public void testHash() {
        System.out.println("PerfectHash");
        PerfectHash hash = new PerfectHash("abc", "great", "hash");
        assertEquals(0, hash.get("abc"));
        assertEquals(1, hash.get("great"));
        assertEquals(2, hash.get("hash"));
        assertEquals(-1, hash.get("perfect"));
        assertEquals(-1, hash.get("hash2"));
    }

    @Test
    public void testSingleKey() {
        System.out.println("PerfectHash single key");
        PerfectHash hash = new PerfectHash("only");
        assertEquals(0, hash.get("only"));
        assertEquals(-1, hash.get("other"));
    }

    @Test
    public void testTwoKeys() {
        System.out.println("PerfectHash two keys");
        PerfectHash hash = new PerfectHash("alpha", "beta");
        assertEquals(0, hash.get("alpha"));
        assertEquals(1, hash.get("beta"));
        assertEquals(-1, hash.get("gamma"));
    }

    @Test
    public void testLargeKeySet() {
        System.out.println("PerfectHash large key set");
        // Use Java keywords as a realistic set
        String[] keywords = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while"
        };
        PerfectHash hash = new PerfectHash(keywords);
        // Every keyword maps to its own index
        for (int i = 0; i < keywords.length; i++) {
            assertEquals(i, hash.get(keywords[i]),
                "Wrong index for keyword: " + keywords[i]);
        }
        // Non-keyword returns -1
        assertEquals(-1, hash.get("notakeyword"));
        assertEquals(-1, hash.get("INT"));  // case-sensitive
        assertEquals(-1, hash.get(""));
    }

    @Test
    public void testCaseSensitivity() {
        System.out.println("PerfectHash case sensitivity");
        PerfectHash hash = new PerfectHash("Hello", "World");
        assertEquals(0, hash.get("Hello"));
        assertEquals(1, hash.get("World"));
        assertEquals(-1, hash.get("hello"));   // different case → not found
        assertEquals(-1, hash.get("HELLO"));
    }

    @Test
    public void testEmptyStringKey() {
        System.out.println("PerfectHash with empty string key");
        // Empty string is a valid key (length 0)
        PerfectHash hash = new PerfectHash("", "a", "bb");
        assertEquals(0, hash.get(""));
        assertEquals(1, hash.get("a"));
        assertEquals(2, hash.get("bb"));
        assertEquals(-1, hash.get("c"));
    }

    @Test
    public void testEmptyKeySetThrows() {
        System.out.println("PerfectHash empty key set");
        assertThrows(IllegalArgumentException.class, () -> new PerfectHash());
    }

    @Test
    public void testDuplicateKeyThrows() {
        System.out.println("PerfectHash duplicate key");
        assertThrows(IllegalArgumentException.class,
            () -> new PerfectHash("a", "b", "a"));
    }

    @Test
    public void testReturnedIndicesAreUnique() {
        System.out.println("PerfectHash indices are unique");
        String[] keys = {"foo", "bar", "baz", "qux", "quux"};
        PerfectHash hash = new PerfectHash(keys);
        var seen = new HashSet<Integer>();
        for (String k : keys) {
            int idx = hash.get(k);
            assertTrue(idx >= 0 && idx < keys.length, "Index out of range: " + idx);
            assertTrue(seen.add(idx), "Duplicate index " + idx + " for key " + k);
        }
    }

    @Test
    public void testWithSelectPositions() {
        System.out.println("PerfectHash with select positions");
        // Use position 0 only to disambiguate
        String[] keys = {"apple", "banana", "cherry"};
        PerfectHash hash = new PerfectHash(new int[]{0}, keys);
        assertEquals(0, hash.get("apple"));
        assertEquals(1, hash.get("banana"));
        assertEquals(2, hash.get("cherry"));
        assertEquals(-1, hash.get("avocado"));
    }

    @Test
    public void testMissWithNonPrintablePrefix() {
        System.out.println("PerfectHash miss with character below min");
        PerfectHash hash = new PerfectHash("abc", "def");
        // A string with a character below the minimum seen character
        // should return -1, not throw
        assertEquals(-1, hash.get("\u0001abc"));
    }
}
