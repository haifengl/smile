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
import smile.anomaly.IsolationForest;
import smile.anomaly.LOF;
import smile.anomaly.SVM;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.transform.Transform;
import smile.data.type.StructType;
import smile.feature.importance.KernelSHAP;

/**
 * The anomaly detection model.
 *
 * @param algorithm the algorithm name.
 * @param schema    the schema of input data (without response variable).
 * @param formula   the model formula.
 * @param detector  the underlying anomaly detector ({@link IsolationForest}, {@link LOF}, or {@link SVM}).
 * @param threshold the anomaly decision threshold.
 * @param transform optional feature preprocessing transform; may be {@code null}.
 * @param tags      the model metadata tags.
 *
 * @author Haifeng Li
 */
public record AnomalyModel(String algorithm,
                           StructType schema,
                           Formula formula,
                           Serializable detector,
                           double threshold,
                           Transform transform,
                           Properties tags) implements Model, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an anomaly detection model without a preprocessing transform.
     *
     * @param algorithm the algorithm name.
     * @param schema    the schema of input data (without response variable).
     * @param formula   the model formula.
     * @param detector  the underlying anomaly detector.
     * @param threshold the anomaly decision threshold.
     * @param tags      the model metadata tags.
     */
    public AnomalyModel(String algorithm,
                        StructType schema,
                        Formula formula,
                        Serializable detector,
                        double threshold,
                        Properties tags) {
        this(algorithm, schema, formula, detector, threshold, null, tags);
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
     * Computes the anomaly or decision score for a single observation.
     *
     * @param x the input tuple.
     * @return the anomaly or decision score.
     */
    @SuppressWarnings("unchecked")
    public double score(Tuple x) {
        double[] v = vectorize(x);
        return switch (detector) {
            case IsolationForest iforest -> iforest.score(v);
            case LOF<?> lof -> ((LOF<double[]>) lof).score(v);
            case SVM<?> svm -> ((SVM<double[]>) svm).score(v);
            default -> throw new IllegalStateException("Unsupported anomaly detector: " + detector.getClass().getName());
        };
    }

    /**
     * Computes the anomaly or decision scores for a data frame.
     *
     * @param data the input data frame.
     * @return the anomaly or decision scores.
     */
    public double[] score(DataFrame data) {
        formula.bind(data.schema());
        return data.stream().mapToDouble(this::score).toArray();
    }

    /**
     * Predicts whether a single observation is an anomaly using the model's threshold.
     *
     * @param x the input tuple.
     * @return {@code true} if the observation is predicted as an anomaly.
     */
    @SuppressWarnings("unchecked")
    public boolean predict(Tuple x) {
        double[] v = vectorize(x);
        return switch (detector) {
            case IsolationForest iforest -> iforest.predict(v, threshold);
            case LOF<?> lof -> ((LOF<double[]>) lof).predict(v, threshold);
            case SVM<?> svm -> ((SVM<double[]>) svm).predict(v, threshold);
            default -> throw new IllegalStateException("Unsupported anomaly detector: " + detector.getClass().getName());
        };
    }

    /**
     * Predicts whether each observation in a data frame is an anomaly.
     *
     * @param data the input data frame.
     * @return an array of anomaly flags.
     */
    public boolean[] predict(DataFrame data) {
        formula.bind(data.schema());
        int n = data.size();
        boolean[] predictions = new boolean[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = predict(data.get(i));
        }
        return predictions;
    }

    @Override
    public Prediction infer(Tuple x, boolean probability, boolean explain) {
        return infer(x, probability, explain, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Prediction infer(Tuple x, boolean probability, boolean explain, KernelSHAP.Options options) {
        double[] v = vectorize(x);
        double s;
        boolean isAnomaly;
        switch (detector) {
            case IsolationForest iforest -> {
                s = iforest.score(v);
                isAnomaly = s > threshold;
            }
            case LOF<?> lof -> {
                s = ((LOF<double[]>) lof).score(v);
                isAnomaly = s > threshold;
            }
            case SVM<?> svm -> {
                s = ((SVM<double[]>) svm).score(v);
                isAnomaly = s < threshold;
            }
            default -> throw new IllegalStateException("Unsupported anomaly detector: " + detector.getClass().getName());
        }
        Explanations explanations = explain ? new Explanations("Not supported") : null;
        return new Prediction(isAnomaly, s, explanations);
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
