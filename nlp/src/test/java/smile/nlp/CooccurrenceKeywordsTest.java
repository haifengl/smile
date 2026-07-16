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
package smile.nlp;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimpleDocument#keywords(int)}.
 *
 * @author Haifeng Li
 */
public class CooccurrenceKeywordsTest {
    static String text;

    @BeforeAll
    public static void setUpClass() throws Exception {
        // Given
        text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));
    }

    // -------------------------------------------------------------------
    // Happy-path tests using the Turing (1950) paper excerpt
    // -------------------------------------------------------------------

    @Test
    public void testExtractCustomCount() throws IOException {
        // When
        Text doc = Text.of(text);
        List<NGram> result = doc.keywords(5);

        // Then: at most 5 keywords returned
        assertTrue(result.size() <= 5, "Should return at most 5 keywords");
        assertFalse(result.isEmpty(), "Should return at least 1 keyword");
    }

    @Test
    public void testExtractContainsCoreKeywords() throws IOException {
        // When
        Text doc = Text.of(text);
        List<NGram> result = doc.keywords(10);
        Set<String> keywordPhrases = result.stream()
                .map(ng -> String.join(" ", ng.words()))
                .collect(Collectors.toSet());

        // Then: semantically important terms from the Turing paper must be present
        assertTrue(keywordPhrases.contains("machine") || keywordPhrases.contains("digital computer")
                        || keywordPhrases.contains("imitation game"),
                "Expected at least one core Turing-paper keyword, got: " + keywordPhrases);
    }

    @Test
    public void testExtractNoDuplicatePhrases() throws IOException {
        // When
        Text doc = Text.of(text);
        List<NGram> result = doc.keywords(10);
        Set<String> keywordPhrases = result.stream()
                .map(ng -> String.join(" ", ng.words()))
                .collect(Collectors.toSet());

        // Then: no duplicate phrase strings
        assertEquals(result.size(), keywordPhrases.size(),
                "Returned keywords must not contain duplicate phrases");
    }

    // -------------------------------------------------------------------
    // Input-validation tests
    // -------------------------------------------------------------------

    @Test
    public void testExtractNullTextThrows() {
        // Given / When / Then
        Text doc = Text.of(null);
        assertThrows(IllegalArgumentException.class, () -> doc.keywords(10));
    }

    @Test
    public void testExtractBlankTextThrows() {
        // Given / When / Then
        Text doc = Text.of("   ");
        assertThrows(IllegalArgumentException.class, () -> doc.keywords(10));
    }

    @Test
    public void testExtractZeroKeywordsThrows() throws IOException {
        // When / Then
        Text doc = Text.of(text);
        assertThrows(IllegalArgumentException.class, () -> doc.keywords(0));
    }

    @Test
    public void testExtractNegativeKeywordsThrows() throws IOException {
        // When / Then
        Text doc = Text.of(text);
        assertThrows(IllegalArgumentException.class, () -> doc.keywords(-1));
    }

    // -------------------------------------------------------------------
    // Edge-case: document too short to produce qualifying n-grams
    // -------------------------------------------------------------------

    @Test
    public void testExtractShortTextReturnsEmpty() {
        // Given: a two-sentence text that cannot produce n-grams with frequency >= 4
        String text = "The cat sat on the mat. The dog ran in the park.";

        // When
        Text doc = Text.of(text);
        List<NGram> result = doc.keywords(5);

        // Then: empty list, no exception
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
