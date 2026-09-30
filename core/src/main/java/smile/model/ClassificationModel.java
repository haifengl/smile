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
import smile.classification.*;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.validation.ClassificationMetrics;

/**
 * The classification model.
 *
 * @param algorithm the algorithm name.
 * @param schema the schema of input data (without response variable).
 * @param formula the model formula.
 * @param classifier the classification model.
 * @param train the training metrics.
 * @param validation the cross-validation metrics.
 * @param test the test metrics.
 * @param tags the model metadata tags.
 *
 * @author Haifeng Li
 */
public record ClassificationModel(String algorithm,
                                  StructType schema,
                                  Formula formula,
                                  DataFrameClassifier classifier,
                                  ClassificationMetrics train,
                                  ClassificationMetrics validation,
                                  ClassificationMetrics test,
                                  Properties tags) implements Model, Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Model inference.
     * @param x the input tuple.
     * @return the prediction.
     */
    public int predict(Tuple x) {
        return classifier.predict(x);
    }

    /**
     * Model inference.
     * @param x the input tuple.
     * @param posteriori a posteriori probabilities on output.
     * @return the prediction.
     */
    public int predict(Tuple x, double[] posteriori) {
        return classifier.predict(x, posteriori);
    }

    /**
     * Returns the number of classes.
     * @return the number of classes.
     */
    public int numClasses() {
        return classifier.numClasses();
    }

    @Override
    public boolean supportsShap() {
        return classifier instanceof smile.feature.importance.SHAP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public double[][] shap(Tuple x) {
        if (classifier instanceof smile.feature.importance.SHAP shap) {
            double[] raw = ((smile.feature.importance.SHAP<Tuple>) shap).shap(x);
            int k = classifier.numClasses();
            int p = raw.length / k;
            double[][] reshaped = new double[k][p];
            for (int j = 0; j < p; j++) {
                for (int c = 0; c < k; c++) {
                    reshaped[c][j] = raw[j * k + c];
                }
            }
            return reshaped;
        }
        throw new UnsupportedOperationException("SHAP is not supported for algorithm: " + algorithm);
    }

    @Override
    public Prediction infer(Tuple x, boolean probability, boolean explain) {
        double[] posteriori = null;
        int y;
        if (probability && classifier.isSoft()) {
            posteriori = new double[classifier.numClasses()];
            y = classifier.predict(x, posteriori);
        } else {
            y = classifier.predict(x);
        }

        Explanations explanations = null;
        if (explain) {
            if (supportsShap()) {
                explanations = new Explanations(shap(x));
            } else {
                explanations = new Explanations("Not supported");
            }
        }
        return new Prediction(y, posteriori, explanations);
    }

    @Override
    public Prediction[] infer(smile.data.DataFrame data, boolean probability, boolean explain) {
        formula.bind(data.schema());
        int n = data.size();
        if (probability && classifier.isSoft()) {
            var probList = new java.util.ArrayList<double[]>(n);
            int[] pred = classifier.predict(data, probList);
            Prediction[] responses = new Prediction[n];
            for (int i = 0; i < n; i++) {
                Explanations explanations = null;
                if (explain) {
                    explanations = supportsShap()
                            ? new Explanations(shap(data.get(i)))
                            : new Explanations("Not supported");
                }
                responses[i] = new Prediction(pred[i], probList.get(i), explanations);
            }
            return responses;
        } else {
            int[] pred = classifier.predict(data);
            Prediction[] responses = new Prediction[n];
            for (int i = 0; i < n; i++) {
                Explanations explanations = null;
                if (explain) {
                    explanations = supportsShap()
                            ? new Explanations(shap(data.get(i)))
                            : new Explanations("Not supported");
                }
                responses[i] = new Prediction(pred[i], null, explanations);
            }
            return responses;
        }
    }
}
