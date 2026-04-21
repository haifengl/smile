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
package smile.feature.transform;

import java.util.LinkedHashMap;
import java.util.Map;
import smile.data.transform.InvertibleColumnTransform;
import smile.data.type.StructField;
import smile.math.MathEx;
import smile.data.DataFrame;
import smile.data.type.StructType;
import smile.util.function.Function;

/**
 * Standardizes numeric feature to 0 mean and unit variance.
 * Standardization makes an assumption that the data follows
 * a Gaussian distribution and are also not robust when outliers present.
 * A robust alternative is to subtract the median and divide by the IQR
 * by {@code RobustStandardizer}.
 *
 * <p>The standard deviation is computed with the <em>sample</em> formula
 * (N−1 denominator). For a constant column (stdev = 0), the scale falls
 * back to 1.0 so that the output is simply {@code x - mean} (all zeros
 * for training data). A single-row data frame is treated the same way.
 *
 * @author Haifeng Li
 */
public interface Standardizer {
    /**
     * Fits the data transformation.
     * @param data the training data.
     * @param columns the columns to transform.
     *                If empty, transform all the numeric columns.
     * @return the transform.
     * @throws IllegalArgumentException if the data frame is empty or a
     *         specified column is non-numeric.
     */
    static InvertibleColumnTransform fit(DataFrame data, String... columns) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        if (columns.length == 0) {
            columns = schema.fields().stream()
                    .filter(StructField::isNumeric)
                    .map(StructField::name)
                    .toArray(String[]::new);
        }

        Map<String, Function> transforms = new LinkedHashMap<>();
        Map<String, Function> inverses = new LinkedHashMap<>();
        for (String column : columns) {
            StructField field = schema.field(column);
            if (!field.isNumeric()) {
                throw new IllegalArgumentException(String.format("%s is not numeric", field.name()));
            }

            double[] vector = data.column(column).toDoubleArray();
            double mu = MathEx.mean(vector);
            double sd = MathEx.stdev(vector);
            double scale = MathEx.isZero(sd) ? 1.0 : sd;

            Function transform = new Function() {
                @Override
                public double f(double x) {
                    return (x - mu) / scale;
                }

                @Override
                public String toString() {
                    return (mu >= 0.0) ?
                            String.format("(%s - %.4f) / %.4f", field.name(),  mu, scale)
                          : String.format("(%s + %.4f) / %.4f", field.name(), -mu, scale);
                }
            };

            Function inverse = (double x) -> x * scale + mu;
            transforms.put(field.name(), transform);
            inverses.put(field.name(), inverse);
        }

        return new InvertibleColumnTransform("Standardizer", transforms, inverses);
    }
}
