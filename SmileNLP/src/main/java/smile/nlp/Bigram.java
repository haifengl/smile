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
