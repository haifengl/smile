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
package smile.nlp.normalizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mark Arehart
 */
public class SimpleNormalizerTest {

    private final SimpleNormalizer normalizer = SimpleNormalizer.getInstance();

    /**
     * Integration test covering all normalization steps together.
     */
    @Test
    public void testNormalize() {
        System.out.println("normalize text");
        String text = "\t\u00A0THE BIG RIPOFF\n\n"
                + "Mr. John B. Smith bought cheapsite.com for 1.5 million dollars,\n\r"
                + "i.e. he paid far too much for it.\n\n"
                + "Did he mind?\n\r"
                + "   \t     \n"
                + "Adam Jones Jr. thinks \u201Che\u0301\u201D didn\u2019t.    \n\r\n"
                + "......\n"
                + "In any case, this isn't true... Well, with a probability of .9 it isn't. ";

        String expected = "THE BIG RIPOFF "
                + "Mr. John B. Smith bought cheapsite.com for 1.5 million dollars, "
                + "i.e. he paid far too much for it. "
                + "Did he mind? "
                + "Adam Jones Jr. thinks \"hé\" didn't. "
                + "...... "
                + "In any case, this isn't true... Well, with a probability of .9 it isn't.";

        assertEquals(expected, normalizer.normalize(text));
    }

    /**
     * Test that null input is returned as null.
     */
    @Test
    public void testNormalizeNull() {
        // Given a SimpleNormalizer
        // When normalizing null
        // Then null is returned without throwing
        assertNull(normalizer.normalize(null));
    }

    /**
     * Test that empty string is returned as-is.
     */
    @Test
    public void testNormalizeEmptyString() {
        // Given a SimpleNormalizer
        // When normalizing an empty string
        // Then an empty string is returned
        assertEquals("", normalizer.normalize(""));
    }

    /**
     * Test that already normalized plain ASCII text is returned unchanged.
     */
    @Test
    public void testNormalizeAlreadyNormalized() {
        // Given a SimpleNormalizer and plain ASCII text
        // When normalizing
        // Then the text is returned unchanged (modulo trailing trim)
        String text = "Hello, world.";
        assertEquals(text, normalizer.normalize(text));
    }

    /**
     * Test that multiple consecutive whitespace characters are compressed to a single space.
     */
    @Test
    public void testNormalizeCompressesWhitespace() {
        // Given text with tabs, newlines, and multiple spaces
        // When normalizing
        // Then all runs of whitespace become a single space
        assertEquals("a b c", normalizer.normalize("a   b\t\tc"));
        assertEquals("a b", normalizer.normalize("a\n\nb"));
        assertEquals("a b", normalizer.normalize("a\t  \n b"));
    }

    /**
     * Test that leading and trailing whitespace is removed.
     */
    @Test
    public void testNormalizeTrimming() {
        // Given text with leading and trailing whitespace
        // When normalizing
        // Then leading and trailing whitespace is stripped
        assertEquals("hello", normalizer.normalize("  hello  "));
        assertEquals("hello", normalizer.normalize("\t\nhello\n\t"));
    }

    /**
     * Test that non-breaking space (U+00A0) and other Unicode spaces are compressed.
     */
    @Test
    public void testNormalizeUnicodeWhitespace() {
        // Given text containing non-breaking space (U+00A0) and em-space (U+2003)
        // When normalizing
        // Then they are treated as whitespace and compressed
        assertEquals("a b", normalizer.normalize("a\u00A0b"));
        assertEquals("a b", normalizer.normalize("a\u2003b"));
    }

    /**
     * Test that Unicode curly/fancy double quotes are replaced with ASCII double quote.
     */
    @Test
    public void testNormalizeDoubleQuotes() {
        // Given text containing various Unicode double quote characters
        // When normalizing
        // Then all are replaced with the ASCII double quote "
        assertEquals("\"hello\"", normalizer.normalize("\u201Chello\u201D")); // " "
        assertEquals("\"test\"",  normalizer.normalize("\u201Etest\u201F")); // „ ‟
        assertEquals("\"hi\"",    normalizer.normalize("\uFF02hi\uFF02")); // ＂
    }

    /**
     * Test that Unicode curly/fancy single quotes are replaced with ASCII apostrophe.
     */
    @Test
    public void testNormalizeSingleQuotes() {
        // Given text containing various Unicode single quote characters
        // When normalizing
        // Then all are replaced with ASCII apostrophe '
        assertEquals("it's",   normalizer.normalize("it\u2019s")); // right single quotation mark
        assertEquals("'hello'", normalizer.normalize("\u2018hello\u2019")); // ' '
        assertEquals("'test'",  normalizer.normalize("\u275Btest\u275C")); // ❛ ❜
    }

    /**
     * Test that Unicode dashes are replaced with double hyphen.
     */
    @Test
    public void testNormalizeDashes() {
        // Given text containing various Unicode dash characters
        // When normalizing
        // Then all are replaced with "--"
        assertEquals("a--b", normalizer.normalize("a\u2013b")); // en dash
        assertEquals("a--b", normalizer.normalize("a\u2014b")); // em dash
        assertEquals("a--b", normalizer.normalize("a\u2015b")); // horizontal bar
        assertEquals("a--b", normalizer.normalize("a\u2012b")); // figure dash
    }

    /**
     * Test that control characters (Cc) and format characters (Cf) are removed.
     */
    @Test
    public void testNormalizeRemovesControlChars() {
        // Given text containing a null byte and zero-width non-joiner (Cf)
        // When normalizing
        // Then control and format characters are stripped
        assertEquals("ab", normalizer.normalize("a\u0000b"));       // null byte (Cc)
        assertEquals("ab", normalizer.normalize("a\u200Bb"));       // zero-width space (Cf)
        assertEquals("hello", normalizer.normalize("hel\u007Flo")); // DEL control char
    }

    /**
     * Test that NFKC Unicode normalization is applied (e.g., ligatures decomposed).
     */
    @Test
    public void testNormalizeNFKC() {
        // Given text containing the fi ligature (U+FB01) which NFKC decomposes to "fi"
        // When normalizing
        // Then the ligature is decomposed
        assertEquals("fi", normalizer.normalize("\uFB01")); // ﬁ → fi
        // Fullwidth digits should be normalized to ASCII
        assertEquals("123", normalizer.normalize("\uFF11\uFF12\uFF13")); // １２３ → 123
    }

    /**
     * Test the apply() method from the UnaryOperator / Function interface.
     */
    @Test
    public void testApplyFunctionInterface() {
        // Given a Normalizer used as UnaryOperator<String>
        // When calling apply()
        // Then it produces the same result as normalize()
        Normalizer n = SimpleNormalizer.getInstance();
        String text = "  hello\u2014world  ";
        assertEquals(n.normalize(text), n.apply(text));
    }

    /**
     * Test that combining diacritical marks are preserved after NFKC normalization.
     */
    @Test
    public void testNormalizePreservesDiacritics() {
        // Given a string with a composed accented character
        // When normalizing
        // Then the character is preserved (NFKC composes where possible)
        String composed   = "caf\u00E9";       // café (precomposed)
        String decomposed = "cafe\u0301";      // cafe + combining acute
        // NFKC normalizes decomposed to composed form
        assertEquals(composed, normalizer.normalize(decomposed));
        assertEquals(composed, normalizer.normalize(composed));
    }
}
