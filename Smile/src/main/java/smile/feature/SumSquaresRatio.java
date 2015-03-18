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

import java.util.Arrays;
import smile.math.Math;

/**
 * The ratio of between-groups to within-groups sum of squares is a univariate
 * feature ranking metric, which can be used as a feature selection criterion
 * for multi-class classification problems. For each variable j, this ratio is
 * BSS(j) / WSS(j) = &Sigma;I(y<sub>i</sub> = k)(x<sub>kj</sub> - x<sub>&middot;j</sub>)<sup>2</sup> / &Sigma;I(y<sub>i</sub> = k)(x<sub>ij</sub> - x<sub>kj</sub>)<sup>2</sup>;
 * where x<sub>&middot;j</sub> denotes the average of variable j across all
 * samples, x<sub>kj</sub> denotes the average of variable j across samples
 * belonging to class k, and x<sub>ij</sub> is the value of variable j of sample i.
 * Clearly, features with larger sum squares ratios are better for classification.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> S. Dudoit, J. Fridlyand and T. Speed. Comparison of discrimination methods for the classification of tumors using gene expression data. J Am Stat Assoc, 97:77-87, 2002.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SumSquaresRatio  implements FeatureRanking {

    @Override
    public double[] rank(double[][] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        // class label set.
        int[] labels = Math.unique(y);
        Arrays.sort(labels);
        
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]); 
            }
            
            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + labels[i]+1);                 
            }
        }

        int k = labels.length;
        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");            
        }
        
        int n = x.length;
        int p = x[0].length;
        int[] nc = new int[k];
        double[] mu = new double[p];
        double[][] condmu = new double[k][p];

        for (int i = 0; i < n; i++) {
            int yi = y[i];
            nc[yi]++;
            for (int j = 0; j < p; j++) {
                mu[j] += x[i][j];
                condmu[yi][j] += x[i][j];
            }
        }

        for (int j = 0; j < p; j++) {
            mu[j] /= n;
            for (int i = 0; i < k; i++) {
                condmu[i][j] /= nc[i];
            }
        }

        double[] wss = new double[p];
        double[] bss = new double[p];

        for (int i = 0; i < n; i++) {
            int yi = y[i];
            for (int j = 0; j < p; j++) {
                bss[j] += Math.sqr(condmu[yi][j] - mu[j]);
                wss[j] += Math.sqr(x[i][j] - condmu[yi][j]);
            }
        }

        for (int j = 0; j < p; j++) {
            bss[j] /= wss[j];
        }

        return bss;
    }
}
