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

import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructField;
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
    private static final long serialVersionUID = 2L;

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
                std[i] = 1.0;
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
            if (schema.field(i).isNumeric()) {
                final int col = i;
                double sum = data.stream().mapToDouble(t -> t.getDouble(col)).sum();
                double squaredSum = data.stream().mapToDouble(t -> t.getDouble(col)).map(x -> x*x).sum();
                mu[i] = sum / n;
                std[i] = Math.sqrt(squaredSum / n - mu[i] * mu[i]);
                if (MathEx.isZero(std[i])) {
                    std[i] = 1.0;
                }
            }
        }

        return new Standardizer(schema, mu, std);
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     */
    public static Standardizer fit(double[][] data) {
        return fit(DataFrame.of(data));
    }

    /** Scales a value with i-th column parameters. */
    private double scale(double x, int i) {
        return (x - mu[i]) / std[i];
    }

    @Override
    public double[] transform(double[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = scale(x[i], i);
        }
        return y;
    }

    @Override
    public Tuple transform(Tuple x) {
        if (!schema.equals(x.schema())) {
            throw new IllegalArgumentException(String.format("Invalid schema %s, expected %s", x.schema(), schema));
        }

        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                if (schema.field(i).isNumeric()) {
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
            StructField field = schema.field(i);
            if (field.isNumeric()) {
                final int col = i;
                DoubleStream stream = data.stream().mapToDouble(t -> scale(t.getDouble(col), col));
                vectors[i] = DoubleVector.of(field, stream);
            } else {
                vectors[i] = data.column(i);
            }
        }
        return DataFrame.of(vectors);
    }

    @Override
    public String toString() {
        return IntStream.range(0, mu.length)
                .mapToObj(i -> String.format("%s[%.4f, %.4f]", schema.field(i).name, mu[i], std[i]))
                .collect(Collectors.joining(",", "Standardizer(", ")"));
    }
}
