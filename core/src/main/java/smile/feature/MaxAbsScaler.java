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

import smile.data.NumericAttribute;
import smile.math.Math;
import smile.data.Attribute;
import smile.sort.QuickSelect;

/**
 * Scale each feature by its maximum absolute value. This class scales and
 * translates each feature individually such that the maximal absolute value
 * of each feature in the training set will be 1.0. It does not shift/center
 * the data, and thus does not destroy any sparsity.
 *
 * @author Haifeng Li
 */
public class MaxAbsScaler implements FeatureTransform {
    /**
     * Scaling factor.
     */
    private double[] scale;

    /**
     * Constructor. Learn the scaling parameters from the data.
     * @param data The training data to learn scaling parameters.
     *             The data will not be modified.
     */
    public MaxAbsScaler(double[][] data) {
        int n = data.length;
        int p = data[0].length;
        scale = new double[p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                double abs = Math.abs(data[i][j]);
                if (scale[j] < abs) {
                    scale[j] = abs;
                }
            }
        }

        for (int i = 0; i < scale.length; i++) {
            if (Math.isZero(scale[i])) {
                scale[i] = 1.0;
            }
        }
    }

    /**
     * Constructor. Learn the scaling parameters from the data.
     * @param attributes The variable attributes. Of which, numeric variables
     *                   will be standardized.
     * @param data The training data to learn scaling parameters.
     *             The data will not be modified.
     */
    public MaxAbsScaler(Attribute[] attributes, double[][] data) {
        int n = data.length;
        int p = data[0].length;
        scale = new double[p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (attributes[j].getType() == Attribute.Type.NUMERIC) {
                    double abs = Math.abs(data[i][j]);
                    if (scale[j] < abs) {
                        scale[j] = abs;
                    }
                }
            }
        }

        for (int i = 0; i < scale.length; i++) {
            if (Math.isZero(scale[i])) {
                scale[i] = 1.0;
            }
        }
    }

    /**
     * Scales each feature by its maximum absolute value.
     * @param x a vector to be scaled. The vector will be modified on output.
     * @return the input vector.
     */
    @Override
    public double[] transform(double[] x) {
        if (x.length != scale.length) {
            throw new IllegalArgumentException(String.format("Invalid vector size %d, expected %d", x.length, scale.length));
        }

        for (int i = 0; i < x.length; i++) {
            x[i] /= scale[i];
        }

        return x;
    }
}
