/*******************************************************************************
 * Copyright (c) 2019 Haifeng Li
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

package smile.classification;

import java.io.Serializable;
import java.util.*;
import smile.math.Math;

/**
 * A class to map a sparse set of class labels (e.g., [1, 3, 5, 6]) into a dense set (e.g., [0, 1,
 * 2, 3]). This lets classifiers internally represent the range of possible class values as a simple
 * range of integers, while allowing users to have gaps in their classes.
 */
final class SparseClassMap implements Serializable {
    /**
     * Whether the labels were already dense. If this is true, the forward and reverse maps will
     * be null.
     */
    private final boolean isIdentity;

    /**
     * How many distinct class labels there are.
     */
    private final int k;

    /**
     * A map from dense labels to the original sparse labels.
     */
    private final int[] reverseMap;

    /**
     * A map from the original sparse labels to dense labels.
     */
    private final Map<Integer, Integer> forwardMap;

    /**
     * Creates a SparseClassMap.
     *
     * @param labels The class labels from the training data.
     */
    SparseClassMap(int[] classLabels) {
        int[] uniqueLabels = Math.unique(classLabels);
        Arrays.sort(uniqueLabels);
        k = uniqueLabels.length;
        if (uniqueLabels[0] < 0) {
            throw new IllegalArgumentException("Negative class label: " + uniqueLabels[0]);
        } else if (uniqueLabels[0] == 0 && uniqueLabels[uniqueLabels.length - 1] == uniqueLabels.length - 1) {
            isIdentity = true;
            reverseMap = null;
            forwardMap = null;
        } else {
            isIdentity = false;
            reverseMap = uniqueLabels;
            forwardMap = new HashMap<>(k * 2);
            for (int i = 0; i < k; i++) {
                forwardMap.put(reverseMap[i], i);
            }
        }
    }

    /**
     * Gets the number of distinct classes.
     */
    int numberOfClasses() {
        return k;
    }

    /**
     * Gets the maximum sparse class label.
     */
    int maxSparseLabel() {
        if (isIdentity) {
            return k - 1;
        } else {
            return reverseMap[reverseMap.length - 1];
        }
    }

    /**
     * Returns whether this mapping is the identity.
     */
    boolean isIdentity() {
        return isIdentity;
    }

    /**
     * Maps a sparse class label to the corresponding dense label.
     */
    int sparseLabelToDenseLabel(int label) {
        if (label < 0 || label > maxSparseLabel()) {
            throw new IllegalArgumentException("Invalid sparse label " + label);
        }

        if (isIdentity) {
            return label;
        } else {
            Integer denseLabel = forwardMap.get(label);
            if (denseLabel == null) {
                throw new IllegalArgumentException("Invalid sparse label " + label);
            }
            return denseLabel;
        }
    }

    /**
     * Maps an array of sparse class labels to the corresponding dense labels.
     */
    int[] sparseLabelsToDenseLabels(int[] labels) {
        if (isIdentity) {
            return labels;
        } else {
            int[] denseLabels = new int[labels.length];
            for (int i = 0; i < labels.length; i++) {
                Integer denseLabel = forwardMap.get(labels[i]);
                if (denseLabel == null) {
                    throw new IllegalArgumentException("Invalid sparse label " + labels[i]);
                }
                denseLabels[i] = denseLabel;
            }
            return denseLabels;
        }
    }

    /**
     * Maps a dense class label to the corresponding sparse label.
     */
    int denseLabelToSparseLabel(int label) {
        if (label < 0 || label >= k) {
            throw new IllegalArgumentException("Invalid dense label " + label);
        }

        if (isIdentity) {
            return label;
        } else {
            return reverseMap[label];
        }
    }
}
