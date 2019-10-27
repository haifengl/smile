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

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.math.MathEx;

import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
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
    private double[] scale;

    /**
     * Constructor.
     * @param schema the schema of data.
     * @param scale the scaling factor.
     */
    public MaxAbsScaler(StructType schema, double[] scale) {
        if (schema.length() != scale.length) {
            throw new IllegalArgumentException("Schema and scaling factor size don't match");
        }
        this.schema = schema;
        this.scale = scale;

        for (int i = 0; i < scale.length; i++) {
            if (MathEx.isZero(scale[i])) {
                scale[i] = 1.0;
            }
        }
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     */
    public static MaxAbsScaler fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        double[] scale = new double[schema.length()];

        for (int i = 0; i < scale.length; i++) {
            if (schema.field(i).isNumeric()) {
                scale[i] = data.doubleVector(i).stream().map(Math::abs).max().getAsDouble();
            }
        }

        return new MaxAbsScaler(schema, scale);
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     */
    public static MaxAbsScaler fit(double[][] data) {
        return fit(DataFrame.of(data));
    }

    /** Scales a value with i-th column parameters. */
    private double scale(double x, int i) {
        return x / scale[i];
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
        for (int i = 0; i < scale.length; i++) {
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
        return IntStream.range(0, scale.length)
                .mapToObj(i -> String.format("%s[%.4f]", schema.field(i).name, scale[i]))
                .collect(Collectors.joining(",", "MaxAbsScaler(", ")"));
    }
}
