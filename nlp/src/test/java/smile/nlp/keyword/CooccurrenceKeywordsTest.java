/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.nlp.keyword;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.nlp.collocation.NGram;

/**
 *
 * @author Haifeng Li
 */
public class CooccurrenceKeywordsTest {

    public CooccurrenceKeywordsTest() {
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

    @Test
    public void testExtract() throws IOException {
        System.out.println("keywords");
        String text = new String(Files.readAllBytes(smile.util.Paths.getTestData("text/turing.txt")));

        NGram[] result = CooccurrenceKeywords.of(text);
        
        assertEquals(10, result.length);
        for (NGram ngram : result) {
            System.out.println(ngram);
        }

        assertEquals("store", result[0].words[0]);
        assertEquals(18, result[0].count);
        assertEquals("digital computer", String.join(" ", result[1].words));
        assertEquals(34, result[1].count);
        assertEquals("machine", result[2].words[0]);
        assertEquals(198, result[2].count);
        assertEquals("storage capacity", String.join(" ", result[3].words));
        assertEquals(11, result[3].count);
        assertEquals("instruction", result[4].words[0]);
        assertEquals(14, result[4].count);
        assertEquals("think", result[5].words[0]);
        assertEquals(46, result[5].count);
        assertEquals("imitation game", String.join(" ", result[6].words));
        assertEquals(15, result[6].count);
        assertEquals("discrete-state machine", String.join(" ", result[7].words));
        assertEquals(17, result[7].count);
        assertEquals("teach", result[8].words[0]);
        assertEquals(11, result[8].count);
        assertEquals("interrogator", result[9].words[0]);
        assertEquals(25, result[9].count);
    }
}