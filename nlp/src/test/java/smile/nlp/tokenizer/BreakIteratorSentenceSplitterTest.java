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
public class BreakIteratorSentenceSplitterTest {

    public BreakIteratorSentenceSplitterTest() {
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
     * Test of split method, of class BreakIteratorSentenceSplitter.
     */
    @Test
    public void testSplit() {
        System.out.println("split");
        String text = "THE BIG RIPOFF\n\nMr. John B. Smith bought cheapsite.com for 1.5 million dollars, i.e. he paid far too much for it. Did he mind? Adam Jones Jr. thinks he didn't. In any case, this isn't true... Well, with a probability of .9 it isn't. ";
        String[] expResult = {
            "THE BIG RIPOFF\n\nMr.",
            "John B.",
            "Smith bought cheapsite.com for 1.5 million dollars, i.e. he paid far too much for it.",
            "Did he mind?",
            "Adam Jones Jr. thinks he didn't.",
            "In any case, this isn't true...",
            "Well, with a probability of .9 it isn't."
        };

        BreakIteratorSentenceSplitter instance = new BreakIteratorSentenceSplitter();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++)
            assertEquals(expResult[i], result[i]);
    }

    /**
     * Test that a single sentence is returned whole.
     */
    @Test
    public void testSplitSingleSentence() {
        // Given a string that is a single sentence
        // When splitting
        // Then a single element is returned
        String text = "This is a single sentence.";
        BreakIteratorSentenceSplitter instance = new BreakIteratorSentenceSplitter();
        String[] result = instance.split(text);
        assertEquals(1, result.length);
        assertEquals("This is a single sentence.", result[0]);
    }

    /**
     * Test that the apply() method from Function interface delegates correctly.
     */
    @Test
    public void testApplyFunctionInterface() {
        // Given a SentenceSplitter used as a Function
        // When calling apply()
        // Then it returns the same result as split()
        String text = "Hello world. How are you?";
        BreakIteratorSentenceSplitter splitter = new BreakIteratorSentenceSplitter();
        String[] viaSplit = splitter.split(text);
        String[] viaApply = splitter.apply(text);
        assertArrayEquals(viaSplit, viaApply);
    }

    /**
     * Test splitting with a specific locale.
     */
    @Test
    public void testSplitWithLocale() {
        // Given a sentence splitter constructed with Locale.ENGLISH
        // When splitting typical English text
        // Then it should produce correct results
        String text = "Hello. World.";
        BreakIteratorSentenceSplitter instance = new BreakIteratorSentenceSplitter(java.util.Locale.ENGLISH);
        String[] result = instance.split(text);
        assertTrue(result.length >= 1, "Expected at least one sentence");
    }
}