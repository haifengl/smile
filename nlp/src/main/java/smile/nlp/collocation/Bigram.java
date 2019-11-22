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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import smile.nlp.Corpus;
import smile.sort.HeapSelect;
import smile.stat.distribution.ChiSquareDistribution;

/**
 * Collocations are expressions of multiple words which commonly co-occur.
 * A bigram collocation is a pair of words w1 w2 that appear together with
 * statistically significance.
 *
 * @author Haifeng Li
 */
public class Bigram extends smile.nlp.Bigram implements Comparable<Bigram> {

    /**
     * The frequency of bigram in the corpus.
     */
    public final int count;
    /**
     * The chi-square statistical score of the collocation.
     */
    public final double score;

    /**
     * Constructor.
     * @param w1 the first word of bigram.
     * @param w2 the second word of bigram.
     * @param count the frequency of bigram in the corpus.
     * @param score the chi-square statistical score of collocation in a corpus.
     */
    public Bigram(String w1, String w2, int count, double score) {
        super(w1, w2);
        this.count = count;
        this.score = score;
    }

    @Override
    public String toString() {
        return String.format("(%s %s, %d, %.2f)", w1, w2, count, score);
    }

    @Override
    public int compareTo(Bigram o) {
        return Double.compare(score, o.score);
    }

    /**
     * Chi-square distribution with 1 degree of freedom.
     */
    private static ChiSquareDistribution chisq = new ChiSquareDistribution(1);

    /**
     * Finds top k bigram collocations in the given corpus.
     * @param minFrequency The minimum frequency of bigram in the corpus.
     * @return the array of significant bigram collocations in descending order
     * of likelihood ratio.
     */
    public static Bigram[] of(Corpus corpus, int k, int minFrequency) {
        Bigram[] bigrams = new Bigram[k];
        HeapSelect<Bigram> heap = new HeapSelect<>(bigrams);

        Iterator<smile.nlp.Bigram> iterator = corpus.getBigrams();
        while (iterator.hasNext()) {
            smile.nlp.Bigram bigram = iterator.next();
            int c12 = corpus.getBigramFrequency(bigram);

            if (c12 > minFrequency) {
                int c1 = corpus.getTermFrequency(bigram.w1);
                int c2 = corpus.getTermFrequency(bigram.w2);

                double score = likelihoodRatio(c1, c2, c12, corpus.size());
                heap.add(new Bigram(bigram.w1, bigram.w2, c12, -score));
            }
        }

        heap.sort();

        Bigram[] collocations = new Bigram[k];
        for (int i = 0; i < k; i++) {
            Bigram bigram = bigrams[k-i-1];
            collocations[i] = new Bigram(bigram.w1, bigram.w2, bigram.count, -bigram.score);
        }

        return collocations;
    }

    /**
     * Finds bigram collocations in the given corpus whose p-value is less than
     * the given threshold.
     * @param p the p-value threshold
     * @param minFrequency The minimum frequency of bigram in the corpus.
     * @return the array of significant bigram collocations in descending
     * order of likelihood ratio.
     */
    public static Bigram[] of(Corpus corpus, double p, int minFrequency) {
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("Invalid p = " + p);
        }

        double cutoff = chisq.quantile(p);

        ArrayList<Bigram> bigrams = new ArrayList<>();

        Iterator<smile.nlp.Bigram> iterator = corpus.getBigrams();
        while (iterator.hasNext()) {
            smile.nlp.Bigram bigram = iterator.next();
            int c12 = corpus.getBigramFrequency(bigram);

            if (c12 > minFrequency) {
                int c1 = corpus.getTermFrequency(bigram.w1);
                int c2 = corpus.getTermFrequency(bigram.w2);

                double score = likelihoodRatio(c1, c2, c12, corpus.size());
                if (score > cutoff) {
                    bigrams.add(new Bigram(bigram.w1, bigram.w2, c12, score));
                }
            }
        }

        Bigram[] collocations = bigrams.toArray(new Bigram[bigrams.size()]);
        Arrays.sort(collocations, Collections.reverseOrder());

        return collocations;
    }

    /**
     * Returns the likelihood ratio test statistic -2 log &lambda;
     * @param c1 the number of occurrences of w1.
     * @param c2 the number of occurrences of w2.
     * @param c12 the number of occurrences of w1 w2.
     * @param N the number of tokens in the corpus.
     */
    private static double likelihoodRatio(int c1, int c2, int c12, long N) {
        double p = (double) c2 / N;
        double p1 = (double) c12 / c1;
        double p2 = (double) (c2 - c12) / (N - c1);

        double logLambda = logL(c12, c1, p) + logL(c2-c12, N-c1, p) - logL(c12, c1, p1) - logL(c2-c12, N-c1, p2);
        return -2 * logLambda;
    }

    /**
     * Help function for calculating likelihood ratio statistic.
     */
    private static double logL(int k, long n, double x) {
        if (x == 0.0) x = 0.01;
        if (x == 1.0) x = 0.99;
        return k * Math.log(x) + (n-k) * Math.log(1-x);
    }
}
