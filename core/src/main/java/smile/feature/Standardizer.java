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

import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.math.MathEx;

/**
 * Standardizes numeric feature to 0 mean and unit variance.
 * Standardization makes an assumption that the data follows
 * a Gaussian distribution and are also not robust when outliers present.
 * A robust alternative is to subtract the median and divide by the IQR
 * by <code>RobustStandardizer</code>.
 *
 * @author Haifeng Li
 */
public class Standardizer implements FeatureTransform {
    /**
     * The schema of data.
     */
    StructType schema;
    /**
     * Mean or median.
     */
    double[] mu;
    /**
     * Standard deviation or IQR.
     */
    double[] std;

    /**
     * Constructor.
     * @param schema the schema of data.
     * @param mu mean.
     * @param std standard deviation.
     */
    public Standardizer(StructType schema, double[] mu, double[] std) {
        if (schema.length() != mu.length || mu.length != std.length) {
            throw new IllegalArgumentException("Schema and scaling factor size don't match");
        }

        for (int i = 0; i < std.length; i++) {
            if (MathEx.isZero(std[i])) {
                throw new IllegalArgumentException("Invalid standard deviation: 0");
            }
        }

        this.schema = schema;
        this.mu = mu;
        this.std = std;
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     */
    public static Standardizer fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        double[] mu = new double[schema.length()];
        double[] std = new double[schema.length()];

        int n = data.nrows();
        for (int i = 0; i < mu.length; i++) {
            if (DataType.isDouble(schema.field(i).type)) {
                double sum = data.doubleVector(i).stream().sum();
                double squaredSum = data.doubleVector(i).stream().map(x -> x*x).sum();
                mu[i] = sum / n;
                std[i] = Math.sqrt(squaredSum / n - mu[i] * mu[i]);
            }
        }

        return new Standardizer(schema, mu, std);
    }

    /** Scales a value with i-th column parameters. */
    private double scale(double x, int i) {
        return (x - mu[i]) / std[i];
    }

    @Override
    public Tuple transform(Tuple x) {
        if (!schema.equals(x.schema())) {
            throw new IllegalArgumentException(String.format("Invalid schema %s, expected %s", x.schema(), schema));
        }

        return new Tuple() {
            @Override
            public Object get(int i) {
                if (DataType.isDouble(schema.field(i).type)) {
                    return scale(x.getDouble(i), i);
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

    @Override
    public DataFrame transform(DataFrame data) {
        if (!schema.equals(data.schema())) {
            throw new IllegalArgumentException(String.format("Invalid schema %s, expected %s", data.schema(), schema));
        }

        BaseVector[] vectors = new BaseVector[schema.length()];
        for (int i = 0; i < mu.length; i++) {
            if (DataType.isDouble(schema.field(i).type)) {
                final int idx = i;
                DoubleStream stream = data.doubleVector(i).stream().map(x -> scale(x, idx));
                vectors[i] = DoubleVector.of(schema.field(i).name, stream);
            } else {
                vectors[i] = data.vector(i);
            }
        }
        return DataFrame.of(vectors);
    }

    @Override
    public String toString() {
        return IntStream.range(0, mu.length).mapToObj(
                i -> String.format("%s[%.4f, %.4f]", schema.field(i).name, mu[i], std[i])
        ).collect(Collectors.joining(",", "Standardizer(", ")"));
    }
}
