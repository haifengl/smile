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

package smile.nlp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;

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
            smile.util.Paths.getTestDataLines("text/quote.tok.gt9.5000")
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> corpus.add(UUID.randomUUID().toString(), null, line));
        } catch (IOException ex) {
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

    /**
     * Test of size method, of class SimpleCorpus.
     */
    @Test
    public void testSize() {
        System.out.println("size");
        assertEquals(51797, corpus.size());
    }

    /**
     * Test of getNumDocuments method, of class SimpleCorpus.
     */
    @Test
    public void testGetNumDocuments() {
        System.out.println("getNumDocuments");
        assertEquals(5000, corpus.getNumDocuments());
    }

    /**
     * Test of getNumTerms method, of class SimpleCorpus.
     */
    @Test
    public void testGetNumTerms() {
        System.out.println("getNumTerms");
        assertEquals(14335, corpus.getNumTerms());
    }

    /**
     * Test of getNumBigrams method, of class SimpleCorpus.
     */
    @Test
    public void testGetNumBigrams() {
        System.out.println("getNumBigrams");
        assertEquals(17121, corpus.getNumBigrams());
    }

    /**
     * Test of getAverageDocumentSize method, of class SimpleCorpus.
     */
    @Test
    public void testGetAverageDocumentSize() {
        System.out.println("getAverageDocumentSize");
        assertEquals(10, corpus.getAverageDocumentSize());
    }

    /**
     * Test of getTermFrequency method, of class SimpleCorpus.
     */
    @Test
    public void testGetTermFrequency() {
        System.out.println("getTermFrequency");
        assertEquals(50, corpus.getTermFrequency("romantic"));
    }

    /**
     * Test of getBigramFrequency method, of class SimpleCorpus.
     */
    @Test
    public void testGetBigramFrequency() {
        System.out.println("getBigramFrequency");
        Bigram bigram = new Bigram("romantic", "comedy");
        assertEquals(29, corpus.getBigramFrequency(bigram));
    }

    /**
     * Test of search method, of class SimpleCorpus.
     */
    @Test
    public void testSearch() {
        System.out.println("search 'romantic'");
        Iterator<Relevance> hits = corpus.search(new BM25(), "romantic");
        while (hits.hasNext()) {
            Relevance hit = hits.next();
            System.out.println(hit.doc() + "\t" + hit.score());
        }
    }

    /**
     * Test of search method, of class SimpleCorpus, without hits.
     */
    @Test
    public void testSearchNoResult() {
        System.out.println("search 'romantic'");
        Iterator<Relevance> hits = corpus.search(new BM25(), "find");
        assertEquals(Collections.emptyIterator(),hits);
    }


    /**
     * Test of search method, of class SimpleCorpus.
     */
    @Test
    public void testSearch2() {
        System.out.println("search 'romantic comedy'");
        String[] terms = {"romantic", "comedy"};
        Iterator<Relevance> hits = corpus.search(new BM25(), terms);
        while (hits.hasNext()) {
            Relevance hit = hits.next();
            System.out.println(hit.doc() + "\t" + hit.score());
        }
    }

    /**
     * Test of search method, of class SimpleCorpus.
     */
    @Test
    public void testSearch2WithNoHits() {
        System.out.println("search 'no hits'");
        String[] terms = {"thisisnotaword"};
        Iterator<Relevance> hits = corpus.search(new BM25(), terms);
        assertEquals(false, hits.hasNext());
    }
}