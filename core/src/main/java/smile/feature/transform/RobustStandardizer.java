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
import smile.sort.IQAgent;
import smile.util.function.Function;

/**
 * Robustly standardizes numeric feature by subtracting
 * the median and dividing by the IQR.
 *
 * @author Haifeng Li
 */
public interface RobustStandardizer {
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

            IQAgent agent = new IQAgent();
            double[] vector = data.column(column).toDoubleArray();
            for (double xi : vector) {
                agent.add(xi);
            }

            double median = agent.quantile(0.5);
            double iqr = agent.quantile(0.75) - agent.quantile(0.25);
            double scale = MathEx.isZero(iqr) ? 1.0 : iqr;

            Function transform = new Function() {
                @Override
                public double f(double x) {
                    return (x - median) / scale;
                }

                @Override
                public String toString() {
                    return (median >= 0.0) ?
                            String.format("(%s - %.4f) / %.4f", field.name(),  median, scale)
                          : String.format("(%s + %.4f) / %.4f", field.name(), -median, scale);
                }
            };

            Function inverse = (double x) -> x * scale + median;
            transforms.put(field.name(), transform);
            inverses.put(field.name(), inverse);
        }

        return new InvertibleColumnTransform("RobustStandardizer", transforms, inverses);
    }
}
