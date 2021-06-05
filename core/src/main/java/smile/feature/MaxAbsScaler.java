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

import smile.data.DataFrame;
import smile.data.type.StructType;
import smile.math.MathEx;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Scales each feature by its maximum absolute value. This class scales and
 * translates each feature individually such that the maximal absolute value
 * of each feature in the training set will be 1.0. It does not shift/center
 * the data, and thus does not destroy any sparsity.
 *
 * @author Haifeng Li
 */
public class MaxAbsScaler implements FeatureTransform {
    private static final long serialVersionUID = 2L;

    /**
     * The schema of data.
     */
    protected StructType schema;
    /**
     * Scaling factor.
     */
    private final double[] scale;

    /**
     * Constructor.
     * @param scale the scaling factor.
     */
    public MaxAbsScaler(double[] scale) {
        this.scale = scale;

        for (int i = 0; i < scale.length; i++) {
            if (MathEx.isZero(scale[i])) {
                scale[i] = 1.0;
            }
        }
    }

    /**
     * Constructor.
     * @param schema the schema of data.
     * @param scale the scaling factor.
     */
    public MaxAbsScaler(StructType schema, double[] scale) {
        this(scale);
        if (schema.length() != scale.length) {
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
    public static MaxAbsScaler fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        int p = schema.length();
        double[] scale = new double[p];

        int n = data.size();
        for (int j = 0; j < p; j++) {
            if (schema.field(j).isNumeric()) {
                double max = 0.0;
                for (int i = 0; i < n; i++) {
                    max = Math.max(max, Math.abs(data.getDouble(i, j)));
                }
                scale[j] = max;
            }
        }

        return new MaxAbsScaler(schema, scale);
    }

    /**
     * Fits the transformation parameters.
     * @param data The training data.
     * @return the model.
     */
    public static MaxAbsScaler fit(double[][] data) {
        int p = data[0].length;
        double[] scale = new double[p];

        for (double[] datum : data) {
            for (int j = 0; j < p; j++) {
                scale[j] = Math.max(scale[j], Math.abs(datum[j]));
            }
        }

        return new MaxAbsScaler(scale);
    }

    @Override
    public double transform(double x, int i) {
        return x / scale[i];
    }

    @Override
    public double invert(double x, int i) {
        return x * scale[i];
    }

    /** Returns the string representation of i-th column scaling factor. */
    private String toString(int i) {
        String field = schema == null ? String.format("V%d", i+1) : schema.field(i).name;
        return String.format("%s[%.4f]", field, scale[i]);
    }

    @Override
    public String toString() {
        return IntStream.range(0, scale.length)
                .mapToObj(this::toString)
                .collect(Collectors.joining(",", "MaxAbsScaler(", ")"));
    }
}
