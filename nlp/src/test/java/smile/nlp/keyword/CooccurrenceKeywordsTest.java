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
package smile.nlp.keyword;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import smile.nlp.collocation.NGram;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CooccurrenceKeywords}.
 *
 * @author Haifeng Li
 */
public class CooccurrenceKeywordsTest {

    // -------------------------------------------------------------------
    // Happy-path tests using the Turing (1950) paper excerpt
    // -------------------------------------------------------------------

    @Test
    public void testExtractDefaultTopTen() throws IOException {
        // Given
        String text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));

        // When
        NGram[] result = CooccurrenceKeywords.of(text);

        // Then: exactly 10 keywords returned
        assertEquals(10, result.length, "Default extraction should return 10 keywords");
    }

    @Test
    public void testExtractCustomCount() throws IOException {
        // Given
        String text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));

        // When
        NGram[] result = CooccurrenceKeywords.of(text, 5);

        // Then: at most 5 keywords returned
        assertTrue(result.length <= 5, "Should return at most 5 keywords");
        assertTrue(result.length > 0, "Should return at least 1 keyword");
    }

    @Test
    public void testExtractContainsCoreKeywords() throws IOException {
        // Given
        String text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));

        // When
        NGram[] result = CooccurrenceKeywords.of(text);
        Set<String> keywordPhrases = Arrays.stream(result)
                .map(ng -> String.join(" ", ng.words))
                .collect(Collectors.toSet());

        // Then: semantically important terms from the Turing paper must be present
        assertTrue(keywordPhrases.contains("machine") || keywordPhrases.contains("digital computer")
                        || keywordPhrases.contains("imitation game"),
                "Expected at least one core Turing-paper keyword, got: " + keywordPhrases);
    }

    @Test
    public void testExtractNoDuplicatePhrases() throws IOException {
        // Given
        String text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));

        // When
        NGram[] result = CooccurrenceKeywords.of(text);
        Set<String> keywordPhrases = Arrays.stream(result)
                .map(ng -> String.join(" ", ng.words))
                .collect(Collectors.toSet());

        // Then: no duplicate phrase strings
        assertEquals(result.length, keywordPhrases.size(),
                "Returned keywords must not contain duplicate phrases");
    }

    // -------------------------------------------------------------------
    // Input-validation tests
    // -------------------------------------------------------------------

    @Test
    public void testExtractNullTextThrows() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> CooccurrenceKeywords.of(null));
    }

    @Test
    public void testExtractBlankTextThrows() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> CooccurrenceKeywords.of("   "));
    }

    @Test
    public void testExtractZeroKeywordsThrows() throws IOException {
        // Given
        String text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> CooccurrenceKeywords.of(text, 0));
    }

    @Test
    public void testExtractNegativeKeywordsThrows() throws IOException {
        // Given
        String text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> CooccurrenceKeywords.of(text, -1));
    }

    // -------------------------------------------------------------------
    // Edge-case: document too short to produce qualifying n-grams
    // -------------------------------------------------------------------

    @Test
    public void testExtractShortTextReturnsEmpty() {
        // Given: a two-sentence text that cannot produce n-grams with frequency >= 4
        String text = "The cat sat on the mat. The dog ran in the park.";

        // When
        NGram[] result = CooccurrenceKeywords.of(text);

        // Then: empty array, no exception
        assertNotNull(result);
        assertEquals(0, result.length);
    }
}