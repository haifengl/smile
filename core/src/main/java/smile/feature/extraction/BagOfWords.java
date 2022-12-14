/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.feature.extraction;

import java.util.Arrays;
import java.util.function.Function;
import java.util.HashMap;
import java.util.Map;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.transform.Transform;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.sort.QuickSort;

/**
 * The bag-of-words feature of text used in natural language
 * processing and information retrieval. In this model, a text
 * (such as a sentence or a document) is represented as an
 * unordered collection of words, disregarding grammar and
 * even word order.
 * 
 * @author Haifeng Li
 */
public class BagOfWords implements Transform {
    /**
     * The tokenizer of text, which may include additional processing
     * such as filtering stop word, converting to lowercase, stemming, etc.
     */
    private final Function<String, String[]> tokenizer;
    /**
     * The mapping from feature words to indices.
     */
    private final Map<String, Integer> words;
    /**
     * True to check if feature words appear in a document instead of their
     * frequencies.
     */
    private final boolean binary;
    /**
     * The schema of output space.
     */
    private final StructType schema;
    /**
     * The input text fields.
     */
    private final String[] columns;

    /**
     * Constructor.
     * @param tokenizer the tokenizer of text, which may include additional processing
     *                  such as filtering stop word, converting to lowercase, stemming, etc.
     * @param words the list of feature words.
     */
    public BagOfWords(Function<String, String[]> tokenizer, String[] words) {
        this(null, tokenizer, words, false);
    }

    /**
     * Constructor.
     * @param columns the input text fields in a data frame.
     * @param tokenizer the tokenizer of text, which may include additional processing
     *                  such as filtering stop word, converting to lowercase, stemming, etc.
     * @param words the list of feature words. The feature words should be unique in the list.
     *              Note that the Bag class doesn't learn the features, but just use them as attributes.
     * @param binary true to check if feature object appear in a collection
     *               instead of their frequencies.
     */
    public BagOfWords(String[] columns, Function<String, String[]> tokenizer, String[] words, boolean binary) {
        this.columns = columns;
        this.tokenizer = tokenizer;
        this.binary = binary;
        this.words = new HashMap<>();
        for (int i = 0; i < words.length; i++) {
            if (this.words.containsKey(words[i])) {
                throw new IllegalArgumentException("Duplicated word:" + words[i]);
            }
            this.words.put(words[i], i);
        }

        StructField[] fields = Arrays.stream(words)
                .map(word -> new StructField("BoW_" + word, DataTypes.IntegerType))
                .toArray(StructField[]::new);
        this.schema = new StructType(fields);
    }

    /**
     * Learns a vocabulary dictionary of top-k frequent tokens in the raw documents.
     * @param data training data.
     * @param k the limit of vocabulary size.
     * @param columns the text columns.
     * @return the model.
     */
    public static BagOfWords fit(DataFrame data, Function<String, String[]> tokenizer, int k, String... columns) {
        HashMap<String, Integer> words = new HashMap<>();
        for (String column : columns) {
            for (String text : data.column(column).toStringArray()) {
                for (String word : tokenizer.apply(text)) {
                    Integer count = words.get(word);
                    if (count == null) words.put(word, 1);
                    else words.put(word, count + 1);
                }
            }
        }

        String[] features = new String[words.size()];
        int[] count = new int[words.size()];
        int i = 0;
        for (String word : words.keySet()) {
            features[i] = word;
            count[i++] = -words.get(word);
        }

        QuickSort.sort(count, features);
        return new BagOfWords(columns, tokenizer, Arrays.copyOf(features, Math.min(k, features.length)), false);
    }

    @Override
    public Tuple apply(Tuple x) {
        int[] bag = new int[words.size()];

        for (String column : columns) {
            for (String word : tokenizer.apply(x.getString(column))) {
                Integer f = words.get(word);
                if (f != null) {
                    if (binary) bag[f] = 1;
                    else bag[f]++;
                }
            }
        }

        return Tuple.of(bag, schema);
    }

    /**
     * Returns the bag-of-words features of a document.
     * @param text a document.
     * @return the feature vector.
     */
    public int[] apply(String text) {
        int[] bag = new int[words.size()];

        for (String word : tokenizer.apply(text)) {
            Integer f = words.get(word);
            if (f != null) {
                if (binary) bag[f] = 1;
                else bag[f]++;
            }
        }

        return bag;
    }
}
