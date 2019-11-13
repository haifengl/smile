/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.feature;

import smile.classification.ClassLabels;
import smile.math.MathEx;

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
public class SumSquaresRatio implements FeatureRanking {
    public static final SumSquaresRatio instance = new SumSquaresRatio();

    @Override
    public double[] rank(double[][] x, int[] y) {
        return of(x, y);
    }

    /**
     * Univariate feature ranking. Note that this method actually does NOT rank
     * the features. It just returns the metric values of each feature. The
     * use can then rank and select features.
     *
     * @param x a n-by-p matrix of n instances with p features.
     * @param y class labels.
     * @return the sum of squares ratio of between-groups to within-groups.
     */
    public static double[] of(double[][] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        y = codec.y;

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
                bss[j] += MathEx.sqr(condmu[yi][j] - mu[j]);
                wss[j] += MathEx.sqr(x[i][j] - condmu[yi][j]);
            }
        }

        for (int j = 0; j < p; j++) {
            bss[j] /= wss[j];
        }

        return bss;
    }
}
