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

package smile.nlp.dictionary;

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
public class EnglishPunctuationsTest {

    public EnglishPunctuationsTest() {
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
     * Test of contains method, of class EnglishPunctuations.
     */
    @Test
    public void testContains() {
        System.out.println("contains");
        assertEquals(true, EnglishPunctuations.getInstance().contains("["));
        assertEquals(true, EnglishPunctuations.getInstance().contains("]"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("("));
        assertEquals(true, EnglishPunctuations.getInstance().contains(")"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("{"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("}"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("<"));
        assertEquals(true, EnglishPunctuations.getInstance().contains(">"));
        assertEquals(true, EnglishPunctuations.getInstance().contains(":"));
        assertEquals(true, EnglishPunctuations.getInstance().contains(","));
        assertEquals(true, EnglishPunctuations.getInstance().contains(";"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("-"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("--"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("---"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("!"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("?"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("."));
        assertEquals(true, EnglishPunctuations.getInstance().contains("..."));
        assertEquals(true, EnglishPunctuations.getInstance().contains("`"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("'"));
        assertEquals(true, EnglishPunctuations.getInstance().contains("\""));
        assertEquals(true, EnglishPunctuations.getInstance().contains("/"));
        assertEquals(false, EnglishPunctuations.getInstance().contains(""));
        assertEquals(false, EnglishPunctuations.getInstance().contains("word"));
    }
}