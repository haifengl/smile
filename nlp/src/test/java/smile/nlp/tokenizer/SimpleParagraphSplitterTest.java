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
package smile.nlp.tokenizer;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SimpleParagraphSplitterTest {

    public SimpleParagraphSplitterTest() {
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
     * Test of split method, of class SimpleEnglishParagraphSplitter.
     */
    @Test
    public void testSplit() {
        System.out.println("split");
        String text = "THE BIG RIPOFF\n\n"
                + "Mr. John B. Smith bought cheapsite.com for 1.5 million dollars,\n\r"
                + "i.e. he paid far too much for it.\n\n"
                + "Did he mind?\n\r"
                + "   \t     \n"
                + "Adam Jones Jr. thinks he didn't.    \n\r\n"
                + "......\n"
                + "In any case, this isn't true... Well, with a probability of .9 it isn't. ";

        String[] expResult = {
            "THE BIG RIPOFF",
            "Mr. John B. Smith bought cheapsite.com for 1.5 million dollars,\n\ri.e. he paid far too much for it.",
            "Did he mind?",
            "Adam Jones Jr. thinks he didn't.    ",
            "......\nIn any case, this isn't true... Well, with a probability of .9 it isn't. "
        };

        SimpleParagraphSplitter instance = SimpleParagraphSplitter.getInstance();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++)
            assertEquals(expResult[i], result[i]);
    }

    /**
     * Test that a single paragraph with no blank lines is returned as-is.
     */
    @Test
    public void testSplitSingleParagraph() {
        // Given a string with no blank lines
        // When splitting into paragraphs
        // Then it should return a single paragraph
        String text = "This is the only paragraph. It has multiple sentences.";
        SimpleParagraphSplitter instance = SimpleParagraphSplitter.getInstance();
        String[] result = instance.split(text);
        assertEquals(1, result.length);
        assertEquals(text, result[0]);
    }

    /**
     * Test that a Windows-style CRLF blank line acts as a paragraph boundary.
     */
    @Test
    public void testSplitWithCRLF() {
        // Given a string using Windows-style CRLF line endings
        // When splitting into paragraphs
        // Then it should correctly detect paragraph boundaries
        String text = "First paragraph.\r\n\r\nSecond paragraph.";
        SimpleParagraphSplitter instance = SimpleParagraphSplitter.getInstance();
        String[] result = instance.split(text);
        assertEquals(2, result.length);
        assertEquals("First paragraph.", result[0]);
        assertEquals("Second paragraph.", result[1]);
    }

    /**
     * Test that the Unicode paragraph-separator character (U+2029) splits paragraphs.
     */
    @Test
    public void testSplitWithUnicodeParagraphSeparator() {
        // Given a string containing the Unicode paragraph separator
        // When splitting into paragraphs
        // Then each part separated by U+2029 should be a paragraph
        String text = "First paragraph.\u2029Second paragraph.";
        SimpleParagraphSplitter instance = SimpleParagraphSplitter.getInstance();
        String[] result = instance.split(text);
        assertEquals(2, result.length);
        assertEquals("First paragraph.", result[0]);
        assertEquals("Second paragraph.", result[1]);
    }

    /**
     * Test that multiple consecutive blank lines collapse to one paragraph boundary.
     */
    @Test
    public void testSplitMultipleBlankLines() {
        // Given a string with three blank lines between paragraphs
        // When splitting
        // Then the result should be the same as with one blank line (two paragraphs)
        String text = "Para one.\n\n\n\nPara two.";
        SimpleParagraphSplitter instance = SimpleParagraphSplitter.getInstance();
        String[] result = instance.split(text);
        assertEquals(2, result.length);
        assertEquals("Para one.", result[0]);
        assertEquals("Para two.", result[1]);
    }

    /**
     * Test the apply() method from the Function interface.
     */
    @Test
    public void testApplyFunctionInterface() {
        // Given a ParagraphSplitter used as a Function
        // When calling apply()
        // Then it delegates to split() correctly
        String text = "Para A.\n\nPara B.";
        ParagraphSplitter splitter = SimpleParagraphSplitter.getInstance();
        String[] result = splitter.apply(text);
        assertEquals(2, result.length);
        assertEquals("Para A.", result[0]);
        assertEquals("Para B.", result[1]);
    }
}