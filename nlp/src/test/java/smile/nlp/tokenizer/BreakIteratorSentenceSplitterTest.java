/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.nlp.tokenizer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class BreakIteratorSentenceSplitterTest {

    public BreakIteratorSentenceSplitterTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
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
}