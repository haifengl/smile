/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.nlp.collocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.nlp.NGram;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleParagraphSplitter;
import smile.nlp.tokenizer.SimpleSentenceSplitter;
import smile.nlp.tokenizer.SimpleTokenizer;

/**
 *
 * @author Haifeng Li
 */
public class AprioriPhraseExtractorTest {

    public AprioriPhraseExtractorTest() {
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
     * Test of extract method, of class AprioriPhraseExtractorTest.
     */
    @Test
    public void testExtract() {
        System.out.println("extract");
        Scanner scanner = new Scanner(this.getClass().getResourceAsStream("/smile/data/text/turing.txt"));
        String text = scanner.useDelimiter("\\Z").next();
        scanner.close();
        
        PorterStemmer stemmer = new PorterStemmer();
        SimpleTokenizer tokenizer = new SimpleTokenizer();
        ArrayList<String[]> sentences = new ArrayList<String[]>();
        for (String paragraph : SimpleParagraphSplitter.getInstance().split(text)) {
            for (String s : SimpleSentenceSplitter.getInstance().split(paragraph)) {
                String[] sentence = tokenizer.split(s);
                for (int i = 0; i < sentence.length; i++) {
                    if (stemmer.stripPluralParticiple(sentence[i]).toLowerCase().equals("")) {
                        System.out.println(Arrays.toString(sentence));
                    }
                    sentence[i] = stemmer.stripPluralParticiple(sentence[i]).toLowerCase();
                }
                sentences.add(sentence);
            }
        }

        AprioriPhraseExtractor instance = new AprioriPhraseExtractor();
        ArrayList<ArrayList<NGram>> result = instance.extract(sentences, 4, 4);

        assertEquals(5, result.size());
        for (ArrayList<NGram> ngrams : result) {
        	for (NGram ngram : ngrams) {
                System.out.print(ngram);
        	}
        	System.out.println();
        }
    }
}