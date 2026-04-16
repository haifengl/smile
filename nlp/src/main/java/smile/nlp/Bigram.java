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

/**
 * Bigrams or digrams are groups of two words, and are very commonly used
 * as the basis for simple statistical analysis of text. They are a special
 * case of N-gram.
 * 
 * @param w1 the first word of bigram.
 * @param w2 the second word of bigram.
 * @param count the total number of occurrences of bigram in the corpus.
 * @param score the chi-square statistical score of the collocation in the corpus.
 * @author Haifeng Li
 */
public record Bigram(String w1, String w2,
                     int count, double score) implements Comparable<Bigram> {

    /**
     * Constructor.
     * @param w1 the first word of bigram.
     * @param w2 the second word of bigram.
     */
    public Bigram(String w1, String w2) {
        this(w1, w2, -1, Double.NaN);
    }

    @Override
    public String toString() {
        return count > 0 ?
                String.format("(%s %s, %d, %.2f)", w1, w2, count, score) :
                String.format("(%s %s)", w1, w2);
    }

    // Compares by word pair only.
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + w1.hashCode();
        hash = 37 * hash + w2.hashCode();
        return hash;
    }

    // Compares by word pair only.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Bigram other)) {
            return false;
        }

        return w1.equals(other.w1) && w2.equals(other.w2);
    }

    // NOTE: compareTo is intentionally inconsistent with equals/hashCode (inherited
    // from smile.nlp.Bigram, which compares by word pair). This class is only used
    // as a value holder for sorting collocations by score, not in sorted sets/maps.
    @Override
    public int compareTo(Bigram o) {
        return Double.compare(score, o.score);
    }
}
