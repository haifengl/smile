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
package smile.model;

import java.io.Serializable;
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
 *
 * @author Haifeng Li
 */
public record RegressionModel(String algorithm,
                              StructType schema,
                              Formula formula,
                              DataFrameRegression regression,
                              RegressionMetrics train,
                              RegressionMetrics validation,
                              RegressionMetrics test) implements Model, Serializable {
    /**
     * Model inference.
     * @param x the input tuple.
     * @return the prediction.
     */
    public double predict(Tuple x) {
        return regression.predict(x);
    }
}
