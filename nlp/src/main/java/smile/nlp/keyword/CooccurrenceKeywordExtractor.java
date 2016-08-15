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
package smile.nlp.keyword;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import smile.nlp.NGram;
import smile.nlp.Trie;
import smile.nlp.collocation.AprioriPhraseExtractor;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleParagraphSplitter;
import smile.nlp.tokenizer.SimpleSentenceSplitter;
import smile.nlp.tokenizer.SimpleTokenizer;
import smile.sort.QuickSort;

/**
 * Keyword extraction from a single document using word co-occurrence statistical information.
 * The algorithm was proposed by Y. Matsuo and M. Ishizuka. It consists of six steps:
 * <ol>
 * <li> Stem words by Porter algorithm and extract phrases based APRIORI algorithm
 *      (upto 4 words with frequency more than 3 times). Discard stop words.
 * <li> Select the top frequent terms up to 30% of running terms.
 * <li> Clustering frequent terms. Two terms are in the same cluster if
 *      either their Jensen-Shannon divergence or mutual information is
 *      above the threshold (0.95 * log 2, and log 2, respectively).
 * <li> Calculate the expected co-occurrence probability
 * <li> Calculate the refined χ2 values that removes the maximal term.
 * <li> Output a given number of terms of largest refined χ2 values.
 * </ol>
 * 
 * @author Haifeng Li
 */
public class CooccurrenceKeywordExtractor {

    /**
     * Returns the top 10 keywords.
     * @param text A single document.
     * @return The top 10 keywords.
     */
    public ArrayList<NGram> extract(String text) {
        return extract(text, 10);
    }
    
    /**
     * Returns a given number of top keywords.
     * @param text A single document.
     * @return The top keywords.
     */
    public ArrayList<NGram> extract(String text, int maxNumKeywords) {
        ArrayList<String[]> sentences = new ArrayList<>();
        
        SimpleTokenizer tokenizer = new SimpleTokenizer();
        PorterStemmer stemmer = new PorterStemmer();
        
        // Split text into sentences. Stem words by Porter algorithm.
        int ntotal = 0;
        for (String paragraph : SimpleParagraphSplitter.getInstance().split(text)) {
            for (String s : SimpleSentenceSplitter.getInstance().split(paragraph)) {
                String[] sentence = tokenizer.split(s);
                for (int i = 0; i < sentence.length; i++) {
                    sentence[i] = stemmer.stripPluralParticiple(sentence[i]).toLowerCase();
                }
                sentences.add(sentence);
                ntotal += sentence.length;
            }
        }

        //  Extract phrases by Apriori-like algorithm.
        int maxNGramSize = 4;
        ArrayList<NGram> terms = new ArrayList<>();
        AprioriPhraseExtractor phraseExtractor = new AprioriPhraseExtractor();
        for (ArrayList<NGram> ngrams : phraseExtractor.extract(sentences, maxNGramSize, 4)) {
            for (NGram ngram : ngrams) {
                terms.add(ngram);
            }
        }
        Collections.sort(terms);
        
        // Select upto 30% most frequent terms.
        int n = 3 * terms.size() / 10;
        NGram[] freqTerms = new NGram[n];
        for (int i = 0, start = terms.size() - n; i < n; i++) {
            freqTerms[i] = terms.get(start + i);
        }
        
        // Trie for phrase matching.
        Trie<String, Integer> trie = new Trie<>();
        for (int i = 0; i < n; i++) {
            trie.put(freqTerms[i].words, i);
        }
        
        // Build co-occurrence table
        int[] nw = new int[n];
        int[][] table = new int[n][n];
        for (String[] sentence : sentences) {
            Set<Integer> phrases = new HashSet<>();
            for (int j = 1; j <= maxNGramSize; j++) {
                for (int i = 0; i <= sentence.length - j; i++) {
                    String[] phrase  = Arrays.copyOfRange(sentence, i, i+j);
                    Integer index = trie.get(phrase);
                    if (index != null) {
                        phrases.add(index);
                    }
                }
            }
            
            for (int i : phrases) {
                nw[i] += phrases.size();
                for (int j : phrases) {
                    if (i != j) {
                        table[i][j]++;
                    }
                }
            }
        }
        
        // Clustering frequent terms.
        int[] cluster = new int[n];
        for (int i = 0; i < cluster.length; i++) {
            cluster[i] = i;
        }

        //double log2 = Math.log(2.0);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // Mutual information
                if (table[i][j] > 0) {
                    // This doesn't work as ntotal is usually large and thus the mutual information
                    // is way larger than the threshold log2 given in the paper.
                    //double mutual = Math.log((double) ntotal * table[i][j] / (freqTerms[i].freq * freqTerms[j].freq));
                    // Here we just use the (squared) geometric average of co-occurrence probability
                    // It works well to clustering things like "digital computer" and "computer" in practice.
                    double mutual = (double) table[i][j] * table[i][j] / (freqTerms[i].freq * freqTerms[j].freq);
                    if (mutual >= 0.25) {
                        cluster[j] = cluster[i];
                    } /*else {
                        double js = 0.0; // Jsensen-Shannon divergence
                        for (int k = 0; k < n; k++) {
                            double p1 = (double) table[i][k] / freqTerms[i].freq;
                            double p2 = (double) table[j][k] / freqTerms[j].freq;

                            // The formula in the paper is not correct as p is not real probablity.
                            if (p1 > 0 && p2 > 0) {
                                js += -(p1+p2) * Math.log((p1+p2)/2.0) + p1 * Math.log(p1) + p2 * Math.log(p2);
                            }
                        }
                    
                        js /= 2.0;
                        if (js > log2) {
                            cluster[j] = cluster[i];
                        }
                    }*/
                }
            }
        }
        
        // Calculate expected probability
        double[] pc = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                pc[cluster[j]] += table[i][j];
            }
        }
        for (int i = 0; i < n; i++) {
            pc[i] /= ntotal;
        }

        
        // Calculate chi-square scores.
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (cluster[j] != j) {
                    continue;
                }
                    
                double fwc = 0.0;
                for (int k = 0; k < n; k++) {
                    if (cluster[k] == j)
                    fwc += table[i][k];
                }
                    
                double expected = nw[i] * pc[j];
                double d = (fwc - expected);
                double chisq = d * d / expected;
                score[i] += chisq;
                if (chisq > max) max = chisq;
            }
            //score[i] -= max;
        }
        
        int[] index = QuickSort.sort(score);
        ArrayList<NGram> keywords = new ArrayList<>();
        for (int i = n; i-- > 0; ) {
            boolean add = true;
            // filter out components of phrases, e.g. "digital" in "digital computer".
            for (int j = i+1; j < n; j++) {
                if (cluster[index[j]] == cluster[index[i]]) {
                    if (freqTerms[index[j]].words.length >= freqTerms[index[i]].words.length) {
                        add = false;
                        break;
                    } else {
                        keywords.remove(freqTerms[index[j]]);
                        add = true;
                    }
                }
            }
            
            if (add) {
                keywords.add(freqTerms[index[i]]);
                if (keywords.size() >= maxNumKeywords) break;
            }
        }

        return keywords;
    }
}
