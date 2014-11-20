/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.feature;

import java.util.HashMap;
import java.util.Map;

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
public class Bag<T> {
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
     * @param features the list of feature objects.
     * @param binary true to check if feature object appear in a collection
     * instead of their frequencies.
     */
    public Bag(T[] features, boolean binary) {
        this.binary = binary;
        this.features = new HashMap<T, Integer>();
        for (int i = 0; i < features.length; i++) {
            this.features.put(features[i], i);
        }
    }
    
    /**
     * Returns the bag-of-words features of a document. The features are real-valued
     * in convenience of most learning algorithms although they take only integer
     * or binary values.
     */
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
