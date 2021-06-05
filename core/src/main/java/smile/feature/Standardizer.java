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

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.type.StructType;
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
    double[] sd;

    /**
     * Constructor.
     * @param mu mean.
     * @param sd standard deviation.
     */
    public Standardizer(double[] mu, double[] sd) {
        if (mu.length != sd.length) {
            throw new IllegalArgumentException("Scaling factor size don't match");
        }

        for (int i = 0; i < sd.length; i++) {
            if (MathEx.isZero(sd[i])) {
                sd[i] = 1.0;
            }
        }

        this.mu = mu;
        this.sd = sd;
    }

    /**
     * Constructor.
     * @param schema the schema of data.
     * @param mu mean.
     * @param sd standard deviation.
     */
    public Standardizer(StructType schema, double[] mu, double[] sd) {
        this(mu, sd);
        if (schema.length() != mu.length) {
            throw new IllegalArgumentException("Schema and scaling factor size don't match");
        }
        this.schema = schema;
    }

    @Override
    public Optional<StructType> schema() {
        return Optional.ofNullable(schema);
    }

    /**
     * Fits the transformation parameters.
     * @param data The training data.
     * @return the model.
     */
    public static Standardizer fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        int p = schema.length();
        double[] mu = new double[p];
        double[] sd = new double[p];

        for (int i = 0; i < p; i++) {
            if (schema.field(i).isNumeric()) {
                double[] x = data.column(i).toDoubleArray();
                mu[i] = MathEx.mean(x);
                sd[i] = MathEx.sd(x);
            }
        }

        return new Standardizer(schema, mu, sd);
    }

    /**
     * Fits the transformation parameters.
     * @param data The training data.
     * @return the model.
     */
    public static Standardizer fit(double[][] data) {
        double[] mu = MathEx.colMeans(data);
        double[] sd = MathEx.colSds(data);
        return new Standardizer(mu, sd);
    }

    @Override
    public double transform(double x, int i) {
        return (x - mu[i]) / sd[i];
    }

    @Override
    public double invert(double x, int i) {
        return x * sd[i] + mu[i];
    }

    /** Returns the string representation of i-th column scaling factor. */
    private String toString(int i) {
        String field = schema == null ? String.format("V%d", i+1) : schema.field(i).name;
        return String.format("%s[%.4f, %.4f]", field, mu[i], sd[i]);
    }

    @Override
    public String toString() {
        String className = getClass().getSimpleName();
        return IntStream.range(0, mu.length)
                .mapToObj(this::toString)
                .collect(Collectors.joining(",", className + "(", ")"));
    }
}
