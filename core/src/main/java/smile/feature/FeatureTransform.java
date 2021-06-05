/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.feature;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;

import static smile.util.Regex.BOOLEAN_REGEX;
import static smile.util.Regex.DOUBLE_REGEX;

/**
 * Feature transformation. In general, learning algorithms benefit from
 * standardization of the data set. If some outliers are present in the
 * set, robust transformers are more appropriate.
 *
 * @author Haifeng Li
 */
public interface FeatureTransform extends Serializable {
    /**
     * Returns the optional schema of data.
     * @return the optional schema of data.
     */
    Optional<StructType> schema();

    /**
     * Scales a value with i-th column parameters.
     * @param x a feature value.
     * @param i the column index.
     * @return the transformed feature value.
     */
    double transform(double x, int i);

    /**
     * Inverse scales a value with i-th column parameters.
     * @param x a feature value.
     * @param i the column index.
     * @return the transformed feature value.
     */
    double invert(double x, int i);

    /**
     * Transform a feature vector.
     * @param x the feature vector.
     * @return the transformed feature value.
     */
    default double[] transform(double[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = transform(x[i], i);
        }
        return y;
    }

    /**
     * Transform a feature vector.
     * @param x the input feature vector.
     * @param y the output feature vector.
     */
    default void transform(double[] x, double[] y) {
        for (int i = 0; i < y.length; i++) {
            y[i] = transform(x[i], i);
        }
    }

    /**
     * Transform a data frame.
     * @param data a data frame.
     * @return the transformed data frame.
     */
    default double[][] transform(double[][] data) {
        return Arrays.stream(data).parallel().map(this::transform).toArray(double[][]::new);
    }

    /**
     * Transform a feature vector.
     * @param x a feature vector.
     * @return the transformed feature value.
     */
    default Tuple transform(Tuple x) {
        if (!schema().isPresent()) {
            throw new UnsupportedOperationException("Schema is not available");
        }

        StructType schema = schema().get();
        if (!schema.equals(x.schema())) {
            throw new IllegalArgumentException(String.format("Invalid schema %s, expected %s", x.schema(), schema));
        }

        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                if (schema.field(i).isNumeric()) {
                    return transform(x.getDouble(i), i);
                } else {
                    return x.get(i);
                }
            }

            @Override
            public StructType schema() {
                return schema;
            }
        };
    }

    /**
     * Transform a data frame.
     * @param data a data frame.
     * @return the transformed data frame.
     */
    default DataFrame transform(DataFrame data) {
        if (!schema().isPresent()) {
            throw new UnsupportedOperationException("Schema is not available");
        }

        StructType schema = schema().get();
        if (!schema.equals(data.schema())) {
            throw new IllegalArgumentException(String.format("Invalid schema %s, expected %s", data.schema(), schema));
        }

        BaseVector<?, ?, ?>[] vectors = new BaseVector[schema.length()];
        IntStream.range(0, schema.length()).parallel().forEach(i -> {
            StructField field = schema.field(i);
            if (field.isNumeric()) {
                DoubleStream stream = data.stream().mapToDouble(t -> transform(t.getDouble(i), i));
                vectors[i] = DoubleVector.of(field, stream);
            } else {
                vectors[i] = data.column(i);
            }
        });
        return DataFrame.of(vectors);
    }

    /**
     * Inverse transform a feature vector.
     * @param x a feature vector.
     * @return the inverse transformed feature value.
     */
    default double[] invert(double[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = invert(x[i], i);
        }
        return y;
    }

    /**
     * Inverse transform a data frame.
     * @param data a data frame.
     * @return the inverse transformed data frame.
     */
    default double[][] invert(double[][] data) {
        return Arrays.stream(data).parallel().map(this::invert).toArray(double[][]::new);
    }

    /**
     * Inverse transform a feature vector.
     * @param x a feature vector.
     * @return the inverse transformed feature value.
     */
    default Tuple invert(Tuple x) {
        if (!schema().isPresent()) {
            throw new UnsupportedOperationException("Schema is not available");
        }

        StructType schema = schema().get();
        if (!schema.equals(x.schema())) {
            throw new IllegalArgumentException(String.format("Invalid schema %s, expected %s", x.schema(), schema));
        }

        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                if (schema.field(i).isNumeric()) {
                    return invert(x.getDouble(i), i);
                } else {
                    return x.get(i);
                }
            }

            @Override
            public StructType schema() {
                return schema;
            }
        };
    }

    /**
     * Inverse transform a data frame.
     * @param data a data frame.
     * @return the inverse transformed data frame.
     */
    default DataFrame invert(DataFrame data) {
        if (!schema().isPresent()) {
            throw new UnsupportedOperationException("Schema is not available");
        }

        StructType schema = schema().get();
        if (!schema.equals(data.schema())) {
            throw new IllegalArgumentException(String.format("Invalid schema %s, expected %s", data.schema(), schema));
        }

        BaseVector<?, ?, ?>[] vectors = new BaseVector[schema.length()];
        IntStream.range(0, schema.length()).forEach(i -> {
            StructField field = schema.field(i);
            if (field.isNumeric()) {
                DoubleStream stream = data.stream().parallel().mapToDouble(t -> invert(t.getDouble(i), i));
                vectors[i] = DoubleVector.of(field, stream);
            } else {
                vectors[i] = data.column(i);
            }
        });
        return DataFrame.of(vectors);
    }

    /**
     * Returns the feature transformer. If the parameter {@code transformer} is null or empty,
     * return {@code null}.
     *
     * @param transformer the feature transformation algorithm.
     * @param data the training data.
     * @return the feature transformer.
     */
    static FeatureTransform of(String transformer, double[][] data) {
        if (transformer == null|| transformer.isEmpty()) return null;

        transformer = transformer.trim().toLowerCase(Locale.ROOT);
        if (transformer.equals("l1")) {
            return Normalizer.L1;
        }

        if (transformer.equals("l2")) {
            return Normalizer.L2;
        }

        if (transformer.equals("linf")) {
            return Normalizer.L_INF;
        }

        if (transformer.equals("minmax")) {
            return Scaler.fit(data);
        }

        if (transformer.equals("maxabs")) {
            return MaxAbsScaler.fit(data);
        }

        Pattern winsor = Pattern.compile(
                String.format("winsor\\((%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX));
        Matcher m = winsor.matcher(transformer);
        if (m.matches()) {
            double lower = Double.parseDouble(m.group(1));
            double upper = Double.parseDouble(m.group(2));
            return WinsorScaler.fit(data, lower, upper);
        }

        Pattern standardizer = Pattern.compile(
                String.format("standardizer(\\(\\s*(%s)\\))?", BOOLEAN_REGEX));
        m = standardizer.matcher(transformer);
        if (m.matches()) {
            boolean robust = false;
            if (m.group(1) != null) {
                robust = Boolean.parseBoolean(m.group(2));
            }
            return robust ? RobustStandardizer.fit(data) : Standardizer.fit(data);
        }

        throw new IllegalArgumentException("Unsupported feature transform: " + transformer);
    }
}
