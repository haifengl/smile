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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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
}