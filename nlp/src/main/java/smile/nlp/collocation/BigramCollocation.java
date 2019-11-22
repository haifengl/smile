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

import smile.nlp.Bigram;

/**
 * Collocations are expressions of multiple words which commonly co-occur.
 * A bigram collocation is a pair of words w1 w2 that appear together with
 * statistically significance.
 *
 * @author Haifeng Li
 */
public class BigramCollocation implements Comparable<BigramCollocation> {

    /**
     * The bigram.
     */
    public final Bigram bigram;
    /**
     * The frequency of bigram in the corpus.
     */
    public final int frequency;
    /**
     * The chi-square statistical score of the collocation.
     */
    public final double score;

    /**
     * Constructor.
     * @param w1 the first word of bigram.
     * @param w2 the second word of bigram.
     * @param frequency the frequency of bigram in the corpus.
     * @param score the chi-square statistical score of collocation in a corpus.
     */
    public BigramCollocation(String w1, String w2, int frequency, double score) {
        this.bigram = new Bigram(w1, w2);
        this.frequency = frequency;
        this.score = score;
    }

    /**
     * Constructor.
     * @param bigram the bigram.
     * @param frequency the frequency of bigram in the corpus.
     * @param score the chi-square statistical score of collocation in a corpus.
     */
    public BigramCollocation(Bigram bigram, int frequency, double score) {
        this.bigram = bigram;
        this.frequency = frequency;
        this.score = score;
    }

    @Override
    public String toString() {
        return String.format("(%s %s, %d, %.2f)", bigram.w1, bigram.w2, frequency, score);
    }

    @Override
    public int hashCode() {
        return bigram.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final BigramCollocation other = (BigramCollocation) obj;
        return bigram.equals(other.bigram);
    }

    @Override
    public int compareTo(BigramCollocation o) {
        return Double.compare(score, o.score);
    }
}
