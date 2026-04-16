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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EnglishDictionary}.
 *
 * @author Haifeng Li
 */
public class EnglishDictionaryTest {

    @Test
    public void testContainsKnownWord() {
        // Given the CONCISE dictionary
        // When checking a common English word
        // Then it should be found
        assertTrue(EnglishDictionary.CONCISE.contains("house"));
        assertTrue(EnglishDictionary.CONCISE.contains("apple"));
        assertTrue(EnglishDictionary.CONCISE.contains("mountain"));
    }

    @Test
    public void testContainsUnknownWord() {
        // Given the CONCISE dictionary
        // When checking a non-word or very rare word
        // Then it should not be found
        assertFalse(EnglishDictionary.CONCISE.contains("xyzzy"));
        assertFalse(EnglishDictionary.CONCISE.contains("qwerty"));
        assertFalse(EnglishDictionary.CONCISE.contains(""));
    }

    @Test
    public void testContainsSingleCapitalLetterExcluded() {
        // Given the CONCISE dictionary
        // When checking single uppercase letters (filtered during loading)
        // Then they should not be present
        assertFalse(EnglishDictionary.CONCISE.contains("A"));
        assertFalse(EnglishDictionary.CONCISE.contains("Z"));
    }

    @Test
    public void testSize() {
        // Given the CONCISE dictionary
        // When querying its size
        // Then it should be a large positive number (roughly 25000+)
        int size = EnglishDictionary.CONCISE.size();
        assertTrue(size > 20000, "Expected more than 20000 words, got " + size);
        assertTrue(size < 100000, "Expected less than 100000 words, got " + size);
    }

    @Test
    public void testIterableForEach() {
        // Given the CONCISE dictionary implementing Iterable
        // When iterating with a for-each loop
        // Then we should be able to count words matching the expected size
        int count = 0;
        for (String word : EnglishDictionary.CONCISE) {
            assertNotNull(word);
            assertFalse(word.isEmpty());
            count++;
        }
        assertEquals(EnglishDictionary.CONCISE.size(), count);
    }

    @Test
    public void testStream() {
        // Given the CONCISE dictionary
        // When using stream()
        // Then the stream count should equal size()
        long streamCount = EnglishDictionary.CONCISE.stream().count();
        assertEquals(EnglishDictionary.CONCISE.size(), streamCount);
    }

    @Test
    public void testStreamFilter() {
        // Given the CONCISE dictionary
        // When filtering words starting with "a"
        // Then we get a non-empty subset
        long count = EnglishDictionary.CONCISE.stream()
                .filter(w -> w.startsWith("a"))
                .count();
        assertTrue(count > 0, "Expected words starting with 'a'");
    }
}

