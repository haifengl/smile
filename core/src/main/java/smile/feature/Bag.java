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
        for (int i = 0, k = 0; i < words.length; i++) {
            if (this.words.containsKey(words[i])) {
                throw new IllegalArgumentException("Duplicated word:" + words[i]);
            }
            this.words.put(words[i], k++);
        }
    }

    /**
     * Returns the bag-of-words features of a document. The features are real-valued
     * in convenience of most learning algorithms although they take only integer
     * or binary values.
     */
    public double[] apply(String[] x) {
        double[] bag = new double[words.size()];

        if (binary) {
            for (String word : x) {
                Integer f = words.get(word);
                if (f != null) {
                    bag[f] = 1.0;
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
