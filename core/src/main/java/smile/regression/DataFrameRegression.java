/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.regression;

import java.util.Arrays;
import java.util.function.BiFunction;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.feature.FeatureTransform;

/**
 * Regression trait on DataFrame.
 *
 * @author Haifeng Li
 */
public interface DataFrameRegression extends Regression<Tuple> {
    /**
     * Returns the model formula.
     * @return the model formula.
     */
    Formula formula();

    /**
     * Returns the schema of predictors.
     * @return the schema of predictors.
     */
    StructType schema();

    /**
     * Predicts the dependent variables of a data frame.
     *
     * @param data the data frame.
     * @return the predicted values.
     */
    default double[] predict(DataFrame data) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());
        return data.stream().mapToDouble(this::predict).toArray();
    }

    /**
     * Fits a vector regression model on data frame.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param trainer the training lambda.
     * @return the model.
     */
    static DataFrameRegression of(Formula formula, DataFrame data, BiFunction<double[][], double[], Regression<double[]>> trainer) {
        return of(null, formula, data, trainer);
    }

    /**
     * Fits a vector regression model on data frame.
     *
     * @param transformer the feature transformation (e.g. standardizer, minmax, winsor(0.01, 0.99), etc.)
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param trainer the training lambda.
     * @return the model.
     */
    static DataFrameRegression of(String transformer, Formula formula, DataFrame data, BiFunction<double[][], double[], Regression<double[]>> trainer) {
        DataFrame X = formula.x(data);
        StructType schema = X.schema();
        double[][] x = X.toArray(false, CategoricalEncoder.DUMMY);
        double[] y = formula.y(data).toDoubleArray();

        FeatureTransform preprocessor = FeatureTransform.of(transformer, x);
        if (preprocessor != null) {
            x = preprocessor.transform(x);
        }

        Regression<double[]> model = trainer.apply(x, y);

        return new DataFrameRegression() {
            @Override
            public Formula formula() {
                return formula;
            }

            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public double predict(Tuple x) {
                double[] vector = formula.x(x).toArray();
                if (preprocessor != null) {
                    preprocessor.transform(vector, vector);
                }
                return model.predict(vector);
            }
        };
    }

    /**
     * Return an ensemble of multiple base models to obtain better
     * predictive performance.
     *
     * @param models the base models.
     * @return the ensemble model.
     */
    static DataFrameRegression ensemble(DataFrameRegression... models) {
        return new DataFrameRegression() {
            /** The ensemble is an online learner only if all the base models are. */
            private boolean online = Arrays.stream(models).allMatch(model -> model.online());

            @Override
            public boolean online() {
                return online;
            }

            @Override
            public Formula formula() {
                return models[0].formula();
            }

            @Override
            public StructType schema() {
                return models[0].schema();
            }

            @Override
            public double predict(Tuple x) {
                double y = 0;
                for (DataFrameRegression model : models) {
                    y += model.predict(x);
                }
                return y / models.length;
            }

            @Override
            public void update(Tuple x, double y) {
                for (DataFrameRegression model : models) {
                    model.update(x, y);
                }
            }
        };
    }
}
