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
import java.util.stream.Collectors;

/**
 * An n-gram is a contiguous sequence of n words from a given sequence of text.
 * An n-gram of size 1 is referred to as a unigram; size 2 is a bigram;
 * size 3 is a trigram.
 *
 * @author Haifeng Li
 */
public class NGram {

    /**
     * Immutable word sequences.
     */
    public final String[] words;

    /**
     * Constructor.
     * @param words the n-gram word sequence.
     */
    public NGram(String[] words) {
        this.words = words;
    }

    @Override
    public String toString() {
        return Arrays.stream(words).collect(Collectors.joining(", ", "[", "]"));
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
}
