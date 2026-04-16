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
package smile.nlp.dictionary;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class EnglishPunctuationsTest {

    public EnglishPunctuationsTest() {
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

    /**
     * Test of contains method, of class EnglishPunctuations.
     */
    @Test
    public void testContains() {
        System.out.println("contains");
        assertTrue(EnglishPunctuations.getInstance().contains("["));
        assertTrue(EnglishPunctuations.getInstance().contains("]"));
        assertTrue(EnglishPunctuations.getInstance().contains("("));
        assertTrue(EnglishPunctuations.getInstance().contains(")"));
        assertTrue(EnglishPunctuations.getInstance().contains("{"));
        assertTrue(EnglishPunctuations.getInstance().contains("}"));
        assertTrue(EnglishPunctuations.getInstance().contains("<"));
        assertTrue(EnglishPunctuations.getInstance().contains(">"));
        assertTrue(EnglishPunctuations.getInstance().contains(":"));
        assertTrue(EnglishPunctuations.getInstance().contains(","));
        assertTrue(EnglishPunctuations.getInstance().contains(";"));
        assertTrue(EnglishPunctuations.getInstance().contains("-"));
        assertTrue(EnglishPunctuations.getInstance().contains("--"));
        assertTrue(EnglishPunctuations.getInstance().contains("---"));
        assertTrue(EnglishPunctuations.getInstance().contains("!"));
        assertTrue(EnglishPunctuations.getInstance().contains("?"));
        assertTrue(EnglishPunctuations.getInstance().contains("."));
        assertTrue(EnglishPunctuations.getInstance().contains("..."));
        assertTrue(EnglishPunctuations.getInstance().contains("`"));
        assertTrue(EnglishPunctuations.getInstance().contains("'"));
        assertTrue(EnglishPunctuations.getInstance().contains("\""));
        assertTrue(EnglishPunctuations.getInstance().contains("/"));
        assertFalse(EnglishPunctuations.getInstance().contains(""));
        assertFalse(EnglishPunctuations.getInstance().contains("word"));
    }

    /**
     * Test of size method, of class EnglishPunctuations.
     */
    @Test
    public void testSize() {
        System.out.println("size");
        // Given the EnglishPunctuations singleton
        // When querying the size
        // Then it should match the number of defined punctuation marks (22)
        assertEquals(22, EnglishPunctuations.getInstance().size());
    }

    /**
     * Test of isNotPunctuation method, of class EnglishPunctuations.
     */
    @Test
    public void testIsNotPunctuation() {
        System.out.println("isNotPunctuation");
        // Given the EnglishPunctuations singleton
        // When calling isNotPunctuation on a regular word
        // Then it should return true
        assertTrue(EnglishPunctuations.getInstance().isNotPunctuation("hello"));
        assertTrue(EnglishPunctuations.getInstance().isNotPunctuation("world"));
        // And for actual punctuation marks, it should return false
        assertFalse(EnglishPunctuations.getInstance().isNotPunctuation("."));
        assertFalse(EnglishPunctuations.getInstance().isNotPunctuation("!"));
        assertFalse(EnglishPunctuations.getInstance().isNotPunctuation("?"));
    }

    /**
     * Test of iterator / Iterable, of class EnglishPunctuations.
     */
    @Test
    public void testIterable() {
        System.out.println("iterable");
        // Given the EnglishPunctuations singleton implementing Iterable
        // When iterating with a for-each loop
        // Then we visit exactly size() non-null marks
        int count = 0;
        for (String mark : EnglishPunctuations.getInstance()) {
            assertNotNull(mark);
            assertFalse(mark.isEmpty());
            count++;
        }
        assertEquals(EnglishPunctuations.getInstance().size(), count);
    }

    /**
     * Test of stream, of class EnglishPunctuations.
     */
    @Test
    public void testStream() {
        System.out.println("stream");
        // Given the EnglishPunctuations singleton
        // When using stream()
        // Then the stream count equals size()
        assertEquals(EnglishPunctuations.getInstance().size(),
                EnglishPunctuations.getInstance().stream().count());
    }
}