/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
public class SimpleSentenceSplitterTest {

    public SimpleSentenceSplitterTest() {
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
     * Test of split method, of class SimpleSentenceSplitter.
     */
    @Test
    public void testSplit() {
        System.out.println("split");
        String text = "THE BIG RIPOFF\n\n"
                + "Mr. John B. Smith bought www.cheap.com for 1.5 million dollars, "
                + "i.e. he paid far too much for it.Did he mind? "
                + "Adam Jones Jr. thinks he didn't. In any case, this isn't true..."
                + "Well, it isn't with a probability of .9.Right?"
                + "Again, it isn't with a probability of .9 .Right?"
                + "[This is bracketed sentence.] "
                + "\"This is quoted sentence.\" "
                + "This last sentence has no period";
        
        String[] expResult = {
            "THE BIG RIPOFF Mr. John B. Smith bought www.cheap.com for 1.5 million dollars, i.e. he paid far too much for it.",
            "Did he mind?",
            "Adam Jones Jr. thinks he didn't.",
            "In any case, this isn't true...",
            "Well, it isn't with a probability of .9.",
            "Right?",
            "Again, it isn't with a probability of .9.",
            "Right?",
            "[This is bracketed sentence.]",
            "\"This is quoted sentence.\"",
            "This last sentence has no period"
        };

        SimpleSentenceSplitter instance = SimpleSentenceSplitter.getInstance();
        String[] result = instance.split(text);
        
        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++)
            assertEquals(expResult[i], result[i]);
    }

    /**
     * Test of split method, of class SimpleSentenceSplitter.
     */
    @Test
    public void testSplitUnicode() {
        System.out.println("split with unicode chars");
        String text = "THE BIG RIPOFF\n\n"
                + "Mr. John B. Smith bought www.cheap.com for 1.5 million dollars, "
                + "i.e. he paid far too much for it.Did he mind? "
                + "Adam Jones Jr. thinks he didn't. In any case, this isn't true..."
                + "Well, it isn't with a probability of .9.Right?"
                + "Again, it isn't with a probability of .9 .Right?"
                + "[This is bracketed sentence.] "
                + "\"This is quoted sentence.\" "
                + "This last sentence has no period";

        String[] expResult = {
                "THE BIG RIPOFF Mr. John B. Smith bought www.cheap.com for 1.5 million dollars, i.e. he paid far too much for it.",
                "Did he mind?",
                "Adam Jones Jr. thinks he didn't.",
                "In any case, this isn't true...",
                "Well, it isn't with a probability of .9.",
                "Right?",
                "Again, it isn't with a probability of .9.",
                "Right?",
                "[This is bracketed sentence.]",
                "\"This is quoted sentence.\"",
                "This last sentence has no period"
        };

        SimpleSentenceSplitter instance = SimpleSentenceSplitter.getInstance();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++)
            assertEquals(expResult[i], result[i]);
    }

    /**
     * Test of split method, of class SimpleSentenceSplitter.
     */
    @Test
    public void testSplitEndWithAbbreviation() {
        System.out.println("split with abbreviation ending");
        String text1 = "This is an nn. This is the next sentence.";
        String text2 = "This is an nn. this is the next sentence.";
        String text3 = "This is an na. This is the next sentence.";

        String[] expResult1 = {"This is an nn. This is the next sentence."};
        String[] expResult2 = {"This is an nn. this is the next sentence."};
        String[] expResult3 = {"This is an na.", "This is the next sentence."};

        SimpleSentenceSplitter instance = SimpleSentenceSplitter.getInstance();

        String[] result1 = instance.split(text1);
        assertEquals(expResult1.length, result1.length);
        for (int i = 0; i < result1.length; i++)
            assertEquals(expResult1[i], result1[i]);

        String[] result2 = instance.split(text2);
        assertEquals(expResult2.length, result2.length);
        for (int i = 0; i < result2.length; i++)
            assertEquals(expResult2[i], result2[i]);

        String[] result3 = instance.split(text3);
        assertEquals(expResult3.length, result3.length);
        for (int i = 0; i < result3.length; i++)
            assertEquals(expResult3[i], result3[i]);
    }
}