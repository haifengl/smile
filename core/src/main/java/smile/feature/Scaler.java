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
 * Scales all numeric variables into the range [0, 1].
 * If the dataset has outliers, normalization will certainly scale
 * the "normal" data to a very small interval. In this case, the
 * Winsorization procedure should be applied: values greater than the
 * specified upper limit are replaced with the upper limit, and those
 * below the lower limit are replace with the lower limit. Often, the
 * specified range is indicate in terms of percentiles of the original
 * distribution (like the 5th and 95th percentile).
 *
 * @author Haifeng Li
 */
public class Scaler extends FeatureTransform {
    /**
     * Lower bound.
     */
    protected double[] lo;
    /**
     * Upper bound.
     */
    protected double[] hi;

    /** Constructor. Inplace transformation. */
    public Scaler() {

    }

    /**
     * Constructor.
     * @param copy  If false, try to avoid a copy and do inplace scaling instead.
     */
    public Scaler(boolean copy) {
        super(copy);
    }

    @Override
    public void learn(Attribute[] attributes, double[][] data) {
        lo = Math.colMin(data);
        hi = Math.colMax(data);

        for (int i = 0; i < lo.length; i++) {
            if (attributes[i].getType() != Attribute.Type.NUMERIC) {
                lo[i] = Double.NaN;
            } else {
                hi[i] -= lo[i];
                if (Math.isZero(hi[i])) {
                    hi[i] = 1.0;
                }
            }
        }
    }

    @Override
    public double[] transform(double[] x) {
        if (x.length != lo.length) {
            throw new IllegalArgumentException(String.format("Invalid vector size %d, expected %d", x.length, lo.length));
        }

        double[] y = copy ? new double[x.length] : x;
        for (int i = 0; i < x.length; i++) {
            if (!Double.isNaN(lo[i])) {
                double yi = (x[i] - lo[i]) / hi[i];
                if (yi < 0.0) yi = 0.0;
                if (yi > 1.0) yi = 1.0;
                y[i] = yi;
            }
        }

        return y;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Scaler(");
        if (lo != null) {
            sb.append("\n");
            for (int i = 0; i < lo.length; i++) {
                sb.append(String.format("  [%.4f, %.4f]%n", lo[i], hi[i]));
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
