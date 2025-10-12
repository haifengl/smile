/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.extraction;

import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.transform.Transform;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.DoubleVector;
import smile.tensor.DenseMatrix;

/**
 * A projection is a kind of feature extraction technique that transforms data
 * from the input space to a feature space, linearly or non-linearly. Often,
 * projections are used to reduce dimensionality, for example PCA and random
 * projection. However, kernel-based methods, e.g. Kernel PCA, can actually map
 * the data into a much higher dimensional space.
 *
 * @author Haifeng Li
 */
public class Projection implements Transform {
    /**
     * The projection matrix. The dimension reduced data
     * can be obtained by y = W * x.
     */
    public final DenseMatrix projection;
    /**
     * The schema of output space.
     */
    public final StructType schema;
    /**
     * The fields of input space.
     */
    public final String[] columns;

    /**
     * Constructor.
     * @param projection the projection matrix.
     * @param prefix the output field name prefix.
     * @param columns the input fields.
     */
    public Projection(DenseMatrix projection, String prefix, String... columns) {
        this.projection = projection;
        int p = projection.nrow();
        StructField[] fields = IntStream.range(1, p+1)
                .mapToObj(i -> new StructField(prefix + i, DataTypes.DoubleType))
                .toArray(StructField[]::new);
        this.schema = new StructType(fields);
        this.columns = columns;
    }

    @Override
    public Tuple apply(Tuple x) {
        double[] y = apply(x.toArray(columns));
        return Tuple.of(schema, y);
    }

    @Override
    public DataFrame apply(DataFrame data) {
        double[][] y = apply(data.toArray(columns));

        int n = data.size();
        int p = projection.nrow();
        DoubleVector[] vectors = new DoubleVector[p];
        for (int j = 0; j < p; j++) {
            double[] x = new double[n];
            for (int i = 0; i < x.length; i++) {
                x[i] = y[i][j];
            }
            vectors[j] = new DoubleVector(schema.field(j), x);
        }
        return new DataFrame(vectors);
    }

    /**
     * Project a data point to the feature space.
     * @param x the data point.
     * @return the projection in the feature space.
     */
    public double[] apply(double[] x) {
        return postprocess(projection.mv(preprocess(x)).toArray(new double[0]));
    }

    /**
     * Project a set of data to the feature space.
     * @param x the data set.
     * @return the projection in the feature space.
     */
    public double[][] apply(double[][] x) {
        double[][] y = new double[x.length][];
        for (int i = 0; i < x.length; i++) {
            y[i] = apply(x[i]);
        }
        return y;
    }

    /**
     * Preprocess the input vector before projection.
     * @param x the input vector of projection.
     * @return the preprocessed vector.
     */
    protected double[] preprocess(double[] x) {
        return x;
    }

    /**
     * Postprocess the output vector after projection.
     * @param x the output vector of projection.
     * @return the postprocessed vector.
     */
    protected double[] postprocess(double[] x) {
        return x;
    }
}
