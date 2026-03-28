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

import java.util.HashMap;
import java.util.Map;
import smile.data.transform.InvertibleColumnTransform;
import smile.data.type.StructField;
import smile.math.MathEx;
import smile.data.DataFrame;
import smile.data.type.StructType;
import smile.util.function.Function;

/**
 * Scales the numeric variables into the range [0, 1].
 * If the dataset has outliers, normalization will certainly scale
 * the "normal" data to a very small interval. In this case, the
 * Winsorization procedure should be applied: values greater than the
 * specified upper limit are replaced with the upper limit, and those
 * below the lower limit are replaced with the lower limit. Often, the
 * specified range is indicate in terms of percentiles of the original
 * distribution (like the 5th and 95th percentile).
 *
 * @author Haifeng Li
 */
public interface Scaler {
    /**
     * Fits the data transformation.
     * @param data the training data.
     * @param columns the columns to transform.
     *                If empty, transform all the numeric columns.
     * @return the transform.
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

        Map<String, Function> transforms = new HashMap<>();
        Map<String, Function> inverses = new HashMap<>();
        for (String column : columns) {
            StructField field = schema.field(column);
            if (!field.isNumeric()) {
                throw new IllegalArgumentException(String.format("%s is not numeric", field.name()));
            }

            double[] vector = data.column(column).toDoubleArray();
            double lo = MathEx.min(vector);
            double hi = MathEx.max(vector);
            double span = hi - lo;
            double scale = MathEx.isZero(span) ? 1.0 : hi - lo;

            Function transform = new Function() {
                @Override
                public double f(double x) {
                    double y = (x - lo) / scale;
                    if (y < 0.0) y = 0.0;
                    if (y > 1.0) y = 1.0;
                    return y;
                }

                @Override
                public String toString() {
                    return (lo >= 0.0) ?
                            String.format("(%s - %.4f) / %.4f", field.name(),  lo, scale)
                          : String.format("(%s + %.4f) / %.4f", field.name(), -lo, scale);
                }
            };

            Function inverse = (double x) -> x * scale + lo;
            transforms.put(field.name(), transform);
            inverses.put(field.name(), inverse);
        }

        return new InvertibleColumnTransform("Scaler", transforms, inverses);
    }
}
