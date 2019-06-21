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