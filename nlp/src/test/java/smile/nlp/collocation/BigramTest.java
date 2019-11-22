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

import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.nlp.SimpleCorpus;
import smile.nlp.Text;

/**
 *
 * @author Haifeng Li
 */
public class BigramTest {
    SimpleCorpus corpus = new SimpleCorpus();

    public BigramTest() {
        try {
            smile.util.Paths.getTestDataLines("text/plot.tok.gt9.5000")
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> corpus.add(new Text(line)));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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
    public void testFind_Corpus_int() {
        System.out.println("k = 10");

        int k = 10;
        Bigram[] result = Bigram.of(corpus, k, 5);

        assertEquals(10, result.length);
        for (int i = 0; i < result.length; i++) {
            System.out.println(result[i]);
        }

        assertEquals(46, result[0].count);
        assertEquals(545.16, result[0].score, 1E-2);
        assertEquals(19, result[9].count);
        assertEquals(186.69, result[9].score, 1E-2);
    }

    @Test
    public void testFind_Corpus_double() {
        System.out.println("p = 0.0001");

        double p = 0.0001;
        Bigram[] result = Bigram.of(corpus, p, 5);

        assertEquals(63, result.length);
        for (int i = 0; i < result.length; i++) {
            System.out.println(result[i]);
        }

        assertEquals(46, result[0].count);
        assertEquals(545.16, result[0].score, 1E-2);
        assertEquals(6, result[62].count);
        assertEquals(10.84, result[62].score, 1E-2);
    }
}