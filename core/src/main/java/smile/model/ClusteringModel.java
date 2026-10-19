/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Properties;
import smile.clustering.CentroidClustering;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.transform.Transform;
import smile.data.type.StructType;
import smile.feature.importance.KernelSHAP;

/**
 * The centroid-based clustering model.
 *
 * @param algorithm  the clustering algorithm name.
 * @param schema     the schema of input data (without response variable).
 * @param formula    the feature selection formula.
 * @param clustering the fitted centroid clustering model.
 * @param transform  optional feature preprocessing transform; may be {@code null}.
 * @param tags       the model metadata tags.
 *
 * @author Haifeng Li
 */
public record ClusteringModel(String algorithm,
                              StructType schema,
                              Formula formula,
                              CentroidClustering<double[], double[]> clustering,
                              Transform transform,
                              Properties tags) implements Model, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a clustering model without a preprocessing transform.
     *
     * @param algorithm  the clustering algorithm name.
     * @param schema     the schema of input data (without response variable).
     * @param formula    the feature selection formula.
     * @param clustering the fitted centroid clustering model.
     * @param tags       the model metadata tags.
     */
    public ClusteringModel(String algorithm,
                           StructType schema,
                           Formula formula,
                           CentroidClustering<double[], double[]> clustering,
                           Properties tags) {
        this(algorithm, schema, formula, clustering, null, tags);
    }

    /**
     * Converts a tuple into a feature vector after applying the formula and
     * optional feature transformation.
     *
     * @param x the input tuple.
     * @return the feature vector.
     */
    private double[] vectorize(Tuple x) {
        Tuple xt = x.schema().equals(schema) ? x : formula.x(x);
        if (transform != null) {
            xt = transform.apply(xt);
        }
        return xt.toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Predicts the cluster label for a single observation.
     *
     * @param x the input tuple.
     * @return the assigned cluster index.
     */
    public int predict(Tuple x) {
        return clustering.predict(vectorize(x));
    }

    /**
     * Predicts the cluster labels for a data frame.
     *
     * @param data the input data frame.
     * @return the assigned cluster indices.
     */
    public int[] predict(DataFrame data) {
        formula.bind(data.schema());
        return data.stream().mapToInt(this::predict).toArray();
    }

    /**
     * Returns the number of clusters.
     *
     * @return the number of clusters.
     */
    public int k() {
        return clustering.k();
    }

    /**
     * Returns the cluster centroids.
     *
     * @return the cluster centroids.
     */
    public double[][] centers() {
        return clustering.centers();
    }

    /**
     * Returns the centroid of the i-th cluster.
     *
     * @param i the cluster index.
     * @return the cluster centroid.
     */
    public double[] center(int i) {
        return clustering.center(i);
    }

    /**
     * Returns the total distortion (average within-cluster sum-of-squares).
     *
     * @return the distortion.
     */
    public double distortion() {
        return clustering.distortion();
    }

    @Override
    public Prediction infer(Tuple x, boolean probability, boolean explain) {
        return infer(x, probability, explain, null);
    }

    @Override
    public Prediction infer(Tuple x, boolean probability, boolean explain, KernelSHAP.Options options) {
        int cluster = predict(x);
        double[] centroid = clustering.center(cluster);
        Explanations explanations = explain ? new Explanations("Not supported") : null;
        return new Prediction(cluster, null, centroid, explanations);
    }

    @Override
    public Prediction[] infer(DataFrame data, boolean probability, boolean explain) {
        return infer(data, probability, explain, null);
    }

    @Override
    public Prediction[] infer(DataFrame data, boolean probability, boolean explain, KernelSHAP.Options options) {
        formula.bind(data.schema());
        int n = data.size();
        Prediction[] responses = new Prediction[n];
        for (int i = 0; i < n; i++) {
            responses[i] = infer(data.get(i), probability, explain, options);
        }
        return responses;
    }
}
