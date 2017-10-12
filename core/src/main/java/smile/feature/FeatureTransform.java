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

import smile.data.Attribute;
import smile.data.NumericAttribute;

/**
 * Feature transformation. In general, learning algorithms benefit from
 * standardization of the data set. If some outliers are present in the
 * set, robust transformers are more appropriate.
 *
 * @author Haifeng Li
 */
public abstract class FeatureTransform {
    /**
     * If false, try to avoid a copy and do inplace transformation instead.
     */
    protected boolean copy;

    /** Constructor. Inplace transformation. */
    public FeatureTransform() {
        this(false);
    }

    /**
     * Constructor.
     * @param copy  If false, try to avoid a copy and do inplace scaling instead.
     */
    public FeatureTransform(boolean copy) {
        this.copy = copy;
    }

    /**
     * Learns transformation parameters from a dataset.
     * All features are assumed numeric.
     * @param data The training data.
     */
    public void learn(double[][] data) {
        int p = data[0].length;
        Attribute[] attributes = new Attribute[p];
        for (int i = 0; i < p; i++) {
            attributes[i] = new NumericAttribute("V"+i);
        }
        learn(attributes, data);
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param attributes The variable attributes. Of which, numeric variables
     *                   will be standardized.
     * @param data The training data to learn scaling parameters.
     *             The data will not be modified.
     */
    public abstract void learn(Attribute[] attributes, double[][] data);

    /**
     * Transform a feature vector.
     * @param x a feature vector.
     * @return the transformed feature value.
     */
    public abstract double[] transform(double[] x);

    /**
     * Transform an array of feature vectors.
     * @param x an array of feature vectors. The feature
     *         vectors may be modified on output if copy is false.
     * @return the transformed feature vectors.
     */
    public double[][] transform(double[][] x) {
        double[][] y = new double[x.length][];
        for (int i = 0; i < y.length; i++) {
            y[i] = transform(x[i]);
        }

        return y;
    }
}
