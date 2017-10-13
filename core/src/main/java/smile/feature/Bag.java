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
import smile.data.Attribute;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;

/**
 * The bag-of-words feature of text used in natural language
 * processing and information retrieval. In this model, a text (such as a
 * sentence or a document) is represented as an unordered collection of words,
 * disregarding grammar and even word order. This is a generic implementations.
 * Thus, it can be used for sequences of any (discrete) data types with limited
 * number of values.
 * 
 * @author Haifeng Li
 */
public class Bag<T> implements FeatureGenerator<T[]> {
    /**
     * The attributes of generated features.
     */
    private Attribute[] attributes;
    /**
     * The mapping from feature words to indices.
     */
    private Map<T, Integer> features;

    /**
     * True to check if feature words appear in a document instead of their
     * frequencies.
     */
    private boolean binary;

    /**
     * Constructor.
     * @param features the list of feature objects.
     */
    public Bag(T[] features) {
        this(features, false);
    }

    /**
     * Constructor.
     * @param features the list of feature objects. The feature objects should be unique in the list.
     * Note that the Bag class doesn't learn the features, but just use them as attributes.
     * @param binary true to check if feature object appear in a collection
     * instead of their frequencies.
     */
    public Bag(T[] features, boolean binary) {
        this.binary = binary;
        this.features = new HashMap<>();
        for (int i = 0, k = 0; i < features.length; i++) {
            if (!this.features.containsKey(features[i])) {
                this.features.put(features[i], k++);
            }
        }

        attributes = new Attribute[this.features.size()];
        for (Map.Entry<T, Integer> entry : this.features.entrySet()) {
            if (binary) {
                attributes[entry.getValue()] = new NominalAttribute(entry.getKey().toString(), new String[]{"No", "Yes"});
            } else {
                attributes[entry.getValue()] = new NumericAttribute(entry.getKey().toString());
            }
        }
    }

    @Override
    public Attribute[] attributes() {
        return attributes;
    }

    /**
     * Returns the bag-of-words features of a document. The features are real-valued
     * in convenience of most learning algorithms although they take only integer
     * or binary values.
     */
    @Override
    public double[] feature(T[] x) {
        double[] bag = new double[features.size()];

        if (binary) {
            for (T word : x) {
                Integer f = features.get(word);
                if (f != null) {
                    bag[f] = 1.0;
                }
            }
        } else {
            for (T word : x) {
                Integer f = features.get(word);
                if (f != null) {
                    bag[f]++;
                }
            }
        }

        return bag;
    }
}
