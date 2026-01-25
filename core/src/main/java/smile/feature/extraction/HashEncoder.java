/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.extraction;

import java.util.TreeMap;
import java.util.function.Function;
import smile.hash.MurmurHash3;
import smile.util.SparseArray;

/**
 * Feature hashing, also known as the hashing trick, is a fast and
 * space-efficient way of vectorizing features, i.e. turning arbitrary
 * features (mostly text) into indices in a vector. It works by applying
 * a hash function to the features and using their hash values as indices
 * directly, rather than looking the indices up in an associative array.
 *
 * @author Haifeng Li
 */
public class HashEncoder implements Function<String, SparseArray> {
    /**
     * The tokenizer of text, which may include additional processing
     * such as filtering stop word, converting to lowercase, stemming, etc.
     */
    private final Function<String, String[]> tokenizer;
    /**
     * The number of features in the output space. Small numbers of
     * features are likely to cause hash collisions, but large numbers
     * will cause larger coefficient dimensions in linear learners.
     */
    private final int numFeatures;
    /**
     * When True, an alternating sign is added to the features as to
     * approximately conserve the inner product in the hashed space
     * even for small number of features. This approach is similar
     * to sparse random projection.
     */
    private final boolean alternateSign;

    /**
     * Constructor.
     * @param tokenizer the tokenizer of text, which may include additional processing
     *                  such as filtering stop word, converting to lowercase, stemming, etc.
     * @param numFeatures the number of features in the output space. Small numbers of
     *      features are likely to cause hash collisions, but large numbers
     *      will cause larger coefficient dimensions in linear learners.
     */
    public HashEncoder(Function<String, String[]> tokenizer, int numFeatures) {
        this(tokenizer, numFeatures, true);
    }

    /**
     * Constructor.
     * @param tokenizer the tokenizer of text, which may include additional processing
     *                  such as filtering stop word, converting to lowercase, stemming, etc.
     * @param numFeatures the number of features in the output space. Small numbers of
     *      features are likely to cause hash collisions, but large numbers
     *      will cause larger coefficient dimensions in linear learners.
     * @param alternateSign When True, an alternating sign is added to the features as to
     *      approximately conserve the inner product in the hashed space
     *      even for small number of features. This approach is similar
     *      to sparse random projection.
     */
    public HashEncoder(Function<String, String[]> tokenizer, int numFeatures, boolean alternateSign) {
        this.tokenizer = tokenizer;
        this.numFeatures = numFeatures;
        this.alternateSign = alternateSign;
    }

    /**
     * Returns the bag-of-words features of a document.
     * @param text a document.
     * @return the sparse feature vector.
     */
    @Override
    public SparseArray apply(String text) {
        TreeMap<Integer, Integer> bag = new TreeMap<>();
        for (String word : tokenizer.apply(text)) {
            int h = MurmurHash3.hash32(word, 0);
            // abs(-2 * * 31)is undefined behavior
            int index = h == -2147483648 ? (2147483647 - (numFeatures - 1)) % numFeatures : Math.abs(h) % numFeatures;

            // improve inner product preservation in the hashed space
            int value = alternateSign && h < 0 ? -1 : 1;
            bag.merge(index, value, Integer::sum);
        }

        SparseArray features = new SparseArray();
        bag.forEach(features::append);
        return features;
    }
}
