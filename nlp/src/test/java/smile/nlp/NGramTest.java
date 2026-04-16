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
package smile.nlp;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleParagraphSplitter;
import smile.nlp.tokenizer.SimpleSentenceSplitter;
import smile.nlp.tokenizer.SimpleTokenizer;

/**
 *
 * @author Haifeng Li
 */
public class  NGramTest {

    public NGramTest() {
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

    @Test
    public void testExtract() throws IOException {
        System.out.println("n-gram extraction");
        String text = new String(Files.readAllBytes(smile.io.Paths.getTestData("text/turing.txt")));

        PorterStemmer stemmer = new PorterStemmer();
        SimpleTokenizer tokenizer = new SimpleTokenizer();
        ArrayList<String[]> sentences = new ArrayList<>();
        for (String paragraph : SimpleParagraphSplitter.getInstance().split(text)) {
            for (String s : SimpleSentenceSplitter.getInstance().split(paragraph)) {
                String[] sentence = tokenizer.split(s);
                for (int i = 0; i < sentence.length; i++) {
                    sentence[i] = stemmer.stripPluralParticiple(sentence[i]).toLowerCase();
                }
                sentences.add(sentence);
            }
        }

        NGram[][] result = NGram.apiori(sentences, 4, 4);

        assertEquals(5, result.length);
        for (NGram[] ngrams : result) {
            for (NGram ngram : ngrams) {
                System.out.println(ngram);
            }
            System.out.println();
        }

        assertEquals(0, result[0].length);
        assertEquals(331, result[1].length);
        assertEquals(16, result[2].length);
        assertEquals(7, result[3].length);
        assertEquals(0, result[4].length);
    }

    /**
     * Test NGram model class: constructor, fields, toString, compareTo.
     */
    @Test
    public void testNGramModel() {
        // Given two NGram objects with different counts
        // When comparing and printing
        // Then compareTo is by count ascending and toString shows words
        NGram a = new NGram(new String[]{"machine", "learning"}, 10);
        NGram b = new NGram(new String[]{"deep", "learning"}, 5);

        assertEquals(2, a.words().length);
        assertEquals("machine", a.words()[0]);
        assertEquals("learning", a.words()[1]);
        assertEquals(10, a.count());

        assertTrue(a.compareTo(b) > 0, "higher count should be greater");
        assertTrue(b.compareTo(a) < 0, "lower count should be less");
        assertEquals(0, a.compareTo(new NGram(new String[]{"a", "b"}, 10)));

        String str = a.toString();
        assertTrue(str.contains("machine"), "toString should contain first word");
        assertTrue(str.contains("learning"), "toString should contain second word");
        assertTrue(str.contains("10"), "toString should contain count");
    }

    /**
     * Test NGram.of with a controlled small corpus to verify unigram counts.
     */
    @Test
    public void testExtractUnigrams() {
        // Given sentences with known repeated words
        // When extracting n-grams with maxNGramSize=1 and minFrequency=2
        // Then unigrams appearing >= 3 times are found (strictly > 2)
        List<String[]> sentences = List.of(
                new String[]{"machine", "learning", "machine"},
                new String[]{"learning", "machine", "learning"},
                new String[]{"deep", "learning"}
        );
        NGram[][] result = NGram.apiori(sentences, 1, 2);

        // result[0] = empty placeholder, result[1] = unigrams
        assertEquals(2, result.length);
        // "machine" appears 3 times, "learning" appears 3 times, "deep" only 1 time
        // Both machine and learning should pass minFrequency=2 (count > 2, i.e. >= 3)
        NGram[] unigrams = result[1];
        assertTrue(unigrams.length >= 2);
        // Should be sorted in descending order by count
        for (int i = 0; i < unigrams.length - 1; i++) {
            assertTrue(unigrams[i].count() >= unigrams[i + 1].count());
        }
    }

    /**
     * Test NGram.of stop-word filtering: unigrams that are stop words are excluded.
     */
    @Test
    public void testExtractFiltersStopWords() {
        // Given sentences containing only stop words
        // When extracting n-grams
        // Then all results are filtered out (empty)
        List<String[]> sentences = List.of(
                new String[]{"the", "is", "the"},
                new String[]{"is", "the", "is"}
        );
        NGram[][] result = NGram.apiori(sentences, 1, 1);
        assertEquals(2, result.length);
        // "the" and "is" are stop words - should be filtered
        assertEquals(0, result[1].length);
    }

    /**
     * Test NGram.of with an empty sentence collection.
     */
    @Test
    public void testExtractEmptySentences() {
        // Given an empty sentence list
        // When extracting n-grams
        // Then all n-gram sets are empty
        NGram[][] result = NGram.apiori(List.of(), 3, 1);
        for (NGram[] ngrams : result) {
            assertEquals(0, ngrams.length);
        }
    }

    /**
     * Test NGram.of: bigrams appear only if their component unigrams also pass minFrequency.
     */
    @Test
    public void testExtractBigrams() {
        // Given sentences where a bigram "machine learning" appears multiple times
        // When extracting with maxNGramSize=2 and minFrequency=1
        // Then the bigram is found
        List<String[]> sentences = List.of(
                new String[]{"machine", "learning", "rocks"},
                new String[]{"machine", "learning", "works"},
                new String[]{"machine", "learning", "helps"}
        );
        NGram[][] result = NGram.apiori(sentences, 2, 1);
        assertEquals(3, result.length);

        // result[2] should contain bigrams; "machine learning" should be present with count 3
        boolean found = false;
        for (NGram ng : result[2]) {
            if (ng.words()[0].equals("machine") && ng.words()[1].equals("learning")) {
                assertEquals(3, ng.count());
                found = true;
            }
        }
        assertTrue(found, "Expected bigram 'machine learning' in results");
    }
}