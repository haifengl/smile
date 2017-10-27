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
import smile.sort.QuickSelect;

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
public class WinsorScaler extends Scaler {
    /**
     * Lower limit.
     */
    protected double lower;
    /**
     * Upper limit.
     */
    protected double upper;

    /**
     * Constructor. Inplace transformation.
     * @param lower the lower limit in terms of percentiles of the original
     *              distribution (say 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     *              distribution (say 95th percentile).
     */
    public WinsorScaler(double lower, double upper) {
        if (lower < 0.0 || lower > 0.5) {
            throw new IllegalArgumentException("Invalid lower limit: " + lower);
        }

        if (upper < 0.5 || upper > 1.0) {
            throw new IllegalArgumentException("Invalid upper limit: " + upper);
        }

        if (upper <= lower) {
            throw new IllegalArgumentException("Invalid lower and upper limit pair: " + lower + " >= " + upper);
        }

        this.lower = lower;
        this.upper = upper;
    }

    /**
     * Constructor.
     * @param lower the lower limit in terms of percentiles of the original
     *              distribution (say 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     *              distribution (say 95th percentile).
     * @param copy  If false, try to avoid a copy and do inplace scaling instead.
     */
    public WinsorScaler(double lower, double upper, boolean copy) {
        super(copy);

        if (lower < 0.0 || lower > 0.5) {
            throw new IllegalArgumentException("Invalid lower limit: " + lower);
        }

        if (upper < 0.5 || upper > 1.0) {
            throw new IllegalArgumentException("Invalid upper limit: " + upper);
        }

        if (upper <= lower) {
            throw new IllegalArgumentException("Invalid lower and upper limit pair: " + lower + " >= " + upper);
        }

        this.lower = lower;
        this.upper = upper;
    }

    @Override
    public void learn(Attribute[] attributes, double[][] data) {
        int n = data.length;
        int p = data[0].length;
        int i1 = (int) Math.round(lower * n);
        int i2 = (int) Math.round(upper * n);
        if (i2 == n) {
            i2 = n - 1;
        }

        lo = new double[p];
        hi = new double[p];
        double[] x = new double[n];

        for (int j = 0; j < p; j++) {
            if (attributes[j].getType() != Attribute.Type.NUMERIC) {
                lo[j] = Double.NaN;
            } else {
                for (int i = 0; i < n; i++) {
                    x[i] = data[i][j];
                }

                lo[j] = QuickSelect.select(x, i1);
                hi[j] = QuickSelect.select(x, i2) - lo[j];
                if (Math.isZero(hi[j])) {
                    throw new IllegalArgumentException("Attribute " + j + " has constant values in the given range.");
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("WinsorScaler(%f, %f", lower, upper));
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
