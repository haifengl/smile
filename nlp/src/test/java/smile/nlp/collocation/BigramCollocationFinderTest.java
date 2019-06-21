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

package smile.nlp.collocation;

import java.io.BufferedReader;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.nlp.SimpleCorpus;

/**
 *
 * @author Haifeng Li
 */
public class BigramCollocationFinderTest {

    public BigramCollocationFinderTest() {
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
     * Test of find method, of class BigramCollocationFinder.
     */
    @Test
    public void testFind_Corpus_int() {
        System.out.println("find");
        SimpleCorpus corpus = new SimpleCorpus();

        try (BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("text/quote.tok.gt9.5000")) {
            String line = null;
            int id = 0;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    corpus.add(String.valueOf(id++), null, line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        int k = 10;
        BigramCollocationFinder instance = new BigramCollocationFinder(5);
        BigramCollocation[] result = instance.find(corpus, k);

        for (int i = 0; i < result.length; i++) {
            System.out.println(result[i]);
        }
    }

    /**
     * Test of find method, of class BigramCollocationFinder.
     */
    @Test
    public void testFind_Corpus_double() {
        System.out.println("find");
        SimpleCorpus corpus = new SimpleCorpus();

        try (BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("text/quote.tok.gt9.5000")) {
            String line = null;
            int id = 0;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    corpus.add(String.valueOf(id), null, line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        double p = 0.0001;
        BigramCollocationFinder instance = new BigramCollocationFinder(5);
        BigramCollocation[] result = instance.find(corpus, p);

        assertEquals(52, result.length);
        for (int i = 0; i < result.length; i++) {
            System.out.println(result[i]);
        }
    }
}