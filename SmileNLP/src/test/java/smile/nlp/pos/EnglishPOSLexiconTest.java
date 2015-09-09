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
public class EnglishPOSLexiconTest {

    public EnglishPOSLexiconTest() {
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
     * Test of get method, of class EnglishPOSLexicon.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(PennTreebankPOS.NN, EnglishPOSLexicon.get("1000000000000")[0]);
        assertEquals(PennTreebankPOS.NN, EnglishPOSLexicon.get("absorbent cotton")[0]);
        assertEquals(PennTreebankPOS.JJ, EnglishPOSLexicon.get("absorbing")[0]);
        assertEquals(PennTreebankPOS.VB, EnglishPOSLexicon.get("displease")[0]);
        assertEquals(PennTreebankPOS.RB, EnglishPOSLexicon.get("disposedly")[0]);
        assertEquals(3, EnglishPOSLexicon.get("disperse").length);
    }
}