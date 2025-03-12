/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.selection;

import java.util.Arrays;
import java.util.stream.IntStream;
import smile.classification.ClassLabels;
import smile.data.DataFrame;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.ValueVector;
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
 * @param feature The feature name.
 * @param ratio Sum squares ratio.
 * @author Haifeng Li
 */
public record SumSquaresRatio(String feature, double ratio) implements Comparable<SumSquaresRatio> {
    @Override
    public int compareTo(SumSquaresRatio other) {
        return Double.compare(ratio, other.ratio);
    }

    @Override
    public String toString() {
        return String.format("SumSquaresRatio(%s, %.4f)", feature, ratio);
    }

    /**
     * Calculates the sum squares ratio of numeric variables.
     *
     * @param data the data frame of the explanatory and response variables.
     * @param clazz the column name of class labels.
     * @return the sum squares ratio.
     */
    public static SumSquaresRatio[] fit(DataFrame data, String clazz) {
        ValueVector y = data.column(clazz);
        ClassLabels codec = ClassLabels.fit(y);

        if (codec.k < 2) {
            throw new UnsupportedOperationException("Invalid number of classes: " + codec.k);
        }

        int n = data.size();
        int k = codec.k;
        int[] nc = new int[k];
        double[] condmu = new double[k];

        for (int i = 0; i < n; i++) {
            int yi = codec.y[i];
            nc[yi]++;
        }

        StructType schema = data.schema();

        return IntStream.range(0, schema.length())
                .filter(i -> {
                    StructField field = schema.field(i);
                    return !field.name().equals(clazz) && field.isNumeric();
                })
                .mapToObj(j -> {
            StructField field = schema.field(j);
            ValueVector xj = data.column(j);
            double mu = 0.0;
            Arrays.fill(condmu, 0.0);
            for (int i = 0; i < n; i++) {
                int yi = codec.y[i];
                double xij = xj.getDouble(i);
                mu += xij;
                condmu[yi] += xij;
            }

            mu /= n;
            for (int i = 0; i < k; i++) {
                condmu[i] /= nc[i];
            }

            double wss = 0.0;
            double bss = 0.0;

            for (int i = 0; i < n; i++) {
                int yi = codec.y[i];
                double xij = xj.getDouble(i);
                bss += MathEx.pow2(condmu[yi] - mu);
                wss += MathEx.pow2(xij - condmu[yi]);
            }

            return new SumSquaresRatio(field.name(), bss / wss);
        }).toArray(SumSquaresRatio[]::new);
    }
}
