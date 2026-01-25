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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.math.MathEx;

/**
 * Classification trait on DataFrame.
 *
 * @author Haifeng Li
 */
public interface DataFrameClassifier extends Classifier<Tuple> {
    /**
     * The classifier trainer.
     * @param <M> the type of model.
     */
    interface Trainer<M extends DataFrameClassifier>  {
        /**
         * Fits a classification model with the default hyperparameters.
         * @param formula a symbolic description of the model to be fitted.
         * @param data the data frame of the explanatory and response variables.
         * @return the model
         */
        default M fit(Formula formula, DataFrame data) {
            Properties params = new Properties();
            return fit(formula, data, params);
        }

        /**
         * Fits a classification model.
         * @param formula a symbolic description of the model to be fitted.
         * @param data the data frame of the explanatory and response variables.
         * @param params the hyperparameters.
         * @return the model
         */
        M fit(Formula formula, DataFrame data, Properties params);
    }

    /**
     * Returns the formula associated with the model.
     * @return the formula associated with the model.
     */
    Formula formula();

    /**
     * Returns the predictor schema.
     * @return the predictor schema.
     */
    StructType schema();

    /**
     * Predicts the class labels of a data frame.
     *
     * @param data the data frame.
     * @return the predicted class labels.
     */
    default int[] predict(DataFrame data) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());
        return data.stream().mapToInt(this::predict).toArray();
    }

    /**
     * Predicts the class labels of a dataset.
     *
     * @param data the data frame.
     * @param posteriori an empty list to store a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(DataFrame data, List<double[]> posteriori) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());

        int n = data.size();
        int k = numClasses();
        double[][] prob = new double[n][k];
        Collections.addAll(posteriori, prob);
        return IntStream.range(0, n).parallel()
                .map(i -> predict(data.get(i), prob[i]))
                .toArray();
    }

    /**
     * Fits a vector classifier on data frame.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param params the hyperparameters.
     * @param trainer the training lambda.
     * @return the model.
     */
    static DataFrameClassifier of(Formula formula, DataFrame data, Properties params, Classifier.Trainer<double[], ?> trainer) {
        DataFrame X = formula.x(data);
        StructType schema = X.schema();
        double[][] x = X.toArray(false, CategoricalEncoder.DUMMY);
        int[] y = formula.y(data).toIntArray();

        Classifier<double[]> model = trainer.fit(x, y, params);

        return new DataFrameClassifier() {
            @Override
            public Formula formula() {
                return formula;
            }

            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public int numClasses() {
                return model.numClasses();
            }

            @Override
            public int[] classes() {
                return model.classes();
            }

            @Override
            public int predict(Tuple x) {
                return model.predict(formula.x(x).toArray());
            }

            @Override
            public int predict(Tuple x, double[] posteriori) {
                return model.predict(formula.x(x).toArray(), posteriori);
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
    static DataFrameClassifier ensemble(DataFrameClassifier... models) {
        return new DataFrameClassifier() {
            /** The ensemble is a soft classifier only if all the base models are. */
            private final boolean soft = Arrays.stream(models).allMatch(DataFrameClassifier::isSoft);

            /** The ensemble is an online learner only if all the base models are. */
            private final boolean online = Arrays.stream(models).allMatch(DataFrameClassifier::isOnline);

            @Override
            public boolean isSoft() {
                return soft;
            }

            @Override
            public boolean isOnline() {
                return online;
            }

            @Override
            public int numClasses() {
                return models[0].numClasses();
            }

            @Override
            public int[] classes() {
                return models[0].classes();
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
            public int predict(Tuple x) {
                int[] labels = new int[models.length];
                for (int i = 0; i < models.length; i++) {
                    labels[i] = models[i].predict(x);
                }
                return MathEx.mode(labels);
            }

            @Override
            public int predict(Tuple x, double[] posteriori) {
                Arrays.fill(posteriori, 0.0);
                double[] prob = new double[posteriori.length];

                for (DataFrameClassifier model : models) {
                    model.predict(x, prob);
                    for (int i = 0; i < prob.length; i++) {
                        posteriori[i] += prob[i];
                    }
                }

                for (int i = 0; i < posteriori.length; i++) {
                    posteriori[i] /= models.length;
                }
                return MathEx.whichMax(posteriori);
            }

            @Override
            public void update(Tuple x, int y) {
                for (DataFrameClassifier model : models) {
                    model.update(x, y);
                }
            }
        };
    }
}
