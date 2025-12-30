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
 * @param properties the model properties.
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
                                  Properties properties) implements Model, Serializable {

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
}
