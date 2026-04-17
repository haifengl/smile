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

import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleParagraphSplitter;
import smile.nlp.tokenizer.SimpleSentenceSplitter;
import smile.nlp.tokenizer.SimpleTokenizer;
import smile.sort.QuickSort;
import smile.util.Trie;

import java.util.*;

/**
 * A minimal interface of text.
 *
 * @author Haifeng Li
 */
public interface Text {
    /**
     * Returns the title of text, if there is one.
     * @return the title of text, if there is one.
     */
    String title();

    /**
     * Returns the text content.
     * @return the text content.
     */
    String content();


    /**
     * Creates a text without title.
     * @param content the text content.
     */
    static Text of(String content) {
        return of("", content);
    }

    /**
     * Creates a text.
     * @param title the text title.
     * @param content the text content.
     */
    static Text of(String title, String content) {
        return new SimpleText(title, content);
    }

    /** Maximum n-gram length considered by the Apriori phrase extraction step. */
    int MAX_NGRAM_SIZE = 4;

    /** Minimum n-gram frequency required for a phrase to be retained. */
    int MIN_NGRAM_FREQ = 4;

    /**
     * Fraction of all distinct phrases kept as "frequent terms" for the
     * co-occurrence analysis (top 30%).
     */
    double FREQ_TERM_RATIO = 0.3;

    /**
     * Clustering threshold: two terms are merged into one cluster when their
     * squared geometric average co-occurrence probability is at or above this
     * value.
     */
    double CLUSTERING_THRESHOLD = 0.25;

    /**
     * Extracts the top {@code maxNumKeywords} keywords of the document
     * using word co-occurrence statistical information. Keywords or
     * keyphrases capture the primary topics discussed in the text.
     * The algorithm was proposed by Matsuo &amp; Ishizuka (2004):
     * <em>Keyword Extraction from a Single Document using Word
     * Co-occurrence Statistical Information</em>, AAAI 2004.
     * It consists of six steps:
     * <ol>
     *   <li>Stem words by Porter algorithm and extract phrases based on an
     *       Apriori-like algorithm (up to {@value #MAX_NGRAM_SIZE} words with
     *       frequency at least {@value #MIN_NGRAM_FREQ}). Discard stop words.</li>
     *   <li>Select the top-frequent terms (up to {@value #FREQ_TERM_RATIO} of
     *       running terms).</li>
     *   <li>Cluster frequent terms. Two terms are placed in the same cluster when
     *       the squared geometric average of their co-occurrence probability
     *       exceeds {@value #CLUSTERING_THRESHOLD}.</li>
     *   <li>Calculate the expected co-occurrence probability per cluster.</li>
     *   <li>Calculate the refined χ² score for each term.</li>
     *   <li>Return the top-{@code maxNumKeywords} terms by χ² score, suppressing
     *       sub-phrase duplicates within the same cluster.</li>
     * </ol>
     *
     * @see <a href="https://aaai.org/papers/1116-aaai04-178/">Matsuo &amp; Ishizuka 2004</a>
     *
     * @param maxNumKeywords the maximum number of keywords to return;
     *                       must be positive.
     * @return the top keywords, possibly fewer if the document is too short.
     * @throws IllegalArgumentException if {@code text} is {@code null} or
     *         blank, or if {@code maxNumKeywords} is not positive.
     */
    default List<NGram> keywords(int maxNumKeywords) {
        String text = content();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be null or blank");
        }
        if (maxNumKeywords <= 0) {
            throw new IllegalArgumentException("maxNumKeywords must be positive, got: " + maxNumKeywords);
        }

        ArrayList<String[]> sentences = new ArrayList<>();
        SimpleTokenizer tokenizer = new SimpleTokenizer();
        PorterStemmer stemmer = new PorterStemmer();

        // Step 1 – tokenize, stem and split into sentences.
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

