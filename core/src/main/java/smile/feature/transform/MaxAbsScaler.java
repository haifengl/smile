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
 * Scales each feature by its maximum absolute value. This class scales and
 * translates each feature individually such that the maximal absolute value
 * of each feature in the training set will be 1.0. It does not shift/center
 * the data, and thus does not destroy any sparsity.
 *
 * @author Haifeng Li
 */
public interface MaxAbsScaler {
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
            double max = 0.0;
            for (double xi : vector) {
                max = Math.max(max, Math.abs(xi));
            }
            double scale = MathEx.isZero(max) ? 1.0 : max;

            Function transform = new Function() {
                @Override
                public double f(double x) {
                    return x / scale;
                }

                @Override
                public String toString() {
                    return String.format("%s / %.4f", field.name(), scale);
                }
            };

            Function inverse = (double x) -> x * scale;
            transforms.put(field.name(), transform);
            inverses.put(field.name(), inverse);
        }

        return new InvertibleColumnTransform("MaxAbsScaler", transforms, inverses);
    }
}
