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

import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import smile.classification.*;
import smile.classification.GradientTreeBoost;
import smile.classification.MLP;
import smile.classification.RBFNetwork;
import smile.classification.RandomForest;
import smile.classification.SVM;
import smile.clustering.CentroidClustering;
import smile.clustering.Clustering;
import smile.clustering.DeterministicAnnealing;
import smile.clustering.GMeans;
import smile.clustering.KMeans;
import smile.clustering.XMeans;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.transform.Transform;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.feature.transform.MaxAbsScaler;
import smile.feature.transform.Normalizer;
import smile.feature.transform.RobustStandardizer;
import smile.feature.transform.Scaler;
import smile.feature.transform.Standardizer;
import smile.feature.transform.WinsorScaler;
import smile.regression.*;
import smile.math.MathEx;
import smile.util.function.TimeFunction;
import smile.validation.ClassificationMetrics;
import smile.validation.CrossValidation;
import smile.validation.RegressionMetrics;
import smile.vq.BIRCH;
import smile.vq.GrowingNeuralGas;
import smile.vq.Neighborhood;
import smile.vq.NeuralGas;
import smile.vq.NeuralMap;
import smile.vq.SOM;
import smile.vq.VectorQuantizer;

/**
 * Generic model interface.
 *
 * @author Haifeng Li
 */
public interface Model {
    /** The tag key for model id. */
    String ID = "id";
    /** The tag key for model version. */
    String VERSION = "version";

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
     * Returns the model metadata tags.
     * @return the model metadata tags.
     */
    Properties tags();

    /**
     * Returns the model metadata tag.
     * @param key the tag key.
     * @return the tag value.
     */
    default String getTag(String key) {
        return tags().getProperty(key);
    }

    /**
     * Returns the model metadata tag.
     * @param key the tag key.
     * @param defaultValue a default value.
     * @return the tag value.
     */
    default String getTag(String key, String defaultValue) {
        return tags().getProperty(key, defaultValue);
    }

    /**
     * Sets a model metadata tag.
     * @param key the tag key.
     * @param value the tag value.
     */
    default void setTag(String key, String value) {
        tags().setProperty(key, value);
    }

    /**
     * Returns true if this model supports online learning updates.
     *
     * @return true if online learning is supported.
     */
    default boolean isOnline() {
        return false;
    }

    /**
     * Online updates the model with a single observation.
     *
     * @param x the input tuple (including target variable if supervised).
     * @throws UnsupportedOperationException if online learning is not supported.
     */
    default void update(Tuple x) {
        throw new UnsupportedOperationException("Online update is not supported for algorithm: " + algorithm());
    }

    /**
     * Online updates the model sequentially with a data frame of observations.
     *
     * @param data the input data frame.
     * @throws UnsupportedOperationException if online learning is not supported.
     */
    default void update(DataFrame data) {
        if (!isOnline()) {
            throw new UnsupportedOperationException("Online update is not supported for algorithm: " + algorithm());
        }
        formula().bind(data.schema());
        int n = data.size();
        for (int i = 0; i < n; i++) {
            update(data.get(i));
        }
    }

    /**
     * Returns true if this model supports SHAP feature explanations.
     *
     * @return true if SHAP explanations are supported.
     */
    default boolean supportsShap() {
        return false;
    }

    /**
     * Computes SHAP values for the input tuple.
     *
     * @param x the input tuple.
     * @return the SHAP values (double[] for regression, double[][] for classification).
     * @throws UnsupportedOperationException if SHAP explanations are not supported.
     */
    default Object shap(Tuple x) {
        throw new UnsupportedOperationException("SHAP is not supported for algorithm: " + algorithm());
    }

    /**
     * Computes SHAP values for the input tuple with custom {@link smile.feature.importance.KernelSHAP.Options}.
     *
     * @param x       the input tuple.
     * @param options the KernelSHAP options, or {@code null} to use default options.
     * @return the SHAP values (double[] for regression, double[][] for classification).
     * @throws UnsupportedOperationException if SHAP explanations are not supported.
     */
    default Object shap(Tuple x, smile.feature.importance.KernelSHAP.Options options) {
        return shap(x);
    }

