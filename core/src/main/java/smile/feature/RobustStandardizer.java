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
import smile.math.Math;
import smile.sort.QuickSelect;

/**
 * Robustly standardizes numeric feature by subtracting
 * the median and dividing by the IQR.
 *
 * @author Haifeng Li
 */
public class RobustStandardizer extends Standardizer {

    /**
     * Constructor.
     */
    public RobustStandardizer() {

    }

    /**
     * Constructor.
     * @param copy  If false, try to avoid a copy and do inplace scaling instead.
     */
    public RobustStandardizer(boolean copy) {
        super(copy);
    }

    @Override
    public void learn(Attribute[] attributes, double[][] data) {
        int n = data.length;
        int p = data[0].length;

        mu = new double[p];
        std = new double[p];
        double[] x = new double[n];

        for (int j = 0; j < p; j++) {
            if (attributes[j].getType() != Attribute.Type.NUMERIC) {
                mu[j] = Double.NaN;
            } else {
                for (int i = 0; i < n; i++) {
                    x[i] = data[i][j];
                }

                mu[j] = QuickSelect.median(x);
                std[j] = QuickSelect.q3(x) - QuickSelect.q1(x);
                if (Math.isZero(std[j])) {
                    throw new IllegalArgumentException("Column " + j + " has constant values between Q1 and Q3.");
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RobustStandardizer(");
        if (mu != null) {
            sb.append("\n");
            for (int i = 0; i < mu.length; i++) {
                sb.append(String.format("  [%.4f, %.4f]%n", mu[i], std[i]));
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
