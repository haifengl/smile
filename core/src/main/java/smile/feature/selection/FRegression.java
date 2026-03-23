/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.selection;

import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.stat.distribution.FDistribution;
import smile.stat.hypothesis.FTest;

/**
 * Univariate F-statistic and p-values, which can be used as a feature
 * selection criterion for linear regression problems.
 *
 * @param feature The feature name.
 * @param statistic F-statistic.
 * @param pvalue The p-value associated with the F-statistic.
 * @param categorical True if the feature is categorical.
 * @author Haifeng Li
 */
public record FRegression(String feature, double statistic, double pvalue, boolean categorical) implements Comparable<FRegression> {
    @Override
    public int compareTo(FRegression other) {
        return Double.compare(statistic, other.statistic);
    }

    @Override
    public String toString() {
        return String.format("FRegression(%s, %.4f, %.4g)", feature, statistic, pvalue);
    }

    /**
     * Calculates the signal noise ratio of numeric variables.
     *
     * @param data the data frame of the explanatory and response variables.
     * @param response the column name of response variable.
     * @return the signal noise ratio.
     */
    public static FRegression[] fit(DataFrame data, String response) {
        double[] y = data.column(response).toDoubleArray();
        StructType schema = data.schema();

        return IntStream.range(0, schema.length())
                .filter(i -> !schema.field(i).name().equals(response))
                .mapToObj(i -> {
            StructField field = schema.field(i);
            if (field.isNumeric()) {
                double[] x = data.column(i).toDoubleArray();
                double cor = MathEx.cor(x, y);
                double r2 = cor * cor;
                int df = y.length - 2;
                var dist = new FDistribution(1, df);
                double F = r2 / (1 - r2) * df;
                double pvalue = 1 - dist.cdf(F);
                return new FRegression(field.name(), F, pvalue, false);
            } else {
                int[] x = data.column(i).toIntArray();
                FTest test = FTest.test(x, y);
                return new FRegression(field.name(), test.f(), test.pvalue(), true);
            }
        }).toArray(FRegression[]::new);
    }
}
