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
package smile.classification;

import java.io.Serial;
import java.io.Serializable;
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
import smile.feature.importance.KernelSHAP;
import smile.feature.importance.SHAP;
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
        Formula expanded = formula.expand(data.schema());
        DataFrame X = expanded.x(data);
        StructType schema = X.schema();
        double[][] x = X.toArray(false, CategoricalEncoder.DUMMY);
        int[] y = expanded.y(data).toIntArray();

        Classifier<double[]> model = trainer.fit(x, y, params);
        KernelSHAP.Options shapOptions = KernelSHAP.Options.of(params);
        boolean directVector = (x.length > 0 && x[0].length == schema.length());
        double[][] bgVectors = directVector ? KernelSHAP.sample(x, KernelSHAP.DEFAULT_BACKGROUND_SAMPLES) : null;
        DataFrame bgFrame = directVector ? null : KernelSHAP.sample(X, KernelSHAP.DEFAULT_BACKGROUND_SAMPLES);

        return new VectorDataFrameClassifier(expanded, schema, model, bgVectors, bgFrame, shapOptions);
    }

    /**
     * Return an ensemble of multiple base models to obtain better
     * predictive performance.
     *
     * @param models the base models.
     * @return the ensemble model.
     */
    static DataFrameClassifier ensemble(DataFrameClassifier... models) {
        if (models.length == 0) {
            throw new IllegalArgumentException("Empty ensemble models.");
        }

        if (models[0] == null) {
            throw new IllegalArgumentException("Null base model at index 0");
        }

        int k = models[0].numClasses();
        int[] labels = models[0].classes();
        for (int i = 0; i < models.length; i++) {
            DataFrameClassifier model = models[i];
            if (model == null) {
                throw new IllegalArgumentException("Null base model at index " + i);
            }

            if (model.numClasses() != k || !Arrays.equals(model.classes(), labels)) {
                throw new IllegalArgumentException("Incompatible base model classes at index " + i);
            }
        }

        return new EnsembleDataFrameClassifier(models.clone());
    }

    /**
     * Adapter wrapping a vector {@code Classifier<double[]>} as a {@code DataFrameClassifier} with {@code SHAP<Tuple>} support.
     */
    final class VectorDataFrameClassifier implements DataFrameClassifier, SHAP<Tuple>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Formula formula;
        private final StructType schema;
        private final Classifier<double[]> model;
        private final double[][] bgVectors;
        private final DataFrame bgFrame;
        private final KernelSHAP.Options shapOptions;
        private transient volatile KernelSHAP<double[]> vectorExplainer;
        private transient volatile KernelSHAP<Tuple> tupleExplainer;

        VectorDataFrameClassifier(Formula formula,
                                  StructType schema,
                                  Classifier<double[]> model,
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

        @Override
        public boolean isSoft() {
            return model.isSoft();
        }

        @Override
        public boolean isOnline() {
            return model.isOnline();
        }

        @Override
        public int numClasses() {
            return model.numClasses();
        }

        @Override
        public int[] classes() {
            return model.classes();
        }

        private double[] vectorize(Tuple x) {
            Tuple xt = x.schema().equals(schema) ? x : formula.x(x);
            return xt.toArray(false, CategoricalEncoder.DUMMY);
        }

        @Override
        public int predict(Tuple x) {
            return model.predict(vectorize(x));
        }

        @Override
        public int predict(Tuple x, double[] posteriori) {
            return model.predict(vectorize(x), posteriori);
        }

        @Override
        public void update(Tuple x, int y) {
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
     * Ensemble of {@code DataFrameClassifier} models with {@code SHAP<Tuple>} support.
     */
    final class EnsembleDataFrameClassifier implements DataFrameClassifier, SHAP<Tuple>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final DataFrameClassifier[] models;
        /** The ensemble is a soft classifier only if all the base models are. */
        private final boolean soft;
        /** The ensemble is an online learner only if all the base models are. */
        private final boolean online;

        EnsembleDataFrameClassifier(DataFrameClassifier[] models) {
            this.models = models;
            this.soft = Arrays.stream(models).allMatch(DataFrameClassifier::isSoft);
            this.online = Arrays.stream(models).allMatch(DataFrameClassifier::isOnline);
        }

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
            if (!soft) {
                throw new UnsupportedOperationException("soft classification with a hard classifier");
            }

            if (posteriori.length != numClasses()) {
                throw new IllegalArgumentException("Invalid posteriori vector size: " + posteriori.length);
            }

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

        @Override
        public double[] shap(Tuple x) {
            return shap(x, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public double[] shap(Tuple x, KernelSHAP.Options options) {
            double[] phi = null;
            for (DataFrameClassifier model : models) {
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
