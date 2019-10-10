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

package smile.feature;

import java.util.HashMap;
import java.util.Map;

/**
 * The bag-of-words feature of text used in natural language
 * processing and information retrieval. In this model, a text (such as a
 * sentence or a document) is represented as an unordered collection of words,
 * disregarding grammar and even word order.
 * 
 * @author Haifeng Li
 */
public class Bag {
    /**
     * The mapping from feature words to indices.
     */
    private Map<String, Integer> words;

    /**
     * True to check if feature words appear in a document instead of their
     * frequencies.
     */
    private boolean binary;

    /**
     * Constructor.
     * @param words the list of feature words.
     */
    public Bag(String[] words) {
        this(words, false);
    }

    /**
     * Constructor.
     * @param words the list of feature words. The feature words should be unique in the list.
     * Note that the Bag class doesn't learn the features, but just use them as attributes.
     * @param binary true to check if feature object appear in a collection
     * instead of their frequencies.
     */
    public Bag(String[] words, boolean binary) {
        this.binary = binary;
        this.words = new HashMap<>();
        for (int i = 0; i < words.length; i++) {
            if (this.words.containsKey(words[i])) {
                throw new IllegalArgumentException("Duplicated word:" + words[i]);
            }
            this.words.put(words[i], i);
        }
    }

    /**
     * Returns the bag-of-words features of a document.
     */
    public int[] apply(String[] x) {
        int[] bag = new int[words.size()];

        if (binary) {
            for (String word : x) {
                Integer f = words.get(word);
                if (f != null) {
                    bag[f] = 1;
                }
            }
        } else {
            for (String word : x) {
                Integer f = words.get(word);
                if (f != null) {
                    bag[f]++;
                }
            }
        }

        return bag;
    }
}
