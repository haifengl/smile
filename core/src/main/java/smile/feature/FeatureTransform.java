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

/**
 * Feature transformation. In general, learning algorithms benefit from
 * standardization of the data set. If some outliers are present in the
 * set, robust scalers or transformers are more appropriate.
 *
 * @author Haifeng Li
 */
public interface FeatureTransform {
    /**
     * Transform a feature vector.
     * @param x a feature vector.
     * @return the transformed feature value.
     */
    public double[] transform(double[] x);

    /**
     * Transform an array of feature vectors.
     * @param x an array of feature vectors. The feature
     *         vectors may be modified on output.
     * @return the transformed feature vectors.
     */
    default public double[][] transform(double[][] x) {
        double[][] y = new double[x.length][];
        for (int i = 0; i < y.length; i++) {
            y[i] = transform(x[i]);
        }

        return y;
    }
}
