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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PennTreebankPOS}.
 *
 * @author Haifeng Li
 */
public class PennTreebankPOSTest {
    // -----------------------------------------------------------------------
    // getValue() - named tags
    // -----------------------------------------------------------------------
    @Test
    public void testGetValueReturnsNounTag() {
        // Given / When / Then
        assertEquals(PennTreebankPOS.NN, PennTreebankPOS.getValue("NN"));
    }
    @Test
    public void testGetValueReturnsVerbTag() {
        assertEquals(PennTreebankPOS.VBZ, PennTreebankPOS.getValue("VBZ"));
    }
    @Test
    public void testGetValueReturnsPossessivePronoun() {
        // PRP$ contains a $ which is unusual - ensure it round-trips
        assertEquals(PennTreebankPOS.PRP$, PennTreebankPOS.getValue("PRP$"));
    }
    // -----------------------------------------------------------------------
    // getValue() - punctuation symbols mapped to named constants
    // -----------------------------------------------------------------------
    @Test
    public void testGetValuePeriodIsSent() {
        assertEquals(PennTreebankPOS.SENT, PennTreebankPOS.getValue("."));
    }
    @Test
    public void testGetValueQuestionMarkIsSent() {
        assertEquals(PennTreebankPOS.SENT, PennTreebankPOS.getValue("?"));
    }
    @Test
    public void testGetValueExclamationIsSent() {
        assertEquals(PennTreebankPOS.SENT, PennTreebankPOS.getValue("!"));
    }
    @Test
    public void testGetValueCommaIsComma() {
        assertEquals(PennTreebankPOS.COMMA, PennTreebankPOS.getValue(","));
    }
    @Test
    public void testGetValueColonIsColon() {
        assertEquals(PennTreebankPOS.COLON, PennTreebankPOS.getValue(":"));
    }
    @Test
    public void testGetValueSemicolonIsColon() {
        assertEquals(PennTreebankPOS.COLON, PennTreebankPOS.getValue(";"));
    }
    @Test
    public void testGetValueEllipsisIsColon() {
        assertEquals(PennTreebankPOS.COLON, PennTreebankPOS.getValue("..."));
    }
    @Test
    public void testGetValueDashIsDash() {
        assertEquals(PennTreebankPOS.DASH, PennTreebankPOS.getValue("-"));
    }
    @Test
    public void testGetValueHashIsPound() {
        assertEquals(PennTreebankPOS.POUND, PennTreebankPOS.getValue("#"));
    }
    @Test
    public void testGetValueOpenParenIsOpeningParenthesis() {
        assertEquals(PennTreebankPOS.OPENING_PARENTHESIS, PennTreebankPOS.getValue("("));
        assertEquals(PennTreebankPOS.OPENING_PARENTHESIS, PennTreebankPOS.getValue("["));
        assertEquals(PennTreebankPOS.OPENING_PARENTHESIS, PennTreebankPOS.getValue("{"));
    }
    @Test
    public void testGetValueCloseParenIsClosingParenthesis() {
        assertEquals(PennTreebankPOS.CLOSING_PARENTHESIS, PennTreebankPOS.getValue(")"));
        assertEquals(PennTreebankPOS.CLOSING_PARENTHESIS, PennTreebankPOS.getValue("]"));
        assertEquals(PennTreebankPOS.CLOSING_PARENTHESIS, PennTreebankPOS.getValue("}"));
    }
    @Test
    public void testGetValueBacktickIsOpeningQuotation() {
        assertEquals(PennTreebankPOS.OPENING_QUOTATION, PennTreebankPOS.getValue("`"));
        assertEquals(PennTreebankPOS.OPENING_QUOTATION, PennTreebankPOS.getValue("``"));
    }
    @Test
    public void testGetValueSingleQuoteIsClosingQuotation() {
        assertEquals(PennTreebankPOS.CLOSING_QUOTATION, PennTreebankPOS.getValue("'"));
        assertEquals(PennTreebankPOS.CLOSING_QUOTATION, PennTreebankPOS.getValue("''"));
    }
    // -----------------------------------------------------------------------
    // getValue() - unknown tag throws
    // -----------------------------------------------------------------------
    @Test
    public void testGetValueUnknownTagThrows() {
        assertThrows(IllegalArgumentException.class, () -> PennTreebankPOS.getValue("XYZZY"));
    }
    // -----------------------------------------------------------------------
    // open field
    // -----------------------------------------------------------------------
    @Test
    public void testNounIsOpenClass() {
        assertTrue(PennTreebankPOS.NN.open);
    }
    @Test
    public void testVerbIsOpenClass() {
        assertTrue(PennTreebankPOS.VB.open);
    }
    @Test
    public void testAdjectiveIsOpenClass() {
        assertTrue(PennTreebankPOS.JJ.open);
    }
    @Test
    public void testDeterminerIsClosedClass() {
        assertFalse(PennTreebankPOS.DT.open);
    }
    @Test
    public void testConjunctionIsClosedClass() {
        assertFalse(PennTreebankPOS.CC.open);
    }
    // -----------------------------------------------------------------------
    // toString() overrides for punctuation constants
    // -----------------------------------------------------------------------
    @Test
    public void testSentToString() {
        assertEquals(".", PennTreebankPOS.SENT.toString());
    }
    @Test
    public void testCommaToString() {
        assertEquals(",", PennTreebankPOS.COMMA.toString());
    }
    @Test
    public void testColonToString() {
        assertEquals(":", PennTreebankPOS.COLON.toString());
    }
    @Test
    public void testDashToString() {
        assertEquals("-", PennTreebankPOS.DASH.toString());
    }
    @Test
    public void testPoundToString() {
        assertEquals("#", PennTreebankPOS.POUND.toString());
    }
    @Test
    public void testOpeningParenToString() {
        assertEquals("(", PennTreebankPOS.OPENING_PARENTHESIS.toString());
    }
    @Test
    public void testClosingParenToString() {
        assertEquals(")", PennTreebankPOS.CLOSING_PARENTHESIS.toString());
    }
    @Test
    public void testOpeningQuotationToString() {
        assertEquals("``", PennTreebankPOS.OPENING_QUOTATION.toString());
    }
    @Test
    public void testClosingQuotationToString() {
        assertEquals("''", PennTreebankPOS.CLOSING_QUOTATION.toString());
    }
}