    /**
     * Performs inference on a single tuple.
     *
     * @param x           the input tuple.
     * @param probability true to compute posterior probabilities for soft classifiers.
     * @param explain     true to compute feature explanations.
     * @return the prediction.
     */
    Prediction infer(Tuple x, boolean probability, boolean explain);

    /**
     * Performs inference on a single tuple with custom {@link smile.feature.importance.KernelSHAP.Options}.
     *
     * @param x           the input tuple.
     * @param probability true to compute posterior probabilities for soft classifiers.
     * @param explain     true to compute feature explanations.
     * @param options     optional KernelSHAP options (may be {@code null}).
     * @return the prediction.
     */
    default Prediction infer(Tuple x, boolean probability, boolean explain, smile.feature.importance.KernelSHAP.Options options) {
        return infer(x, probability, explain);
    }

    /**
     * Performs inference on a single tuple without explanations.
     *
     * @param x           the input tuple.
     * @param probability true to compute posterior probabilities for soft classifiers.
     * @return the prediction.
     */
    default Prediction infer(Tuple x, boolean probability) {
        return infer(x, probability, false);
    }

    /**
     * Performs inference on a single tuple.
     *
     * @param x the input tuple.
     * @return the prediction.
     */
    default Prediction infer(Tuple x) {
        return infer(x, true, false);
    }

    /**
     * Performs batch inference over a data frame.
     *
     * @param data        the input data frame.
     * @param probability true to compute posterior probabilities for soft classifiers.
     * @param explain     true to compute feature explanations.
     * @return an array of predictions.
     */
    default Prediction[] infer(DataFrame data, boolean probability, boolean explain) {
        return infer(data, probability, explain, null);
    }

    /**
     * Performs batch inference over a data frame with custom {@link smile.feature.importance.KernelSHAP.Options}.
     *
     * @param data        the input data frame.
     * @param probability true to compute posterior probabilities for soft classifiers.
     * @param explain     true to compute feature explanations.
     * @param options     optional KernelSHAP options (may be {@code null}).
     * @return an array of predictions.
     */
    default Prediction[] infer(DataFrame data, boolean probability, boolean explain, smile.feature.importance.KernelSHAP.Options options) {
        formula().bind(data.schema());
        return data.stream()
                   .map(row -> infer(row, probability, explain, options))
                   .toArray(Prediction[]::new);
    }

    /**
     * Performs batch inference over a data frame without explanations.
     *
     * @param data        the input data frame.
     * @param probability true to compute posterior probabilities for soft classifiers.
     * @return an array of predictions.
     */
    default Prediction[] infer(DataFrame data, boolean probability) {
        return infer(data, probability, false);
    }

    /**
     * Performs batch inference over a data frame.
     *
     * @param data the input data frame.
     * @return an array of predictions.
     */
    default Prediction[] infer(DataFrame data) {
        return infer(data, true, false);
    }

    /**
     * Trains a classification model. When {@code kfold >= 2}, k-fold
     * cross-validation is performed and the validation metrics are recorded.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param test the optional test data. May be {@code null}.
     * @param params the hyperparameters.
     * @param kfold number of folds for cross-validation; skipped when {@code kfold < 2}.
     * @param round the number of repeated cross-validation rounds.
     * @param ensemble if true, the cross-validation fold models are combined into
     *                 an ensemble; otherwise the final model is retrained on the
     *                 full training set.
     * @return the classification model.
     */
    static ClassificationModel classification(String algorithm, Formula formula, DataFrame data, DataFrame test,
                                              Properties params, int kfold, int round, boolean ensemble) {
        long start = System.nanoTime();
        DataFrameClassifier model;
        ClassificationMetrics validationMetrics = null;
        if (kfold < 2) {
            model = classification(algorithm, formula, data, params);
        } else {
            var cv = CrossValidation.stratify(round, kfold, formula, data,
                    (f, d) -> classification(algorithm, f, d, params));
            DataFrameClassifier[] models = cv.rounds().stream()
                    .map(fold -> fold.model())
                    .toArray(DataFrameClassifier[]::new);
            model = ensemble ? DataFrameClassifier.ensemble(models) : classification(algorithm, formula, data, params);
            validationMetrics = cv.avg();
        }

        double fitTime = (System.nanoTime() - start) / 1E6;
        var trainMetrics = ClassificationMetrics.of(fitTime, model, formula, data);
        var testMetrics = test == null ? null : ClassificationMetrics.of(model, formula, test);

        var y = formula.response().variables();
        var predictors = data.schema().fields().stream().filter(field -> !y.contains(field.name())).toList();
        var schema = new StructType(predictors);
        return new ClassificationModel(algorithm, schema, formula, model,
            trainMetrics, validationMetrics, testMetrics, (Properties) params.clone());
    }

