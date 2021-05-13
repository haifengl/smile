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
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import smile.math.MathEx;
import smile.data.DataFrame;
import smile.data.type.StructType;

/**
 * Scales all numeric variables into the range [0, 1].
 * If the dataset has outliers, normalization will certainly scale
 * the "normal" data to a very small interval. In this case, the
 * Winsorization procedure should be applied: values greater than the
 * specified upper limit are replaced with the upper limit, and those
 * below the lower limit are replace with the lower limit. Often, the
 * specified range is indicate in terms of percentiles of the original
 * distribution (like the 5th and 95th percentile).
 *
 * @author Haifeng Li
 */
public class Scaler implements FeatureTransform {
    private static final long serialVersionUID = 2L;

    /**
     * The schema of data.
     */
    StructType schema;
    /**
     * Lower bound.
     */
    double[] lo;
    /**
     * Upper bound.
     */
    double[] hi;
    /**
     * The span of data, i.e. hi - lo.
     */
    double[] span;

    /**
     * Constructor.
     * @param lo the lower bound.
     * @param hi the upper bound.
     */
    public Scaler(double[] lo, double[] hi) {
        if (lo.length != hi.length) {
            throw new IllegalArgumentException("Scaling factor size don't match");
        }

        this.lo = lo;
        this.hi = hi;

        span = new double[lo.length];
        for (int i = 0; i < lo.length; i++) {
            span[i] = hi[i] - lo[i];
            if (MathEx.isZero(span[i])) {
                span[i] = 1.0;
            }
        }
    }

    /**
     * Constructor.
     * @param schema the schema of data.
     * @param lo the lower bound.
     * @param hi the upper bound.
     */
    public Scaler(StructType schema, double[] lo, double[] hi) {
        this(lo, hi);
        if (schema.length() != lo.length) {
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
     * @param data the training data.
     * @return the model.
     */
    public static Scaler fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        int p = schema.length();
        double[] lo = new double[p];
        double[] hi = new double[p];

        for (int i = 0; i < p; i++) {
            if (schema.field(i).isNumeric()) {
                double[] x = data.column(i).toDoubleArray();
                lo[i] = MathEx.min(x);
                hi[i] = MathEx.max(x);
            }
        }

        return new Scaler(schema, lo, hi);
    }

    /**
     * Fits the transformation parameters.
     * @param data the training data.
     * @return the model.
     */
    public static Scaler fit(double[][] data) {
        double[] lo = MathEx.colMin(data);
        double[] hi = MathEx.colMax(data);
        return new Scaler(lo, hi);
    }

    @Override
    public double transform(double x, int i) {
        double y = (x - lo[i]) / span[i];
        if (y < 0.0) y = 0.0;
        if (y > 1.0) y = 1.0;
        return y;
    }

    @Override
    public double invert(double x, int i) {
        return x * span[i] + lo[i];
    }

    /** Returns the string representation of i-th column scaling factor. */
    private String toString(int i) {
        String field = schema == null ? String.format("V%d", i+1) : schema.field(i).name;
        return String.format("%s[%.4f, %.4f]", field, lo[i], hi[i]);
    }

    @Override
    public String toString() {
        String className = getClass().getSimpleName();
        return IntStream.range(0, lo.length)
                .mapToObj(this::toString)
                .collect(Collectors.joining(",", className + "(", ")"));
    }
}
