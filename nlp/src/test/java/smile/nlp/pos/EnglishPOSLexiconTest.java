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
package smile.nlp.pos;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EnglishPOSLexicon}.
 *
 * @author Haifeng Li
 */
public class EnglishPOSLexiconTest {

    // -----------------------------------------------------------------------
    // Known-word lookups
    // -----------------------------------------------------------------------

    @Test
    public void testGetNoun() {
        // Given / When
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("1000000000000");
        // Then
        assertTrue(result.isPresent());
        assertEquals(PennTreebankPOS.NN, result.get()[0]);
    }

    @Test
    public void testGetNounPhrase() {
        // Given / When
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("absorbent cotton");
        // Then
        assertTrue(result.isPresent());
        assertEquals(PennTreebankPOS.NN, result.get()[0]);
    }

    @Test
    public void testGetAdjective() {
        // Given / When
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("absorbing");
        // Then
        assertTrue(result.isPresent());
        assertEquals(PennTreebankPOS.JJ, result.get()[0]);
    }

    @Test
    public void testGetVerb() {
        // Given / When
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("displease");
        // Then
        assertTrue(result.isPresent());
        assertEquals(PennTreebankPOS.VB, result.get()[0]);
    }

    @Test
    public void testGetAdverb() {
        // Given / When
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("disposedly");
        // Then
        assertTrue(result.isPresent());
        assertEquals(PennTreebankPOS.RB, result.get()[0]);
    }

    @Test
    public void testGetMultiplePOS() {
        // Given / When: "disperse" has noun, verb and adjective readings
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("disperse");
        // Then
        assertTrue(result.isPresent());
        assertEquals(3, result.get().length);
    }

    // -----------------------------------------------------------------------
    // Unknown-word lookup returns empty Optional (not null)
    // -----------------------------------------------------------------------

    @Test
    public void testGetUnknownWordReturnsEmpty() {
        // Given / When
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("xyzzy_nonexistent_word_42");
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetEmptyStringReturnsEmpty() {
        // Given / When
        Optional<PennTreebankPOS[]> result = EnglishPOSLexicon.get("");
        // Then
        assertTrue(result.isEmpty());
    }
}