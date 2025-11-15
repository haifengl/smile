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

import java.util.Arrays;
import java.util.Properties;
import smile.classification.*;
import smile.classification.GradientTreeBoost;
import smile.classification.MLP;
import smile.classification.RBFNetwork;
import smile.classification.RandomForest;
import smile.classification.SVM;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.regression.*;
import smile.validation.ClassificationMetrics;
import smile.validation.CrossValidation;
import smile.validation.RegressionMetrics;

/**
 * Generic model interface.
 *
 * @author Haifeng Li
 */
public interface Model {
    /**
     * Returns the algorithm name.
     * @return the algorithm name.
     */
    String algorithm();

    /**
     * Returns the schema of input data (without response variable).
     * @return the schema of input data (without response variable).
     */
    StructType schema();

    /**
     * Returns the model formula.
     * @return the model formula.
     */
    Formula formula();

    /**
     * Trains a classification model by cross validation.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param params the hyperparameters.
     * @param kfold k-fold cross validation.
     * @param round the number of repeated cross validation.
     * @param ensemble create the ensemble of cross validation models if true.
     * @param test the optional test data.
     * @return the classification model.
     */
    static ClassificationModel classification(String algorithm, Formula formula, DataFrame data, Properties params,
                                              int kfold, int round, boolean ensemble, DataFrame test) {
        long start = System.nanoTime();
        DataFrameClassifier model;
        ClassificationMetrics validationMetrics = null;
        if (kfold < 2) {
            model = classification(algorithm, formula, data, params);
        } else {
            var cv = CrossValidation.stratify(round, kfold, formula, data,
                    (f, d) -> classification(algorithm, f, d, params));
            DataFrameClassifier[] models = new DataFrameClassifier[kfold];
            for (int i = 0; i < kfold; i++) models[i] = cv.rounds().get(i).model();
            model = ensemble ? DataFrameClassifier.ensemble(models) : classification(algorithm, formula, data, params);
            validationMetrics = cv.avg();
        }

        double fitTime = (System.nanoTime() - start) / 1E6;
        var trainMetrics = ClassificationMetrics.of(fitTime, model, formula, data);
        var testMetrics = test == null ? null : ClassificationMetrics.of(model, formula, test);

        var y = formula.response().variables();
        var predictors = data.schema().fields().stream().filter(field -> !y.contains(field.name())).toList();
        var schema = new StructType(predictors);
        return new ClassificationModel(algorithm, schema, formula, model, trainMetrics, validationMetrics, testMetrics);
    }

