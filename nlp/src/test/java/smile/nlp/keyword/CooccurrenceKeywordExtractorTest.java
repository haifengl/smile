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

package smile.nlp.keyword;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.nlp.NGram;

/**
 *
 * @author Haifeng Li
 */
public class CooccurrenceKeywordExtractorTest {

    public CooccurrenceKeywordExtractorTest() {
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
     * Test of extract method, of class KeywordExtractorTest.
     */
    @Test
    public void testExtract() throws IOException {
        System.out.println("extract");
        Scanner scanner = new Scanner(smile.util.Paths.getTestData("text/turing.txt"));
        String text = scanner.useDelimiter("\\Z").next();
        scanner.close();

        CooccurrenceKeywordExtractor instance = new CooccurrenceKeywordExtractor();
        ArrayList<NGram> result = instance.extract(text);
        
        assertEquals(10, result.size());
        for (NGram ngram : result) {
            System.out.println(ngram);
        }
    }
}