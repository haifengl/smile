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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Properties;
import smile.anomaly.IsolationForest;
import smile.anomaly.LOF;
import smile.anomaly.SVM;
import smile.anomaly.TimeSeriesAnomaly;
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
 * @param detector  the underlying anomaly detector ({@link IsolationForest}, {@link LOF},
 *                  {@link SVM}, or {@link TimeSeriesAnomaly}).
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
     * Compact constructor normalizing {@code tags} to a non-null {@link Properties}.
     */
    public AnomalyModel {
        if (tags == null) {
            tags = new Properties();
        }
    }

    @Serial
    private Object readResolve() throws ObjectStreamException {
        if (algorithm == null || algorithm.isBlank()) {
            throw new InvalidObjectException("AnomalyModel algorithm must not be null or blank");
        }
        if (schema == null) {
            throw new InvalidObjectException("AnomalyModel schema must not be null");
        }
        if (formula == null) {
            throw new InvalidObjectException("AnomalyModel formula must not be null");
        }
        if (detector == null) {
            throw new InvalidObjectException("AnomalyModel detector must not be null");
        }
        if (Double.isNaN(threshold)) {
            throw new InvalidObjectException("AnomalyModel threshold must not be NaN");
        }
        return this;
    }

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
            case TimeSeriesAnomaly ts -> {
                requireSingleColumn(v, "score(Tuple)");
                yield ts.score(v[0]);
            }
            default -> throw new IllegalStateException("Unsupported anomaly detector: " + detector.getClass().getName());
        };
    }

    /**
     * Computes the anomaly or decision scores for a data frame.
     * <p>
     * For a {@link TimeSeriesAnomaly} detector the rows are consumed
     * <b>sequentially</b>: row {@code i} is scored against the training history
     * extended by rows {@code 0..i-1}. The row order must therefore be the time
     * order. All other detectors are independent of row order.
     *
     * @param data the input data frame.
     * @return the anomaly or decision scores.
     */
    public double[] score(DataFrame data) {
        formula.bind(data.schema());
        if (detector instanceof TimeSeriesAnomaly ts) {
            return ts.score(timeSeries(data));
        }
        return data.stream().mapToDouble(this::score).toArray();
    }

    /**
     * Extracts the single time-series column from a data frame, preserving row order.
     */
    private double[] timeSeries(DataFrame data) {
        int n = data.size();
        double[] series = new double[n];
        for (int i = 0; i < n; i++) {
            series[i] = vectorize(data.get(i))[0];
        }
        return series;
    }

    private static void requireSingleColumn(double[] v, String method) {
        if (v.length != 1) {
            throw new IllegalArgumentException(
                    method + " requires a single numeric column for a time-series anomaly model, but got " + v.length);
        }
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
            case TimeSeriesAnomaly ts -> {
                requireSingleColumn(v, "predict(Tuple)");
                yield ts.score(v[0]) > threshold;
            }
            default -> throw new IllegalStateException("Unsupported anomaly detector: " + detector.getClass().getName());
        };
    }

    /**
     * Predicts whether each observation in a data frame is an anomaly.
     * <p>
     * For a {@link TimeSeriesAnomaly} detector the rows are consumed
     * sequentially, as in {@link #score(DataFrame)}.
     *
     * @param data the input data frame.
     * @return an array of anomaly flags.
     */
    public boolean[] predict(DataFrame data) {
        formula.bind(data.schema());
        if (detector instanceof TimeSeriesAnomaly ts) {
            double[] scores = ts.score(timeSeries(data));
            boolean[] flags = new boolean[scores.length];
            for (int i = 0; i < scores.length; i++) {
                flags[i] = scores[i] > threshold;
            }
            return flags;
        }
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
            case TimeSeriesAnomaly ts -> {
                requireSingleColumn(v, "infer");
                s = ts.score(v[0]);
                isAnomaly = s > threshold;
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