    /**
     * Trains a classification model.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param params the hyperparameters.
     * @return the classification model.
     */
    static DataFrameClassifier classification(String algorithm, Formula formula, DataFrame data, Properties params) {
        return switch (algorithm) {
            case "random_forest" ->
                    RandomForest.fit(formula, data, RandomForest.Options.of(params));
            case "gradient_boost" ->
                    GradientTreeBoost.fit(formula, data, GradientTreeBoost.Options.of(params));
            case "adaboost" ->
                    AdaBoost.fit(formula, data, AdaBoost.Options.of(params));
            case "cart" ->
                    DecisionTree.fit(formula, data, DecisionTree.Options.of(params));
            case "logistic" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], LogisticRegression>() {
                        @Override
                        public LogisticRegression fit(double[][] x, int[] y, Properties params) {
                            return LogisticRegression.fit(x, y, LogisticRegression.Options.of(params));
                        }
                    });
            case "fisher" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], FLD>() {
                        @Override
                        public FLD fit(double[][] x, int[] y, Properties params) {
                            return FLD.fit(x, y, params);
                        }
                    });
            case "lda" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], LDA>() {
                        @Override
                        public LDA fit(double[][] x, int[] y, Properties params) {
                            return LDA.fit(x, y, params);
                        }
                    });
            case "qda" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], QDA>() {
                        @Override
                        public QDA fit(double[][] x, int[] y, Properties params) {
                            return QDA.fit(x, y, params);
                        }
                    });
            case "rda" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], RDA>() {
                        @Override
                        public RDA fit(double[][] x, int[] y, Properties params) {
                            return RDA.fit(x, y, params);
                        }
                    });
            case "mlp" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], MLP>() {
                        @Override
                        public MLP fit(double[][] x, int[] y, Properties params) {
                            return MLP.fit(x, y, params);
                        }
                    });
            case "svm" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], Classifier<double[]>>() {
                        @Override
                        public Classifier<double[]> fit(double[][] x, int[] y, Properties params) {
                            return SVM.fit(x, y, params);
                        }
                    });
            case "rbf" ->
                    DataFrameClassifier.of(formula, data, params, new Classifier.Trainer<double[], RBFNetwork<double[]>>() {
                        @Override
                        public RBFNetwork<double[]> fit(double[][] x, int[] y, Properties params) {
                            return RBFNetwork.fit(x, y, params);
                        }
                    });
            default ->
                    throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }

    /**
     * Trains a regression model.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param params the hyperparameters.
     * @param kfold k-fold cross validation if kfold > 1.
     * @param round the number of repeated cross validation.
     * @param ensemble create the ensemble of cross validation models if true.
     * @param test the test data.
     * @return the regression model.
     */
    static RegressionModel regression(String algorithm, Formula formula, DataFrame data, Properties params,
                                             int kfold, int round, boolean ensemble, DataFrame test) {
        long start = System.nanoTime();
        DataFrameRegression model;
        RegressionMetrics validationMetrics = null;
        if (kfold < 2) {
            model = regression(algorithm, formula, data, params);
        } else {
            var cv = CrossValidation.regression(round, kfold, formula, data,
                    (f, d) -> regression(algorithm, f, d, params));
            DataFrameRegression[] models = new DataFrameRegression[kfold];
            for (int i = 0; i < kfold; i++) models[i] = cv.rounds().get(i).model();
            model = ensemble ? DataFrameRegression.ensemble(models) : regression(algorithm, formula, data, params);
            validationMetrics = cv.avg();
        }

        double fitTime = (System.nanoTime() - start) / 1E6;
        var trainMetrics = RegressionMetrics.of(fitTime, model, formula, data);
        var testMetrics = test == null ? null : RegressionMetrics.of(model, formula, test);

        var y = formula.response().variables();
        var predictors = data.schema().fields().stream().filter(field -> !y.contains(field.name())).toList();
        var schema = new StructType(predictors);
        return new RegressionModel(algorithm, schema, formula, model, trainMetrics, validationMetrics, testMetrics);
    }

    /**
     * Trains a regression model.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param params the hyperparameters.
     * @return the regression model.
     */
    static DataFrameRegression regression(String algorithm, Formula formula, DataFrame data, Properties params) {
        return switch (algorithm) {
            case "random_forest" ->
                    smile.regression.RandomForest.fit(formula, data, smile.regression.RandomForest.Options.of(params));
            case "gradient_boost" ->
                    smile.regression.GradientTreeBoost.fit(formula, data, smile.regression.GradientTreeBoost.Options.of(params));
            case "cart" ->
                    RegressionTree.fit(formula, data, RegressionTree.Options.of(params));
            case "ols" ->
                    OLS.fit(formula, data, OLS.Options.of(params));
            case "lasso" ->
                    LASSO.fit(formula, data, LASSO.Options.of(params));
            case "elastic_net" ->
                    ElasticNet.fit(formula, data, ElasticNet.Options.of(params));
            case "ridge" -> {
                double[] weights = new double[data.size()];
                Arrays.fill(weights, 1);
                yield RidgeRegression.fit(formula, data, weights, RidgeRegression.Options.of(params));
            }
            case "gaussian_process" ->
                    DataFrameRegression.of(formula, data, params, new Regression.Trainer<double[], GaussianProcessRegression<double[]>>() {
                        @Override
                        public GaussianProcessRegression<double[]> fit(double[][] x, double[] y, Properties params) {
                            return GaussianProcessRegression.fit(x, y, params);
                        }
                    });
            case "mlp" ->
                    DataFrameRegression.of(formula, data, params, new Regression.Trainer<double[], smile.regression.MLP>() {
                        @Override
                        public smile.regression.MLP fit(double[][] x, double[] y, Properties params) {
                            return smile.regression.MLP.fit(x, y, params);
                        }
                    });
            case "svm" ->
                    DataFrameRegression.of(formula, data, params, new Regression.Trainer<double[], Regression<double[]>>() {
                        @Override
                        public Regression<double[]> fit(double[][] x, double[] y, Properties params) {
                            return smile.regression.SVM.fit(x, y, params);
                        }
                    });
            case "rbf" ->
                    DataFrameRegression.of(formula, data, params, new Regression.Trainer<double[], smile.regression.RBFNetwork<double[]>>() {
                        @Override
                        public smile.regression.RBFNetwork<double[]> fit(double[][] x, double[] y, Properties params) {
                            return smile.regression.RBFNetwork.fit(x, y, params);
                        }
                    });
            default ->
                    throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }
}
