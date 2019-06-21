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

import java.util.Arrays;

/**
 * An n-gram is a contiguous sequence of n words from a given sequence of text.
 * An n-gram of size 1 is referred to as a unigram; size 2 is a bigram;
 * size 3 is a trigram.
 *
 * @author Haifeng Li
 */
public class NGram implements Comparable<NGram> {

    /**
     * Immutable word sequences.
     */
    public final String[] words;

    /**
     * Frequency of n-gram in the corpus.
     */
    public int freq;

    /**
     * Constructor.
     * @param words the n-gram word sequence.
     */
    public NGram(String[] words) {
        this(words, 0);
    }

    /**
     * Constructor.
     * @param words the n-gram word sequence.
     * @param freq the frequency of n-gram in the corpus.
     */
    public NGram(String[] words, int freq) {
        this.words = words;
        this.freq = freq;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('(')
          .append(Arrays.toString(words))
          .append(", ")
          .append(freq)
          .append(')');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(words);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final NGram other = (NGram) obj;
        return Arrays.equals(words, other.words);
    }

    @Override
    public int compareTo(NGram o) {
        return freq - o.freq;
    }
}
