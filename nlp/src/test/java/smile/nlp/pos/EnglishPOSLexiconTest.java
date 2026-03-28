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
package smile.nlp.pos;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class EnglishPOSLexiconTest {

    public EnglishPOSLexiconTest() {
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