        // Step 1 (cont.) – extract frequent phrases via Apriori-like algorithm.
        ArrayList<NGram> terms = new ArrayList<>();
        for (NGram[] ngrams : NGram.apriori(sentences, MAX_NGRAM_SIZE, MIN_NGRAM_FREQ)) {
            Collections.addAll(terms, ngrams);
        }

        if (terms.isEmpty()) {
            return List.of();
        }

        Collections.sort(terms);

        // Step 2 – keep only the top FREQ_TERM_RATIO fraction.
        int n = (int) (FREQ_TERM_RATIO * terms.size());
        if (n == 0) {
            return List.of();
        }
        NGram[] freqTerms = new NGram[n];
        for (int i = 0, start = terms.size() - n; i < n; i++) {
            freqTerms[i] = terms.get(start + i);
        }

        // Trie for O(1) phrase-index lookup during co-occurrence counting.
        Trie<String, Integer> trie = new Trie<>();
        for (int i = 0; i < n; i++) {
            trie.put(freqTerms[i].words(), i);
        }

        // Step 3 (part A) – build co-occurrence table.
        // nw[i] counts the number of distinct co-occurring terms across all
        // sentences, i.e. the number of times term i appears alongside any
        // other frequent term in the same sentence.
        int[] nw = new int[n];
        int[][] table = new int[n][n];
        for (String[] sentence : sentences) {
            Set<Integer> phrases = new HashSet<>();
            for (int j = 1; j <= MAX_NGRAM_SIZE; j++) {
                for (int i = 0; i <= sentence.length - j; i++) {
                    String[] phrase = Arrays.copyOfRange(sentence, i, i + j);
                    Integer index = trie.get(phrase);
                    if (index != null) {
                        phrases.add(index);
                    }
                }
            }

            for (int i : phrases) {
                // Each co-occurrence partner is every other term in the set.
                nw[i] += phrases.size() - 1;
                for (int j : phrases) {
                    if (i != j) {
                        table[i][j]++;
                    }
                }
            }
        }

        // Step 3 (part B) – cluster frequent terms by co-occurrence proximity.
        int[] cluster = new int[n];
        for (int i = 0; i < n; i++) {
            cluster[i] = i;
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (table[i][j] > 0) {
                    // Squared geometric average of co-occurrence probability.
                    double mutual = (double) table[i][j] * table[i][j]
                            / ((double) freqTerms[i].count() * freqTerms[j].count());
                    if (mutual >= CLUSTERING_THRESHOLD) {
                        cluster[j] = cluster[i];
                    }
                }
            }
        }

        // Step 4 – expected co-occurrence probability per cluster.
        double[] pc = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                pc[cluster[j]] += table[i][j];
            }
        }
        for (int i = 0; i < n; i++) {
            pc[i] /= ntotal;
        }

        // Step 5 – compute refined χ² scores.
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (cluster[j] != j) {
                    continue;
                }

                double fwc = 0.0;
                for (int k = 0; k < n; k++) {
                    if (cluster[k] == j) {
                        fwc += table[i][k];
                    }
                }

                double expected = nw[i] * pc[j];
                if (expected > 0.0) {
                    double d = fwc - expected;
                    score[i] += d * d / expected;
                }
            }
        }

        // Step 6 – select top-scoring keywords, suppressing cluster sub-phrases.
        int[] index = QuickSort.sort(score);
        ArrayList<NGram> keywords = new ArrayList<>();
        for (int i = n; i-- > 0; ) {
            boolean add = true;
            for (int j = i + 1; j < n; j++) {
                if (cluster[index[j]] == cluster[index[i]]) {
                    if (freqTerms[index[j]].words().length >= freqTerms[index[i]].words().length) {
                        add = false;
                        break;
                    } else {
                        keywords.remove(freqTerms[index[j]]);
                    }
                }
            }

            if (add) {
                keywords.add(freqTerms[index[i]]);
                if (keywords.size() >= maxNumKeywords) break;
            }
        }

        return Collections.unmodifiableList(keywords);
    }
}
