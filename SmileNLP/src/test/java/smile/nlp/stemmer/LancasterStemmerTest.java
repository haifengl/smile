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

package smile.nlp.stemmer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class LancasterStemmerTest {

    public LancasterStemmerTest() {
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
     * Test of stem method, of class LancasterStemmer.
     */
    @Test
    public void testStem() {
        System.out.println("stem");
        String[] words = {"consign", "consigned", "consigning", "consignment",
            "consist", "consisted", "consistency", "consistent", "consistently",
            "consisting", "consists", "consolation", "consolations", "consolatory",
            "console", "consoled", "consoles", "consolidate", "consolidated",
            "consolidating", "consoling", "consolingly", "consols", "consonant",
            "consort", "consorted", "consorting", "conspicuous", "conspicuously",
            "conspiracy", "conspirator", "conspirators", "conspire", "conspired",
            "conspiring", "constable", "constables", "constance", "constancy",
            "constant", "knack", "knackeries", "knacks", "knag", "knave",
            "knaves", "knavish", "kneaded", "kneading", "knee", "kneel",
            "kneeled", "kneeling", "kneels", "knees", "knell", "knelt", "knew",
            "knick", "knif", "knife", "knight", "knightly", "knights", "knit",
            "knits", "knitted", "knitting", "knives", "knob", "knobs", "knock",
            "knocked", "knocker", "knockers", "knocking", "knocks", "knopp",
            "knot", "knots"
        };

        String[] expResult = {"consign", "consign", "consign", "consign",
            "consist", "consist", "consist", "consist", "consist", "consist",
            "consist", "consol", "consol", "consol", "consol", "consol",
            "consol", "consolid", "consolid", "consolid", "consol", "consol",
            "consol", "conson", "consort", "consort", "consort", "conspicu",
            "conspicu", "conspir", "conspir", "conspir", "conspir", "conspir",
            "conspir", "const", "const", "const", "const", "const", "knack",
            "knackery", "knack", "knag", "knav", "knav", "knav", "knead",
            "knead", "kne", "kneel", "kneel", "kneel", "kneel", "kne", "knel",
            "knelt", "knew", "knick", "knif", "knif", "knight", "knight",
            "knight", "knit", "knit", "knit", "knit", "kniv", "knob", "knob",
            "knock", "knock", "knock", "knock", "knock", "knock", "knop",
            "knot", "knot"
        };

        LancasterStemmer instance = new LancasterStemmer();
        for (int i = 0; i < words.length; i++) {
            String result = instance.stem(words[i]);
            assertEquals(expResult[i], result);
        }
    }

}