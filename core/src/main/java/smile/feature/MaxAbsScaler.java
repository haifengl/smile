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

import smile.math.Math;
import smile.data.Attribute;

/**
 * Scales each feature by its maximum absolute value. This class scales and
 * translates each feature individually such that the maximal absolute value
 * of each feature in the training set will be 1.0. It does not shift/center
 * the data, and thus does not destroy any sparsity.
 *
 * @author Haifeng Li
 */
public class MaxAbsScaler extends FeatureTransform {
    /**
     * Scaling factor.
     */
    private double[] scale;

    /**
     * Constructor.
     */
    public MaxAbsScaler() {

    }

    /**
     * Constructor.
     * @param copy  If false, try to avoid a copy and do inplace scaling instead.
     */
    public MaxAbsScaler(boolean copy) {
        super(copy);
    }

    @Override
    public void learn(Attribute[] attributes, double[][] data) {
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

        double[] y = copy ? new double[x.length] : x;
        for (int i = 0; i < x.length; i++) {
            y[i] = x[i] / scale[i];
        }

        return y;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MaxAbsScaler(");
        if (scale != null) {
            if (scale.length > 0) {
                sb.append(String.format("%.4f", scale[0]));
            }

            for (int i = 1; i < scale.length; i++) {
                sb.append(String.format(", %.4f", scale[i]));
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
