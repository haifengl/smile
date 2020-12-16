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

package smile.nlp;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.nlp.relevance.BM25;
import smile.nlp.relevance.Relevance;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SimpleCorpusTest {
    SimpleCorpus corpus = new SimpleCorpus();

    public SimpleCorpusTest() {
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
    public void testSize() {
        System.out.println("size");
        assertEquals(58064, corpus.size());
    }

    @Test
    public void testGetNumDocuments() {
        System.out.println("getNumDocuments");
        assertEquals(5000, corpus.ndoc());
    }

    @Test
    public void testGetNumTerms() {
        System.out.println("getNumTerms");
        assertEquals(15077, corpus.nterm());
    }

    @Test
    public void testGetNumBigrams() {
        System.out.println("getNumBigrams");
        assertEquals(18303, corpus.nbigram());
    }

    @Test
    public void testGetAverageDocumentSize() {
        System.out.println("getAverageDocumentSize");
        assertEquals(11, corpus.avgDocSize());
    }

    @Test
    public void testGetTermFrequency() {
        System.out.println("getTermFrequency");
        assertEquals(27, corpus.count("romantic"));
    }

    @Test
    public void testGetBigramFrequency() {
        System.out.println("getBigramFrequency");
        Bigram bigram = new Bigram("romantic", "comedy");
        assertEquals(9, corpus.count(bigram));
    }

    @Test
    public void testSearchRomantic() {
        System.out.println("search 'romantic'");
        Iterator<Relevance> hits = corpus.search(new BM25(), "romantic");
        int n = 0;
        while (hits.hasNext()) {
            n++;
            Relevance hit = hits.next();
            System.out.println(hit.text + "\t" + hit.score);
        }
        assertEquals(27, n);
    }

    @Test
    public void testSearchNoResult() {
        System.out.println("search 'romantic'");
        Iterator<Relevance> hits = corpus.search(new BM25(), "find");
        assertEquals(Collections.emptyIterator(),hits);
    }

    @Test
    public void testSearchRomanticComedy() {
        System.out.println("search 'romantic comedy'");
        String[] terms = {"romantic", "comedy"};
        Iterator<Relevance> hits = corpus.search(new BM25(), terms);
        int n = 0;
        while (hits.hasNext()) {
            n++;
            Relevance hit = hits.next();
            System.out.println(hit.text + "\t" + hit.score);
        }
        assertEquals(78, n);
    }

    @Test
    public void testSearchNoHits() {
        System.out.println("search 'no hits'");
        String[] terms = {"thisisnotaword"};
        Iterator<Relevance> hits = corpus.search(new BM25(), terms);
        assertFalse(hits.hasNext());
    }
}