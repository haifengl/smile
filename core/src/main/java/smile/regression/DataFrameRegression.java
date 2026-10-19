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
package smile.regression;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.feature.importance.KernelSHAP;
import smile.feature.importance.SHAP;

/**
 * Regression trait on DataFrame.
 *
 * @author Haifeng Li
 */
public interface DataFrameRegression extends Regression<Tuple> {
    /**
     * The regression trainer.
     * @param <M> the type of model.
     */
    interface Trainer<M extends DataFrameRegression> {
        /**
         * Fits a regression model with the default hyperparameters.
         * @param formula a symbolic description of the model to be fitted.
         * @param data the data frame of the explanatory and response variables.
         * @return the model
         */
        default M fit(Formula formula, DataFrame data) {
            Properties params = new Properties();
            return fit(formula, data, params);
        }

        /**
         * Fits a regression model.
         * @param formula a symbolic description of the model to be fitted.
         * @param data the data frame of the explanatory and response variables.
         * @param params the hyperparameters.
         * @return the model
         */
        M fit(Formula formula, DataFrame data, Properties params);
    }

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
     * @param params the hyperparameters.
     * @param trainer the training lambda.
     * @return the model.
     */
    static DataFrameRegression of(Formula formula, DataFrame data, Properties params, Regression.Trainer<double[], ?> trainer) {
        Formula expanded = formula.expand(data.schema());
        DataFrame X = expanded.x(data);
        StructType schema = X.schema();
        double[][] x = X.toArray(false, CategoricalEncoder.DUMMY);
        double[] y = expanded.y(data).toDoubleArray();

        Regression<double[]> model = trainer.fit(x, y, params);
        KernelSHAP.Options shapOptions = KernelSHAP.Options.of(params);
        boolean directVector = (x.length > 0 && x[0].length == schema.length());
        double[][] bgVectors = directVector ? KernelSHAP.sample(x, KernelSHAP.DEFAULT_BACKGROUND_SAMPLES) : null;
        DataFrame bgFrame = directVector ? null : KernelSHAP.sample(X, KernelSHAP.DEFAULT_BACKGROUND_SAMPLES);

        return new VectorDataFrameRegression(expanded, schema, model, bgVectors, bgFrame, shapOptions);
    }

    /**
     * Return an ensemble of multiple base models to obtain better
     * predictive performance.
     *
     * @param models the base models.
     * @return the ensemble model.
     */
    static DataFrameRegression ensemble(DataFrameRegression... models) {
        if (models.length == 0) {
            throw new IllegalArgumentException("Empty ensemble models.");
        }
        for (int i = 0; i < models.length; i++) {
            if (models[i] == null) {
                throw new IllegalArgumentException("Null base model at index " + i);
            }
        }
        return new EnsembleDataFrameRegression(models.clone());
    }

    /**
     * Adapter wrapping a vector {@code Regression<double[]>} as a {@code DataFrameRegression} with {@code SHAP<Tuple>} support.
     */
    final class VectorDataFrameRegression implements DataFrameRegression, SHAP<Tuple>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Formula formula;
        private final StructType schema;
        private final Regression<double[]> model;
        private final double[][] bgVectors;
        private final DataFrame bgFrame;
        private final KernelSHAP.Options shapOptions;
        private transient volatile KernelSHAP<double[]> vectorExplainer;
        private transient volatile KernelSHAP<Tuple> tupleExplainer;

        VectorDataFrameRegression(Formula formula,
                                  StructType schema,
                                  Regression<double[]> model,
                                  double[][] bgVectors,
                                  DataFrame bgFrame,
                                  KernelSHAP.Options shapOptions) {
            this.formula = formula;
            this.schema = schema;
            this.model = model;
            this.bgVectors = bgVectors;
            this.bgFrame = bgFrame;
            this.shapOptions = shapOptions;
        }

        @Override
        public Formula formula() {
            return formula;
        }

        @Override
        public StructType schema() {
            return schema;
        }

        private double[] vectorize(Tuple x) {
            Tuple xt = x.schema().equals(schema) ? x : formula.x(x);
            return xt.toArray(false, CategoricalEncoder.DUMMY);
        }

        @Override
        public double predict(Tuple x) {
            return model.predict(vectorize(x));
        }

        @Override
        public boolean online() {
            return model.online();
        }

        @Override
        public void update(Tuple x, double y) {
            model.update(vectorize(x), y);
        }

        @Override
        public double[] shap(Tuple x) {
            return shap(x, null);
        }

        @Override
        public double[] shap(Tuple x, KernelSHAP.Options options) {
            KernelSHAP.Options opts = options != null ? options : shapOptions;
            if (bgVectors != null) {
                KernelSHAP<double[]> exp = vectorExplainer;
                if (exp == null) {
                    synchronized (this) {
                        exp = vectorExplainer;
                        if (exp == null) {
                            exp = KernelSHAP.of(bgVectors, model, shapOptions);
                            vectorExplainer = exp;
                        }
                    }
                }
                return exp.shap(vectorize(x), opts);
            } else {
                KernelSHAP<Tuple> exp = tupleExplainer;
                if (exp == null) {
                    synchronized (this) {
                        exp = tupleExplainer;
                        if (exp == null) {
                            exp = KernelSHAP.of(bgFrame, this, shapOptions);
                            tupleExplainer = exp;
                        }
                    }
                }
                Tuple xt = x.schema().equals(schema) ? x : formula.x(x);
                return exp.shap(xt, opts);
            }
        }
    }

    /**
     * Ensemble of {@code DataFrameRegression} models with {@code SHAP<Tuple>} support.
     */
    final class EnsembleDataFrameRegression implements DataFrameRegression, SHAP<Tuple>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final DataFrameRegression[] models;
        /** The ensemble is an online learner only if all the base models are. */
        private final boolean online;

        EnsembleDataFrameRegression(DataFrameRegression[] models) {
            this.models = models;
            this.online = Arrays.stream(models).allMatch(DataFrameRegression::online);
        }

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

        @Override
        public double[] shap(Tuple x) {
            return shap(x, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public double[] shap(Tuple x, KernelSHAP.Options options) {
            double[] phi = null;
            for (DataFrameRegression model : models) {
                if (!(model instanceof SHAP<?> s)) {
                    throw new UnsupportedOperationException("Base model does not support SHAP: " + model.getClass().getName());
                }
                double[] mphi = ((SHAP<Tuple>) s).shap(x, options);
                if (phi == null) {
                    phi = mphi.clone();
                } else {
                    for (int i = 0; i < phi.length; i++) {
                        phi[i] += mphi[i];
                    }
                }
            }
            if (phi == null) {
                return new double[0];
            }
            for (int i = 0; i < phi.length; i++) {
                phi[i] /= models.length;
            }
            return phi;
        }
    }
}
