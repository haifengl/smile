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
package smile.feature.selection;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.data.vector.ValueVector;
import smile.regression.DataFrameRegression;

/**
 * Feature selection using tree ensemble models (such as Random Forest or
 * Gradient Tree Boost).
 * <p>
 * Ensemble methods measure the importance of features by aggregating the
 * impurity decrease (such as Gini impurity or variance reduction) across all
 * trees in the ensemble. Features that appear frequently in splits yielding
 * large impurity reductions achieve higher importance scores.
 *
 * @param feature The feature name.
 * @param importance The variable importance score.
 * @author Haifeng Li
 */
public record EnsembleSelection(String feature, double importance) implements Comparable<EnsembleSelection> {
    @Override
    public int compareTo(EnsembleSelection other) {
        return Double.compare(importance, other.importance);
    }

    @Override
    public String toString() {
        return String.format("EnsembleSelection(%s, %.4f)", feature, importance);
    }

    /**
     * Constructs ensemble feature selection scores from a feature schema and importance vector.
     *
     * @param schema the predictors schema.
     * @param importance the importance scores corresponding to each schema field.
     * @return an array of ensemble selection scores.
     */
    public static EnsembleSelection[] of(StructType schema, double[] importance) {
        if (schema.length() != importance.length) {
            throw new IllegalArgumentException(String.format(
                    "Schema length (%d) does not match importance length (%d)",
                    schema.length(), importance.length));
        }

        return IntStream.range(0, schema.length())
                .mapToObj(i -> new EnsembleSelection(schema.field(i).name(), importance[i]))
                .toArray(EnsembleSelection[]::new);
    }

    /**
     * Extracts ensemble selection scores from a trained classification model.
     *
     * @param model the trained classification model.
     * @return an array of ensemble selection scores.
     */
    public static EnsembleSelection[] of(DataFrameClassifier model) {
        if (model instanceof smile.classification.RandomForest forest) {
            return of(forest.schema(), forest.importance());
        } else if (model instanceof smile.classification.GradientTreeBoost gbt) {
            return of(gbt.schema(), gbt.importance());
        } else if (model instanceof smile.classification.AdaBoost ada) {
            return of(ada.schema(), ada.importance());
        } else if (model instanceof smile.classification.DecisionTree dt) {
            return of(dt.schema(), dt.importance());
        }

        throw new IllegalArgumentException("Model does not provide feature importance: " + model.getClass().getName());
    }

    /**
     * Extracts ensemble selection scores from a trained regression model.
     *
     * @param model the trained regression model.
     * @return an array of ensemble selection scores.
     */
    public static EnsembleSelection[] of(DataFrameRegression model) {
        if (model instanceof smile.regression.RandomForest forest) {
            return of(forest.schema(), forest.importance());
        } else if (model instanceof smile.regression.GradientTreeBoost gbt) {
            return of(gbt.schema(), gbt.importance());
        } else if (model instanceof smile.regression.RegressionTree rt) {
            return of(rt.schema(), rt.importance());
        }

        throw new IllegalArgumentException("Model does not provide feature importance: " + model.getClass().getName());
    }

    /**
     * Fits an ensemble model to calculate feature importance scores.
     * Automatically chooses classification or regression based on the response variable type.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of explanatory and response variables.
     * @return an array of ensemble selection scores.
     */
    public static EnsembleSelection[] fit(Formula formula, DataFrame data) {
        formula = formula.expand(data.schema());
        ValueVector y = formula.y(data);
        if (y.dtype().isFloating()) {
            return of(smile.regression.RandomForest.fit(formula, data));
        } else {
            return of(smile.classification.RandomForest.fit(formula, data));
        }
    }

    /**
     * Fits an ensemble model to calculate feature importance scores with hyperparameter options.
     * Automatically chooses classification or regression based on the response variable type.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of explanatory and response variables.
     * @param props the hyperparameters.
     * @return an array of ensemble selection scores.
     */
    public static EnsembleSelection[] fit(Formula formula, DataFrame data, Properties props) {
        formula = formula.expand(data.schema());
        ValueVector y = formula.y(data);
        if (y.dtype().isFloating()) {
            return of(smile.regression.RandomForest.fit(formula, data, smile.regression.RandomForest.Options.of(props)));
        } else {
            return of(smile.classification.RandomForest.fit(formula, data, smile.classification.RandomForest.Options.of(props)));
        }
    }

    /**
     * Fits an ensemble model to calculate feature importance scores.
     *
     * @param data the data frame of explanatory and response variables.
     * @param response the column name of the response variable.
     * @return an array of ensemble selection scores.
     */
    public static EnsembleSelection[] fit(DataFrame data, String response) {
        return fit(Formula.lhs(response), data);
    }

    /**
     * Fits an ensemble model to calculate feature importance scores with hyperparameter options.
     *
     * @param data the data frame of explanatory and response variables.
     * @param response the column name of the response variable.
     * @param props the hyperparameters.
     * @return an array of ensemble selection scores.
     */
    public static EnsembleSelection[] fit(DataFrame data, String response, Properties props) {
        return fit(Formula.lhs(response), data, props);
    }

    /**
     * Returns the names of the top {@code k} features in descending order of importance.
     *
     * @param scores the ensemble selection scores.
     * @param k the number of top features to return.
     * @return the top {@code k} feature names.
     */
    public static String[] top(EnsembleSelection[] scores, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }
        return Arrays.stream(scores)
                .sorted(Comparator.reverseOrder())
                .limit(k)
                .map(EnsembleSelection::feature)
                .toArray(String[]::new);
    }

    /**
     * Returns the names of features with importance score greater than or equal to a threshold,
     * sorted in descending order of importance.
     *
     * @param scores the ensemble selection scores.
     * @param minImportance the minimum importance threshold.
     * @return the selected feature names.
     */
    public static String[] threshold(EnsembleSelection[] scores, double minImportance) {
        return Arrays.stream(scores)
                .filter(s -> s.importance >= minImportance)
                .sorted(Comparator.reverseOrder())
                .map(EnsembleSelection::feature)
                .toArray(String[]::new);
    }
}
