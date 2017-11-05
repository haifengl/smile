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
import java.util.Iterator;
import smile.sort.HeapSelect;
import smile.stat.distribution.ChiSquareDistribution;
import smile.nlp.Bigram;
import smile.nlp.Corpus;

/**
 * Tools to identify collocations (words that often appear consecutively) within
 * corpora. They may also be used to find other associations between word
 * occurrences.
 * <p>
 * Finding collocations requires first calculating the frequencies of words
 * and their appearance in the context of other words. Often the collection
 * of words will then requiring filtering to only retain useful content terms.
 * Each n-gram of words may then be scored according to some association measure,
 * in order to determine the relative likelihood of each n-gram being a
 * collocation.
 * 
 * @author Haifeng Li
 */
public class BigramCollocationFinder {

    /**
     * Chi-square distribution with 1 degree of freedom.
     */
    private ChiSquareDistribution chisq = new ChiSquareDistribution(1);

    /**
     * The minimum frequency of collocation.
     */
    private int minFreq;

    /**
     * Constructor.
     * @param minFreq the minimum frequency of collocation.
     */
    public BigramCollocationFinder(int minFreq) {
        this.minFreq = minFreq;
    }

    /**
     * Finds top k bigram collocations in the given corpus.
     * @return the array of significant bigram collocations in descending order
     * of likelihood ratio.
     */
    public BigramCollocation[] find(Corpus corpus, int k) {
        BigramCollocation[] bigrams = new BigramCollocation[k];
        HeapSelect<BigramCollocation> heap = new HeapSelect<>(bigrams);
        
        Iterator<Bigram> iterator = corpus.getBigrams();
        while (iterator.hasNext()) {
            Bigram bigram = iterator.next();
            int c12 = corpus.getBigramFrequency(bigram);

            if (c12 > minFreq) {
                int c1 = corpus.getTermFrequency(bigram.w1);
                int c2 = corpus.getTermFrequency(bigram.w2);

                double score = likelihoodRatio(c1, c2, c12, corpus.size());
                heap.add(new BigramCollocation(bigram.w1, bigram.w2, c12, -score));
            }
        }

        heap.sort();

        BigramCollocation[] collocations = new BigramCollocation[k];
        for (int i = 0; i < k; i++) {
            BigramCollocation bigram = bigrams[k-i-1];
            collocations[i] = new BigramCollocation(bigram.w1(), bigram.w2(), bigram.frequency(), -bigram.score());
        }

        return collocations;
    }

    /**
     * Finds bigram collocations in the given corpus whose p-value is less than
     * the given threshold.
     * @param p the p-value threshold
     * @return the array of significant bigram collocations in descending order
     * of likelihood ratio.
     */
    public BigramCollocation[] find(Corpus corpus, double p) {
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("Invalid p = " + p);
        }

        double cutoff = chisq.quantile(p);
        
        ArrayList<BigramCollocation> bigrams = new ArrayList<>();

        Iterator<Bigram> iterator = corpus.getBigrams();
        while (iterator.hasNext()) {
            Bigram bigram = iterator.next();
            int c12 = corpus.getBigramFrequency(bigram);

            if (c12 > minFreq) {
                int c1 = corpus.getTermFrequency(bigram.w1);
                int c2 = corpus.getTermFrequency(bigram.w2);

                double score = likelihoodRatio(c1, c2, c12, corpus.size());
                if (score > cutoff) {
                    bigrams.add(new BigramCollocation(bigram.w1, bigram.w2, c12, score));
                }
            }
        }

        int n = bigrams.size();
        BigramCollocation[] collocations = new BigramCollocation[n];
        for (int i = 0; i < n; i++) {
            collocations[i] = bigrams.get(i);
        }

        Arrays.sort(collocations);
        // Reverse to descending order
        for (int i = 0; i < n/2; i++) {
            BigramCollocation b = collocations[i];
            collocations[i] = collocations[n-i-1];
            collocations[n-i-1] = b;
        }

        return collocations;
    }

    /**
     * Returns the likelihood ratio test statistic -2 log &lambda;
     * @param c1 the number of occurrences of w1.
     * @param c2 the number of occurrences of w2.
     * @param c12 the number of occurrences of w1 w2.
     * @param N the number of tokens in the corpus.
     */
    private double likelihoodRatio(int c1, int c2, int c12, long N) {
        double p = (double) c2 / N;
        double p1 = (double) c12 / c1;
        double p2 = (double) (c2 - c12) / (N - c1);

        double logLambda = logL(c12, c1, p) + logL(c2-c12, N-c1, p) - logL(c12, c1, p1) - logL(c2-c12, N-c1, p2);
        return -2 * logLambda;
    }

    /**
     * Help function for calculating likelihood ratio statistic.
     */
    private double logL(int k, long n, double x) {
        if (x == 0.0) x = 0.01;
        if (x == 1.0) x = 0.99;
        return k * Math.log(x) + (n-k) * Math.log(1-x);
    }
}
