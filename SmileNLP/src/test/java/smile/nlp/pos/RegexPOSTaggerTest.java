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

package smile.nlp.pos;

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
public class RegexPOSTaggerTest {

    public RegexPOSTaggerTest() {
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
     * Test of tag method, of class RegexPOSTagger.
     */
    @Test
    public void testTag() {
        System.out.println("tag");
        assertEquals(PennTreebankPOS.CD, RegexPOSTagger.tag("123"));
        assertEquals(PennTreebankPOS.CD, RegexPOSTagger.tag("1234567890"));
        assertEquals(PennTreebankPOS.CD, RegexPOSTagger.tag("123.45"));
        assertEquals(PennTreebankPOS.CD, RegexPOSTagger.tag("1,234"));
        assertEquals(PennTreebankPOS.CD, RegexPOSTagger.tag("1,234.5678"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("914-544-3333"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("544-3333"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("x123"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("x123"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("http://www.msnbc.msn.com/id/42231726/?GT1=43001"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("ftp://www.msnbc.msn.com/id/42231726/?GT1=43001"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("nobody@usc.edu"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("no.body@usc.edu.cn"));
        assertEquals(PennTreebankPOS.NN, RegexPOSTagger.tag("no_body@usc.edu.cn"));
    }
}