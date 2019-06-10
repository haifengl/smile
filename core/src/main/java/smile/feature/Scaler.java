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

import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import smile.data.type.DataType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.math.MathEx;
import smile.data.DataFrame;
import smile.data.Tuple;
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
     * Constructor.
     * @param schema the schema of data.
     * @param lo the lower bound.
     * @param hi the upper bound.
     */
    public Scaler(StructType schema, double[] lo, double[] hi) {
        if (schema.length() != lo.length || lo.length != hi.length) {
            throw new IllegalArgumentException("Schema and scaling factor size don't match");
        }

        this.schema = schema;
        this.lo = lo;
        this.hi = hi;

        for (int i = 0; i < lo.length; i++) {
            hi[i] -= lo[i];
            if (MathEx.isZero(hi[i])) {
                hi[i] = 1.0;
            }
        }
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     */
    public static Scaler fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        double[] lo = new double[schema.length()];
        double[] hi = new double[schema.length()];

        for (int i = 0; i < lo.length; i++) {
            if (DataType.isDouble(schema.field(i).type)) {
                lo[i] = data.doubleVector(i).stream().min().getAsDouble();
                hi[i] = data.doubleVector(i).stream().max().getAsDouble();
            }
        }

        return new Scaler(schema, lo, hi);
    }

    /** Scales a value with i-th column parameters. */
    private double scale(double x, int i) {
        double y = (x - lo[i]) / hi[i];
        if (y < 0.0) y = 0.0;
        if (y > 1.0) y = 1.0;
        return y;
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
        for (int i = 0; i < lo.length; i++) {
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
        return IntStream.range(0, lo.length).mapToObj(
                i -> String.format("%s[%.4f, %.4f]", schema.field(i).name, lo[i], hi[i])
        ).collect(Collectors.joining(",", "Scaler(", ")"));
    }
}
