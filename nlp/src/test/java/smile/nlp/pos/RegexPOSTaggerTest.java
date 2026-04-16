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
 * Tests for {@link RegexPOSTagger}.
 *
 * @author Haifeng Li
 */
public class RegexPOSTaggerTest {

    // -----------------------------------------------------------------------
    // Cardinal numbers → CD
    // -----------------------------------------------------------------------

    @Test
    public void testIntegerIsCardinalNumber() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("123");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.CD), result);
    }

    @Test
    public void testLargeIntegerIsCardinalNumber() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("1234567890");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.CD), result);
    }

    @Test
    public void testDecimalIsCardinalNumber() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("123.45");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.CD), result);
    }

    @Test
    public void testCommaFormattedIntegerIsCardinalNumber() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("1,234");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.CD), result);
    }

    @Test
    public void testCommaFormattedDecimalIsCardinalNumber() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("1,234.5678");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.CD), result);
    }

    // -----------------------------------------------------------------------
    // Phone numbers, extensions, URLs, emails → NN
    // -----------------------------------------------------------------------

    @Test
    public void testFullPhoneNumberIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("914-544-3333");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    @Test
    public void testShortPhoneNumberIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("544-3333");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    @Test
    public void testPhoneExtensionIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("x123");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    @Test
    public void testHttpUrlIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("http://www.msnbc.msn.com/id/42231726/?GT1=43001");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    @Test
    public void testFtpUrlIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("ftp://www.msnbc.msn.com/id/42231726/?GT1=43001");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    @Test
    public void testSimpleEmailIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("nobody@usc.edu");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    @Test
    public void testDottedEmailIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("no.body@usc.edu.cn");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    @Test
    public void testUnderscoreEmailIsNoun() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("no_body@usc.edu.cn");
        // Then
        assertEquals(Optional.of(PennTreebankPOS.NN), result);
    }

    // -----------------------------------------------------------------------
    // Non-matching tokens → empty Optional
    // -----------------------------------------------------------------------

    @Test
    public void testPlainWordReturnsEmpty() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("computer");
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void testPunctuationReturnsEmpty() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag(".");
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void testEmptyStringReturnsEmpty() {
        // Given / When
        Optional<PennTreebankPOS> result = RegexPOSTagger.tag("");
        // Then
        assertTrue(result.isEmpty());
    }
}