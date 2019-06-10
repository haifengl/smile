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

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataType;
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
            if (DataType.isDouble(schema.field(i).type)) {
                scale[i] = data.doubleVector(i).stream().map(Math::abs).max().getAsDouble();
            }
        }

        return new MaxAbsScaler(schema, scale);
    }

    /** Scales a value with i-th column parameters. */
    private double scale(double x, int i) {
        return x / scale[i];
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
        for (int i = 0; i < scale.length; i++) {
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
        return IntStream.range(0, scale.length).mapToObj(
                i -> String.format("%s[%.4f]", schema.field(i).name, scale[i])
        ).collect(Collectors.joining(",", "MaxAbsScaler(", ")"));
    }
}
