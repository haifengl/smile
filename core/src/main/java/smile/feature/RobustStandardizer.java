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
     * Constructor. Learn the scaling parameters from the data.
     * @param data The training data to learn scaling parameters.
     *             The data will not be modified.
     */
    public RobustStandardizer(double[][] data) {
        int n = data.length;
        int p = data[0].length;

        mu = new double[p];
        std = new double[p];
        double[] x = new double[n];

        for (int j = 0; j < p; j++) {
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

    /**
     * Constructor. Learn the scaling parameters from the data.
     * @param attributes The variable attributes. Of which, numeric variables
     *                   will be standardized.
     * @param data The training data to learn scaling parameters.
     *             The data will not be modified.
     */
    public RobustStandardizer(Attribute[] attributes, double[][] data) {
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
}
