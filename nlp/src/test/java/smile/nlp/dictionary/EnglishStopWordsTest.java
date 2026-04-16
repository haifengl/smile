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
 * Tests for {@link EnglishStopWords}.
 *
 * @author Haifeng Li
 */
public class EnglishStopWordsTest {

    // ---- DEFAULT ----

    @Test
    public void testDefaultContainsStopWord() {
        // Given the DEFAULT stop words list
        // When checking a common stop word
        // Then it should be found
        assertTrue(EnglishStopWords.DEFAULT.contains("the"));
        assertTrue(EnglishStopWords.DEFAULT.contains("and"));
        assertTrue(EnglishStopWords.DEFAULT.contains("is"));
    }

    @Test
    public void testDefaultDoesNotContainContentWord() {
        // Given the DEFAULT stop words list
        // When checking a meaningful content word
        // Then it should not be found
        assertFalse(EnglishStopWords.DEFAULT.contains("computer"));
        assertFalse(EnglishStopWords.DEFAULT.contains("algorithm"));
    }

    @Test
    public void testDefaultSize() {
        // Given the DEFAULT stop words list
        // When querying its size
        // Then it should be in the expected range (~386 entries in the file)
        int size = EnglishStopWords.DEFAULT.size();
        assertTrue(size > 100, "Expected more than 100 default stop words, got " + size);
        assertTrue(size < 600, "Expected fewer than 600 default stop words, got " + size);
    }

    @Test
    public void testDefaultIsNotStopWord() {
        // Given the DEFAULT stop words list
        // When using the isNotStopWord convenience method
        // Then it returns true for content words and false for stop words
        assertTrue(EnglishStopWords.DEFAULT.isNotStopWord("machine"));
        assertFalse(EnglishStopWords.DEFAULT.isNotStopWord("the"));
    }

    @Test
    public void testDefaultIterable() {
        // Given the DEFAULT stop words list implementing Iterable
        // When iterating with a for-each loop
        // Then all words are non-null and non-empty, and count matches size()
        int count = 0;
        for (String word : EnglishStopWords.DEFAULT) {
            assertNotNull(word);
            assertFalse(word.isEmpty());
            count++;
        }
        assertEquals(EnglishStopWords.DEFAULT.size(), count);
    }

    @Test
    public void testDefaultStream() {
        // Given the DEFAULT stop words list
        // When using stream()
        // Then the stream count matches size()
        assertEquals(EnglishStopWords.DEFAULT.size(),
                EnglishStopWords.DEFAULT.stream().count());
    }

    // ---- COMPREHENSIVE ----

    @Test
    public void testComprehensiveContainsStopWord() {
        // Given the COMPREHENSIVE stop words list
        // When checking common stop words
        // Then they should be found
        assertTrue(EnglishStopWords.COMPREHENSIVE.contains("the"));
        assertTrue(EnglishStopWords.COMPREHENSIVE.contains("a"));
    }

    @Test
    public void testComprehensiveIsLargerThanDefault() {
        // Given both DEFAULT and COMPREHENSIVE lists
        // When comparing sizes
        // Then COMPREHENSIVE should have more entries
        assertTrue(EnglishStopWords.COMPREHENSIVE.size() > EnglishStopWords.DEFAULT.size());
    }

    @Test
    public void testComprehensiveSize() {
        // Given the COMPREHENSIVE stop words list
        // When querying its size
        // Then it should be in the expected range (~735 entries in the file)
        int size = EnglishStopWords.COMPREHENSIVE.size();
        assertTrue(size > 500, "Expected more than 500 comprehensive stop words, got " + size);
        assertTrue(size < 1200, "Expected fewer than 1200 comprehensive stop words, got " + size);
    }

    // ---- GOOGLE ----

    @Test
    public void testGoogleContainsStopWord() {
        // Given the GOOGLE stop words list
        // When checking known Google stop words
        // Then they should be found
        assertTrue(EnglishStopWords.GOOGLE.contains("the"));
        assertTrue(EnglishStopWords.GOOGLE.contains("is"));
        assertTrue(EnglishStopWords.GOOGLE.contains("a"));
    }

    @Test
    public void testGoogleSize() {
        // Given the GOOGLE stop words list (duplicate "the" removed)
        // When querying its size
        // Then it should be 31 (32 lines minus 1 duplicate)
        assertEquals(31, EnglishStopWords.GOOGLE.size());
    }

    @Test
    public void testGoogleNoDuplicates() {
        // Given the GOOGLE stop words list
        // When streaming all words
        // Then there are no duplicates (size equals distinct count)
        long distinct = EnglishStopWords.GOOGLE.stream().distinct().count();
        assertEquals(EnglishStopWords.GOOGLE.size(), distinct);
    }

    // ---- MYSQL ----

    @Test
    public void testMysqlContainsStopWord() {
        // Given the MYSQL stop words list
        // When checking MySQL-known stop words
        // Then they should be found
        assertTrue(EnglishStopWords.MYSQL.contains("the"));
        assertTrue(EnglishStopWords.MYSQL.contains("a"));
        assertTrue(EnglishStopWords.MYSQL.contains("able"));
    }

    @Test
    public void testMysqlSize() {
        // Given the MYSQL stop words list
        // When querying its size
        // Then it should be in the expected range (~544 entries)
        int size = EnglishStopWords.MYSQL.size();
        assertTrue(size > 400, "Expected more than 400 MySQL stop words, got " + size);
        assertTrue(size < 800, "Expected fewer than 800 MySQL stop words, got " + size);
    }
}

