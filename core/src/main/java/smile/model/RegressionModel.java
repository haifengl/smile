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
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.regression.*;
import smile.validation.RegressionMetrics;

/**
 * The regression model.
 * @param algorithm the algorithm name.
 * @param schema the schema of input data (without response variable).
 * @param formula the model formula.
 * @param regression the regression model.
 * @param train the training metrics.
 * @param validation the cross-validation metrics.
 * @param test the test metrics.
 * @param tags the model metadata tags.
 *
 * @author Haifeng Li
 */
public record RegressionModel(String algorithm,
                              StructType schema,
                              Formula formula,
                              DataFrameRegression regression,
                              RegressionMetrics train,
                              RegressionMetrics validation,
                              RegressionMetrics test,
                              Properties tags) implements Model, Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Model inference.
     * @param x the input tuple.
     * @return the prediction.
     */
    public double predict(Tuple x) {
        return regression.predict(x);
    }

    @Override
    public boolean supportsShap() {
        return regression instanceof smile.feature.importance.SHAP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public double[] shap(Tuple x) {
        if (regression instanceof smile.feature.importance.SHAP shap) {
            return ((smile.feature.importance.SHAP<Tuple>) shap).shap(x);
        }
        throw new UnsupportedOperationException("SHAP is not supported for algorithm: " + algorithm);
    }

    @Override
    public Prediction infer(Tuple x, boolean probability, boolean explain) {
        double y = regression.predict(x);
        Explanations explanations = null;
        if (explain) {
            if (supportsShap()) {
                explanations = new Explanations(shap(x));
            } else {
                explanations = new Explanations("Not supported");
            }
        }
        return new Prediction(y, null, explanations);
    }

    @Override
    public Prediction[] infer(smile.data.DataFrame data, boolean probability, boolean explain) {
        formula.bind(data.schema());
        double[] pred = regression.predict(data);
        int n = pred.length;
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
