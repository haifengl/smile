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