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

/**
 * Bigrams or digrams are groups of two words, and are very commonly used
 * as the basis for simple statistical analysis of text. They are a special
 * case of N-gram.
 * 
 * @author Haifeng Li
 */
public class Bigram {

    /**
     * Immutable first word of bigram.
     */
    public final String w1;

    /**
     * Immutable second word of bigram.
     */
    public final String w2;

    /**
     * Constructor.
     * @param w1 the first word of bigram.
     * @param w2 the second word of bigram.
     */
    public Bigram(String w1, String w2) {
        this.w1 = w1;
        this.w2 = w2;
    }

    @Override
    public String toString() {
        return String.format("(%s %s)", w1, w2);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + w1.hashCode();
        hash = 37 * hash + w2.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final Bigram other = (Bigram) obj;
        if ((this.w1 == null) ? (other.w1 != null) : !this.w1.equals(other.w1)) {
            return false;
        }

        if ((this.w2 == null) ? (other.w2 != null) : !this.w2.equals(other.w2)) {
            return false;
        }

        return true;
    }
}
