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
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleParagraphSplitter;
import smile.nlp.tokenizer.SimpleSentenceSplitter;
import smile.nlp.tokenizer.SimpleTokenizer;

/**
 * Tests for {@link NGram}.
 * @author Haifeng Li
 */
public class NGramTest {

    @Test
    public void testExtract() throws IOException {
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
        NGram[][] result = NGram.apriori(sentences, 4, 4);
        assertEquals(5, result.length);
        assertEquals(0, result[0].length);
        assertEquals(331, result[1].length);
        assertEquals(16, result[2].length);
        assertEquals(7, result[3].length);
        assertEquals(0, result[4].length);
    }

    @Test
    public void testNGramRecordAccessors() {
        NGram a = new NGram(new String[]{"machine", "learning"}, 10);
        NGram b = new NGram(new String[]{"deep", "learning"}, 5);
        assertEquals(2, a.words().length);
        assertEquals("machine", a.words()[0]);
        assertEquals("learning", a.words()[1]);
        assertEquals(10, a.count());
        assertTrue(a.compareTo(b) > 0);
        assertTrue(b.compareTo(a) < 0);
        assertEquals(0, a.compareTo(new NGram(new String[]{"a", "b"}, 10)));
        String str = a.toString();
        assertTrue(str.contains("machine"));
        assertTrue(str.contains("learning"));
        assertTrue(str.contains("10"));
    }

    @Test
    public void testNGramEquality() {
        NGram a = new NGram(new String[]{"machine", "learning"}, 10);
        NGram b = new NGram(new String[]{"machine", "learning"}, 99);
        NGram c = new NGram(new String[]{"deep", "learning"}, 10);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void testExtractUnigrams() {
        List<String[]> sentences = List.of(
                new String[]{"machine", "learning", "machine"},
                new String[]{"learning", "machine", "learning"},
                new String[]{"deep", "learning"}
        );
        NGram[][] result = NGram.apriori(sentences, 1, 2);
        assertEquals(2, result.length);
        NGram[] unigrams = result[1];
        assertTrue(unigrams.length >= 2);
        for (int i = 0; i < unigrams.length - 1; i++) {
            assertTrue(unigrams[i].count() >= unigrams[i + 1].count());
        }
    }

    @Test
    public void testExtractFiltersStopWords() {
        List<String[]> sentences = List.of(
                new String[]{"the", "is", "the"},
                new String[]{"is", "the", "is"}
        );
        NGram[][] result = NGram.apriori(sentences, 1, 1);
        assertEquals(2, result.length);
        assertEquals(0, result[1].length);
    }

    @Test
    public void testExtractEmptySentences() {
        NGram[][] result = NGram.apriori(List.of(), 3, 1);
        for (NGram[] ngrams : result) {
            assertEquals(0, ngrams.length);
        }
    }

    @Test
    public void testExtractBigrams() {
        List<String[]> sentences = List.of(
                new String[]{"machine", "learning", "rocks"},
                new String[]{"machine", "learning", "works"},
                new String[]{"machine", "learning", "helps"}
        );
        NGram[][] result = NGram.apriori(sentences, 2, 1);
        assertEquals(3, result.length);
        boolean found = false;
        for (NGram ng : result[2]) {
            if (ng.words()[0].equals("machine") && ng.words()[1].equals("learning")) {
                assertEquals(3, ng.count());
                found = true;
            }
        }
        assertTrue(found);
    }
}
