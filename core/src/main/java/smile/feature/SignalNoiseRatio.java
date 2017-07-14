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

/**
 * The signal-to-noise (S2N) metric ratio is a univariate feature ranking metric,
 * which can be used as a feature selection criterion for binary classification
 * problems. S2N is defined as |&mu;<sub>1</sub> - &mu;<sub>2</sub>| / (&sigma;<sub>1</sub> + &sigma;<sub>2</sub>),
 * where &mu;<sub>1</sub> and &mu;<sub>2</sub> are the mean value of the variable
 * in classes 1 and 2, respectively, and &sigma;<sub>1</sub> and &sigma;<sub>2</sub>
 * are the standard deviations of the variable in classes 1 and 2, respectively.
 * Clearly, features with larger S2N ratios are better for classification.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> M. Shipp, et al. Diffuse large B-cell lymphoma outcome prediction by gene-expression profiling and supervised machine learning. Nature Medicine, 2002.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SignalNoiseRatio implements FeatureRanking {

    @Override
    public double[] rank(double[][] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        int n1 = 0;
        for (int yi : y) {
            if (yi == 0) {
                n1++;
            } else if (yi != 1) {
                throw new IllegalArgumentException("Invalid class label: " + yi);
            }
        }

        int n = x.length;
        int n2 = n - n1;
        double[][] x1 = new double[n1][];
        double[][] x2 = new double[n2][];
        for (int i = 0, j = 0, k = 0; i < n; i++) {
            if (y[i] == 0) {
                x1[j++] = x[i];
            } else {
                x2[k++] = x[i];
            }
        }

        double[] mu1 = Math.colMeans(x1);
        double[] mu2 = Math.colMeans(x2);
        double[] sd1 = Math.colSds(x1);
        double[] sd2 = Math.colSds(x2);

        int p = mu1.length;
        double[] s2n = new double[p];
        for (int i = 0; i < p; i++) {
            s2n[i] = Math.abs(mu1[i] - mu2[i]) / (sd1[i] + sd2[i]);
        }
        return s2n;
    }
}
