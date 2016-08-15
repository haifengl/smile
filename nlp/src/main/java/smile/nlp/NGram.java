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