    /**
     * Trains a classification model.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param test the optional test data.
     * @param params the hyperparameters.
     * @return the classification model.
     */
    static ClassificationModel classification(String algorithm, Formula formula, DataFrame data, DataFrame test, Properties params) {
        return classification(algorithm, formula, data, test, params, 1, 1, false);
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
            case "random-forest" ->
                    RandomForest.fit(formula, data, RandomForest.Options.of(params));
            case "gradient-boost" ->
                    GradientTreeBoost.fit(formula, data, GradientTreeBoost.Options.of(params));
            case "ada-boost" ->
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
     * Trains a regression model. When {@code kfold >= 2}, k-fold
     * cross-validation is performed and the validation metrics are recorded.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param test the optional test data. May be {@code null}.
     * @param params the hyperparameters.
     * @param kfold number of folds for cross-validation; skipped when {@code kfold < 2}.
     * @param round the number of repeated cross-validation rounds.
     * @param ensemble if true, the cross-validation fold models are combined into
     *                 an ensemble; otherwise the final model is retrained on the
     *                 full training set.
     * @return the regression model.
     */
    static RegressionModel regression(String algorithm, Formula formula, DataFrame data, DataFrame test,
                                      Properties params, int kfold, int round, boolean ensemble) {
        long start = System.nanoTime();
        DataFrameRegression model;
        RegressionMetrics validationMetrics = null;
        if (kfold < 2) {
            model = regression(algorithm, formula, data, params);
        } else {
            var cv = CrossValidation.regression(round, kfold, formula, data,
                    (f, d) -> regression(algorithm, f, d, params));
            DataFrameRegression[] models = cv.rounds().stream()
                    .map(fold -> fold.model())
                    .toArray(DataFrameRegression[]::new);
            model = ensemble ? DataFrameRegression.ensemble(models) : regression(algorithm, formula, data, params);
            validationMetrics = cv.avg();
        }

        double fitTime = (System.nanoTime() - start) / 1E6;
        var trainMetrics = RegressionMetrics.of(fitTime, model, formula, data);
        var testMetrics = test == null ? null : RegressionMetrics.of(model, formula, test);

        var y = formula.response().variables();
        var predictors = data.schema().fields().stream().filter(field -> !y.contains(field.name())).toList();
        var schema = new StructType(predictors);
        return new RegressionModel(algorithm, schema, formula, model,
            trainMetrics, validationMetrics, testMetrics, (Properties) params.clone());
    }

    /**
     * Trains a regression model.
     * @param algorithm the learning algorithm.
     * @param formula the model formula.
     * @param data the training data.
     * @param test the optional test data.
     * @param params the hyperparameters.
     * @return the regression model.
     */
    static RegressionModel regression(String algorithm, Formula formula, DataFrame data, DataFrame test, Properties params) {
        return regression(algorithm, formula, data, test, params, 1, 1, false);
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
            case "random-forest" ->
                    smile.regression.RandomForest.fit(formula, data, smile.regression.RandomForest.Options.of(params));
            case "gradient-boost" ->
                    smile.regression.GradientTreeBoost.fit(formula, data, smile.regression.GradientTreeBoost.Options.of(params));
            case "cart" ->
                    RegressionTree.fit(formula, data, RegressionTree.Options.of(params));
            case "ols" ->
                    OLS.fit(formula, data, OLS.Options.of(params));
            case "lasso" ->
                    LASSO.fit(formula, data, LASSO.Options.of(params));
            case "elastic-net" ->
                    ElasticNet.fit(formula, data, ElasticNet.Options.of(params));
            case "ridge" -> {
                double[] weights = new double[data.size()];
                Arrays.fill(weights, 1);
                yield RidgeRegression.fit(formula, data, weights, RidgeRegression.Options.of(params));
            }
            case "gaussian-process" ->
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

    /**
     * Trains a centroid-based clustering model.
     * @param algorithm the clustering algorithm ("kmeans", "xmeans", "gmeans", "deterministic-annealing", or "da").
     * @param formula the feature selection formula; if {@code null}, all columns are included.
     * @param data the training data.
     * @param params the hyperparameters.
     * @return the clustering model.
     */
    static ClusteringModel clustering(String algorithm, Formula formula, DataFrame data, Properties params) {
        Formula f = formula != null ? formula : Formula.rhs(".");
        Formula expanded = f.expand(data.schema());
        DataFrame X = expanded.x(data);
        StructType schema = X.schema();
        Properties effectiveParams = params != null ? params : new Properties();
        Transform transform = fitTransform(X, effectiveParams.getProperty("smile.feature.transform"));
        if (transform != null) {
            X = transform.apply(X);
        }
        double[][] x = X.toArray(false, CategoricalEncoder.DUMMY);
        CentroidClustering<double[], double[]> model = fitClustering(algorithm, x, effectiveParams);
        return new ClusteringModel(algorithm, schema, expanded, model, transform, (Properties) effectiveParams.clone());
    }

    /**
     * Fits a centroid-based clustering algorithm on a numeric feature matrix.
     * @param algorithm the clustering algorithm ("kmeans", "xmeans", "gmeans", "deterministic-annealing", or "da").
     * @param x the input feature matrix.
     * @param params the hyperparameters.
     * @return the fitted centroid clustering.
     */
    static CentroidClustering<double[], double[]> fitClustering(String algorithm, double[][] x, Properties params) {
        return switch (algorithm) {
            case "kmeans" -> {
                int runs = Integer.parseInt(params.getProperty("smile.clustering.runs", "1"));
                var options = Clustering.Options.of(params);
                yield runs > 1
                        ? Clustering.run(runs, () -> KMeans.fit(x, options))
                        : KMeans.fit(x, options);
            }
            case "xmeans" ->
                    XMeans.fit(x, Clustering.Options.of(params));
            case "gmeans" ->
                    GMeans.fit(x, Clustering.Options.of(params));
            case "deterministic-annealing", "da" ->
                    DeterministicAnnealing.fit(x, DeterministicAnnealing.Options.of(params));
            default ->
                    throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }

    /**
     * Trains a vector quantization model.
     * @param algorithm the vector quantizer algorithm ("som", "neural-gas", "growing-neural-gas", "gng", "neural-map", or "birch").
     * @param formula the feature selection formula; if {@code null}, all columns are included.
     * @param data the training data.
     * @param params the hyperparameters.
     * @return the vector quantization model.
     */
    static VectorQuantizerModel quantizer(String algorithm, Formula formula, DataFrame data, Properties params) {
        Formula f = formula != null ? formula : Formula.rhs(".");
        Formula expanded = f.expand(data.schema());
        DataFrame X = expanded.x(data);
        StructType schema = X.schema();
        Properties effectiveParams = params != null ? params : new Properties();
        Transform transform = fitTransform(X, effectiveParams.getProperty("smile.feature.transform"));
        if (transform != null) {
            X = transform.apply(X);
        }
        double[][] x = X.toArray(false, CategoricalEncoder.DUMMY);
        VectorQuantizer model = fitQuantizer(algorithm, x, effectiveParams);
        return new VectorQuantizerModel(algorithm, schema, expanded, model, transform, (Properties) effectiveParams.clone());
    }

    /**
     * Fits a vector quantization algorithm on a numeric feature matrix.
     * @param algorithm the vector quantizer algorithm ("som", "neural-gas", "growing-neural-gas", "gng", "neural-map", or "birch").
     * @param x the input feature matrix.
     * @param params the hyperparameters.
     * @return the fitted vector quantizer.
     */
    static VectorQuantizer fitQuantizer(String algorithm, double[][] x, Properties params) {
        int n = x.length;
        int d = x[0].length;
        return switch (algorithm) {
            case "som" -> {
                int nrow = Integer.parseInt(params.getProperty("smile.som.nrow", "10"));
                int ncol = Integer.parseInt(params.getProperty("smile.som.ncol", "10"));
                int epochs = Integer.parseInt(params.getProperty("smile.som.epochs", "20"));
                int T = Math.max(1, n * epochs);
                String alphaSpec = params.getProperty("smile.som.alpha");
                TimeFunction alpha = alphaSpec != null && !alphaSpec.isBlank()
                        ? TimeFunction.of(alphaSpec)
                        : TimeFunction.linear(0.1, T, 0.01);
                String thetaSpec = params.getProperty("smile.som.neighborhood");
                Neighborhood theta = thetaSpec != null && !thetaSpec.isBlank()
                        ? Neighborhood.of(thetaSpec)
                        : Neighborhood.Gaussian(1.0, Math.max(1.0, T / 4.0));
                double[][][] lattice = SOM.lattice(nrow, ncol, x);
                SOM som = new SOM(lattice, alpha, theta);
                for (int epoch = 0; epoch < epochs; epoch++) {
                    for (int j : MathEx.permutate(n)) {
                        som.update(x[j]);
                    }
                }
                yield som;
            }
            case "neural-gas" -> {
                int k = Integer.parseInt(params.getProperty("smile.neural_gas.k", "10"));
                int epochs = Integer.parseInt(params.getProperty("smile.neural_gas.epochs", "20"));
                int T = Math.max(1, n * epochs);
                String alphaSpec = params.getProperty("smile.neural_gas.alpha");
                TimeFunction alpha = alphaSpec != null && !alphaSpec.isBlank()
                        ? TimeFunction.of(alphaSpec)
                        : TimeFunction.exp(0.3, Math.max(1.0, T / 2.0));
                String thetaSpec = params.getProperty("smile.neural_gas.theta");
                TimeFunction theta = thetaSpec != null && !thetaSpec.isBlank()
                        ? TimeFunction.of(thetaSpec)
                        : TimeFunction.exp(Math.max(1.0, k / 2.0), Math.max(1.0, T / 8.0));
                String lifetimeSpec = params.getProperty("smile.neural_gas.lifetime");
                TimeFunction lifetime = lifetimeSpec != null && !lifetimeSpec.isBlank()
                        ? TimeFunction.of(lifetimeSpec)
                        : TimeFunction.constant(Math.max(1.0, n * 2.0));
                NeuralGas ng = new NeuralGas(CentroidClustering.seeds(x, k), alpha, theta, lifetime);
                for (int epoch = 0; epoch < epochs; epoch++) {
                    for (int j : MathEx.permutate(n)) {
                        ng.update(x[j]);
                    }
                }
                yield ng;
            }
            case "growing-neural-gas", "gng" -> {
                int epochs = Integer.parseInt(params.getProperty("smile.gng.epochs", "20"));
                double epsBest = Double.parseDouble(params.getProperty("smile.gng.eps_best", "0.2"));
                double epsNeighbor = Double.parseDouble(params.getProperty("smile.gng.eps_neighbor", "0.006"));
                int edgeLifetime = Integer.parseInt(params.getProperty("smile.gng.edge_lifetime", "50"));
                int lambda = Integer.parseInt(params.getProperty("smile.gng.lambda", "100"));
                double alpha = Double.parseDouble(params.getProperty("smile.gng.alpha", "0.5"));
                double beta = Double.parseDouble(params.getProperty("smile.gng.beta", "0.995"));
                GrowingNeuralGas gng = new GrowingNeuralGas(d, epsBest, epsNeighbor, edgeLifetime, lambda, alpha, beta);
                for (int epoch = 0; epoch < epochs; epoch++) {
                    for (int j : MathEx.permutate(n)) {
                        gng.update(x[j]);
                    }
                }
                yield gng;
            }
            case "neural-map" -> {
                int epochs = Integer.parseInt(params.getProperty("smile.neural_map.epochs", "10"));
                double radius = Double.parseDouble(params.getProperty("smile.neural_map.radius", "1.0"));
                double epsBest = Double.parseDouble(params.getProperty("smile.neural_map.eps_best", "0.05"));
                double epsNeighbor = Double.parseDouble(params.getProperty("smile.neural_map.eps_neighbor", "0.002"));
                int edgeLifetime = Integer.parseInt(params.getProperty("smile.neural_map.edge_lifetime", "50"));
                double beta = Double.parseDouble(params.getProperty("smile.neural_map.beta", "0.995"));
                double clearEps = Double.parseDouble(params.getProperty("smile.neural_map.clear_eps", "1E-7"));
                NeuralMap nm = new NeuralMap(d, radius, epsBest, epsNeighbor, edgeLifetime, beta);
                for (int epoch = 0; epoch < epochs; epoch++) {
                    for (int j : MathEx.permutate(n)) {
                        nm.update(x[j]);
                    }
                    if (clearEps > 0) {
                        nm.clear(clearEps);
                    }
                }
                yield nm;
            }
            case "birch" -> {
                int branching = Integer.parseInt(params.getProperty("smile.birch.branching", "5"));
                int leaves = Integer.parseInt(params.getProperty("smile.birch.leaves", "5"));
                double radius = Double.parseDouble(params.getProperty("smile.birch.radius", "1.0"));
                BIRCH birch = new BIRCH(d, branching, leaves, radius);
                for (double[] xi : x) {
                    birch.update(xi);
                }
                yield birch;
            }
            default ->
                    throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }

    /**
     * Fits a feature preprocessing transform on a data frame from a specification string.
     * @param data the predictor data frame.
     * @param spec the transform specification (e.g., "standardizer", "winsor(0.01,0.99)", "minmax", "MaxAbs", "L1", "L2", "Linf").
     * @return the fitted transform, or {@code null} if {@code spec} is null or blank.
     */
    private static Transform fitTransform(DataFrame data, String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String normalized = spec.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("winsor(") && normalized.endsWith(")")) {
            String args = normalized.substring("winsor(".length(), normalized.length() - 1);
            String[] parts = args.split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid winsor transform specification: " + spec);
            }
            double lower = Double.parseDouble(parts[0].trim());
            double upper = Double.parseDouble(parts[1].trim());
            return WinsorScaler.fit(data, lower, upper);
        }
        return switch (normalized) {
            case "standardizer" -> Standardizer.fit(data);
            case "robust", "robuststandardizer", "robust_standardizer" -> RobustStandardizer.fit(data);
            case "minmax", "scaler" -> Scaler.fit(data);
            case "maxabs", "max_abs", "maxabsscaler" -> MaxAbsScaler.fit(data);
            case "winsor", "winsorscaler" -> WinsorScaler.fit(data);
            case "l1" -> new Normalizer(Normalizer.Norm.L1, numericColumns(data));
            case "l2" -> new Normalizer(Normalizer.Norm.L2, numericColumns(data));
            case "linf", "l_inf" -> new Normalizer(Normalizer.Norm.L_INF, numericColumns(data));
            default -> throw new IllegalArgumentException("Unsupported feature transform: " + spec);
        };
    }

    private static String[] numericColumns(DataFrame data) {
        return data.schema().fields().stream()
                .filter(StructField::isNumeric)
                .map(StructField::name)
                .toArray(String[]::new);
    }
}
