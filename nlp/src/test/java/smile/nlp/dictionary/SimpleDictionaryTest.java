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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimpleDictionary}.
 *
 * @author Haifeng Li
 */
public class SimpleDictionaryTest {

    @Test
    public void testLoadFromClasspath() {
        // Given a classpath resource path for the English dictionary
        // When constructing a SimpleDictionary
        // Then it should load words successfully
        SimpleDictionary dict = new SimpleDictionary("/smile/nlp/dictionary/dictionary_en.txt");
        assertTrue(dict.size() > 20000, "Expected more than 20000 words, got " + dict.size());
    }

    @Test
    public void testContainsKnownWord() {
        // Given a SimpleDictionary loaded from the English dictionary resource
        // When checking a common English word
        // Then it should be found
        SimpleDictionary dict = new SimpleDictionary("/smile/nlp/dictionary/dictionary_en.txt");
        assertTrue(dict.contains("house"));
        assertTrue(dict.contains("apple"));
    }

    @Test
    public void testDoesNotContainUnknownWord() {
        // Given a SimpleDictionary loaded from the English dictionary resource
        // When checking a non-existent word
        // Then it should not be found
        SimpleDictionary dict = new SimpleDictionary("/smile/nlp/dictionary/dictionary_en.txt");
        assertFalse(dict.contains("xyzzy"));
        assertFalse(dict.contains(""));
    }

    @Test
    public void testLoadFromPath(@TempDir Path tempDir) throws IOException {
        // Given a temporary file with custom words
        // When constructing a SimpleDictionary from that Path
        // Then it should contain exactly those words
        Path file = tempDir.resolve("custom.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");

        SimpleDictionary dict = new SimpleDictionary(file);
        assertEquals(3, dict.size());
        assertTrue(dict.contains("alpha"));
        assertTrue(dict.contains("beta"));
        assertTrue(dict.contains("gamma"));
        assertFalse(dict.contains("delta"));
    }

    @Test
    public void testLoadFromPathString(@TempDir Path tempDir) throws IOException {
        // Given a temporary file on disk
        // When constructing a SimpleDictionary from the path string
        // Then it should load words from the file (not classpath)
        Path file = tempDir.resolve("words.txt");
        Files.writeString(file, "sun\nmoon\nstar\n");

        SimpleDictionary dict = new SimpleDictionary(file.toString());
        assertEquals(3, dict.size());
        assertTrue(dict.contains("sun"));
        assertTrue(dict.contains("moon"));
        assertTrue(dict.contains("star"));
    }

    @Test
    public void testSkipBlankLines(@TempDir Path tempDir) throws IOException {
        // Given a file with blank lines
        // When loading the dictionary
        // Then blank lines should be ignored
        Path file = tempDir.resolve("blanks.txt");
        Files.writeString(file, "word1\n\nword2\n\n");

        SimpleDictionary dict = new SimpleDictionary(file);
        assertEquals(2, dict.size());
        assertTrue(dict.contains("word1"));
        assertTrue(dict.contains("word2"));
    }

    @Test
    public void testSkipSingleCapitalLetters(@TempDir Path tempDir) throws IOException {
        // Given a file containing single capital letters (e.g., section headers)
        // When loading the dictionary
        // Then single capital letters should be excluded
        Path file = tempDir.resolve("caps.txt");
        Files.writeString(file, "apple\nA\nbanana\nB\n");

        SimpleDictionary dict = new SimpleDictionary(file);
        assertEquals(2, dict.size());
        assertTrue(dict.contains("apple"));
        assertTrue(dict.contains("banana"));
        assertFalse(dict.contains("A"));
        assertFalse(dict.contains("B"));
    }

    @Test
    public void testIterable(@TempDir Path tempDir) throws IOException {
        // Given a small SimpleDictionary
        // When iterating with a for-each loop
        // Then all words are non-null, non-empty, and count matches size()
        Path file = tempDir.resolve("iter.txt");
        Files.writeString(file, "one\ntwo\nthree\n");

        SimpleDictionary dict = new SimpleDictionary(file);
        int count = 0;
        for (String word : dict) {
            assertNotNull(word);
            assertFalse(word.isEmpty());
            count++;
        }
        assertEquals(dict.size(), count);
    }

    @Test
    public void testStream(@TempDir Path tempDir) throws IOException {
        // Given a small SimpleDictionary
        // When using the stream() method
        // Then the stream count should equal size()
        Path file = tempDir.resolve("stream.txt");
        Files.writeString(file, "cat\ndog\nbird\n");

        SimpleDictionary dict = new SimpleDictionary(file);
        assertEquals(3, dict.stream().count());
    }
}

