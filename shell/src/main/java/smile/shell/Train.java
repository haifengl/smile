/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.shell;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.model.*;
import smile.model.cart.SplitRule;

/**
 * Trains a supervised learning model.
 *
 * @author Haifeng Li
 */
@Command(name = "smile train", versionProvider = VersionProvider.class,
        description = "Trains a supervised learning model.",
        mixinStandardHelpOptions = true)
public class Train {
    @Option(names = {"-m", "--model"}, required = true, description = "The model file.")
    private File model;
    @Option(names = {"-f", "--formula"}, description = "The model formula <class ~ .>.")
    private String formula;
    @Option(names = {"-d", "--data"}, required = true, description = "The training data file.")
    private File train;
    @Option(names = {"--test"}, description = "The test data file.")
    private File test;
    @Option(names = {"-t", "--type"}, description = "The data file content type.")
    private String type;
    @Option(names = {"-k", "--kfold"}, paramLabel = "<fold>", description = "k-fold cross validation.")
    private int kfold = 1;
    @Option(names = {"-r", "--round"}, description = "The number of rounds of repeated cross validation.")
    private int round = 1;
    @Option(names = {"-e", "--ensemble"}, description = "Create the ensemble of cross validation models.")
    private boolean ensemble = false;
    @Option(names = {"-s", "--seed"}, description = "The random number generator seed.")
    private long seed = 0;
    /** The algorithm name. */
    private String algorithm;
    /** The problem type is classification. */
    private boolean classification = true;
    /** The hyperparameter key-value pairs. */
    private Properties params = new Properties();

    /** Runs the training algorithm. */
    private void run() throws Exception {
        var path = model.toPath();
        var data = Read.data(train.getCanonicalPath(), type);
        var columns = Arrays.asList(data.names());

        Formula modelFormula;
        if (formula != null) {
            modelFormula = Formula.of(formula);
        } else if (columns.contains("class")) {
            modelFormula = Formula.lhs("class");
        } else if (columns.contains("target")) {
            modelFormula = Formula.lhs("target");
        } else if (columns.contains("y")) {
            modelFormula = Formula.lhs("y");
        } else {
            modelFormula = Formula.lhs(columns.getFirst());
        }

        if (seed != 0) {
           MathEx.setSeed(seed);
        }

        DataFrame testData = null;
        if (test != null) {
            testData = Read.data(test.getCanonicalPath(), type);
        }

        if (classification) {
            var model = Model.classification(algorithm, modelFormula, data, testData, params, kfold, round, ensemble);
            System.out.println("Training metrics: " + model.train());
            if (model.validation() != null) {
              System.out.println("Validation metrics: " + model.validation());
            }
            if (model.test() != null) {
              System.out.println("Test metrics: " + model.test());
            }
            Write.object(model, path);
        } else {
            var model = Model.regression(algorithm, modelFormula, data, testData, params, kfold, round, ensemble);
            System.out.println("Training metrics: " + model.train());
            if (model.validation() != null) {
              System.out.println("Validation metrics: " + model.validation());
            }
            if (model.test() != null) {
              System.out.println("Test metrics: " + model.test());
            }
            Write.object(model, path);
        }
    }

    @Command(name = "random-forest", description = "Random Forest",
             mixinStandardHelpOptions = true)
    void randomForest(
          @Option(names = {"--regression"}, description = "Train a regression model.")
          boolean regression,
          @Option(names = {"--trees"}, paramLabel = "<trees>", description = "The number of trees.")
          int trees,
          @Option(names = {"--mtry"}, paramLabel = "<features>", description = "The number of features to train node split.")
          int mtry,
          @Option(names = {"--split"}, paramLabel = "<rule>", description = "The split rule <GINI, ENTROPY, CLASSIFICATION_ERROR>.")
          SplitRule split,
          @Option(names = {"--max-depth"}, paramLabel = "<depth>", description = "The maximum tree depth.")
          int maxDepth,
          @Option(names = {"--max-nodes"}, paramLabel = "<nodes>", description = "The maximum number of leaf nodes.")
          int maxNodes,
          @Option(names = {"--node-size"}, paramLabel = "<size>", description = "The minimum leaf node size.")
          int nodeSize,
          @Option(names = {"--sampling"}, paramLabel = "<rate>", description = "The sampling rate.")
          double sampling,
          @Option(names = {"--class-weight"}, paramLabel = "<weights>", description = "The class weights.")
          String classWeight) throws Exception {

        algorithm = "random-forest";
        classification = !regression;
        if (trees > 0) params.setProperty("smile.random_forest.trees", String.valueOf(trees));
        if (mtry > 0) params.setProperty("smile.random_forest.mtry", String.valueOf(mtry));
        if (split != null) params.setProperty("smile.random_forest.split_rule", split.name());
        if (maxDepth > 0) params.setProperty("smile.random_forest.max_depth", String.valueOf(maxDepth));
        if (maxNodes > 0) params.setProperty("smile.random_forest.max_nodes", String.valueOf(maxNodes));
        if (nodeSize > 0) params.setProperty("smile.random_forest.node_size", String.valueOf(nodeSize));
        if (sampling > 0) params.setProperty("smile.random_forest.sampling_rate", String.valueOf(sampling));
        if (classWeight != null) params.setProperty("smile.random_forest.class_weight", classWeight);
        run();
    }

    @Command(name = "gradient-boost", description = "Gradient Boosting",
             mixinStandardHelpOptions = true)
    void gradientBoost(
            @Option(names = {"--regression"}, description = "Train a regression model.")
            boolean regression,
            @Option(names = {"--trees"}, paramLabel = "<trees>", description = "The number of trees.")
            int trees,
            @Option(names = {"--shrinkage"}, paramLabel = "<scale>", description = "The shrinkage parameter in (0, 1] controls the learning rate.")
            int shrinkage,
            @Option(names = {"--max-depth"}, paramLabel = "<depth>", description = "The maximum tree depth.")
            int maxDepth,
            @Option(names = {"--max-nodes"}, paramLabel = "<nodes>", description = "The maximum number of leaf nodes.")
            int maxNodes,
            @Option(names = {"--node-size"}, paramLabel = "<size>", description = "The minimum leaf node size.")
            int nodeSize,
            @Option(names = {"--sampling"}, paramLabel = "<rate>", description = "The sampling rate.")
            double sampling) throws Exception {

        algorithm = "gradient-boost";
        classification = !regression;
        if (trees > 0) params.setProperty("smile.gradient_boost.trees", String.valueOf(trees));
        if (shrinkage > 0) params.setProperty("smile.gradient_boost.shrinkage", String.valueOf(shrinkage));
        if (maxDepth > 0) params.setProperty("smile.gradient_boost.max_depth", String.valueOf(maxDepth));
        if (maxNodes > 0) params.setProperty("smile.gradient_boost.max_nodes", String.valueOf(maxNodes));
        if (nodeSize > 0) params.setProperty("smile.gradient_boost.node_size", String.valueOf(nodeSize));
        if (sampling > 0) params.setProperty("smile.gradient_boost.sampling_rate", String.valueOf(sampling));
        run();
    }

    @Command(name = "ada-boost", description = "Adaptive Boosting",
             mixinStandardHelpOptions = true)
    void adaBoost(
            @Option(names = {"--trees"}, paramLabel = "<trees>", description = "The number of trees.")
            int trees,
            @Option(names = {"--max-depth"}, paramLabel = "<depth>", description = "The maximum tree depth.")
            int maxDepth,
            @Option(names = {"--max-nodes"}, paramLabel = "<nodes>", description = "The maximum number of leaf nodes.")
            int maxNodes,
            @Option(names = {"--node-size"}, paramLabel = "<size>", description = "The minimum leaf node size.")
            int nodeSize) throws Exception {

        algorithm = "ada-boost";
        if (trees > 0) params.setProperty("smile.adaboost.trees", String.valueOf(trees));
        if (maxDepth > 0) params.setProperty("smile.adaboost.max_depth", String.valueOf(maxDepth));
        if (maxNodes > 0) params.setProperty("smile.adaboost.max_nodes", String.valueOf(maxNodes));
        if (nodeSize > 0) params.setProperty("smile.adaboost.node_size", String.valueOf(nodeSize));
        run();
    }

    @Command(name = "cart", description = "Classification and Regression Tree",
             mixinStandardHelpOptions = true)
    void cart(
            @Option(names = {"--regression"}, description = "Train a regression model.")
            boolean regression,
            @Option(names = {"--split"}, paramLabel = "<rule>", description = "The split rule <GINI, ENTROPY, CLASSIFICATION_ERROR>.")
            SplitRule split,
            @Option(names = {"--max-depth"}, paramLabel = "<depth>", description = "The maximum tree depth.")
            int maxDepth,
            @Option(names = {"--max-nodes"}, paramLabel = "<nodes>", description = "The maximum number of leaf nodes.")
            int maxNodes,
            @Option(names = {"--node-size"}, paramLabel = "<size>", description = "The minimum leaf node size.")
            int nodeSize) throws Exception {

        algorithm = "cart";
        classification = !regression;
        if (split != null) params.setProperty("smile.cart.split_rule", split.name());
        if (maxDepth > 0) params.setProperty("smile.cart.max_depth", String.valueOf(maxDepth));
        if (maxNodes > 0) params.setProperty("smile.cart.max_nodes", String.valueOf(maxNodes));
        if (nodeSize > 0) params.setProperty("smile.cart.node_size", String.valueOf(nodeSize));
        run();
    }

    @Command(name = "logistic", description = "Logistic Regression",
             mixinStandardHelpOptions = true)
    void logistic(
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--lambda"}, paramLabel = "<value>", description = "The regularization on linear weights.")
            double lambda,
            @Option(names = {"--iterations"}, paramLabel = "<value>", description = "The maximum number of iterations.")
            int iterations,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance to stop iterations.")
            double tolerance) throws Exception {

        algorithm = "logistic";
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        if (lambda > 0) params.setProperty("smile.logistic.lambda", String.valueOf(lambda));
        if (iterations > 0) params.setProperty("smile.logistic.iterations", String.valueOf(iterations));
        if (tolerance > 0) params.setProperty("smile.logistic.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "fisher", description = "Fisher's Linear Discriminant",
             mixinStandardHelpOptions = true)
    void fisher(
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--dimension"}, paramLabel = "<value>", description = "The dimension of mapped space.")
            int dimension,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance if a covariance matrix is singular.")
            double tolerance) throws Exception {

        algorithm = "fisher";
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        if (dimension > 0) params.setProperty("smile.fisher.dimension", String.valueOf(dimension));
        if (tolerance > 0) params.setProperty("smile.fisher.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "lda", description = "Linear Discriminant Analysis",
             mixinStandardHelpOptions = true)
    void lda(
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--priori"}, paramLabel = "<value>", description = "The priori probability of each class.")
            String priori,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance if a covariance matrix is singular.")
            double tolerance) throws Exception {

        algorithm = "lda";
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        if (priori != null) params.setProperty("smile.lda.priori", priori);
        if (tolerance > 0) params.setProperty("smile.lda.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "qda", description = "Quadratic Discriminant Analysis",
             mixinStandardHelpOptions = true)
    void qda(
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--priori"}, paramLabel = "<value>", description = "The priori probability of each class.")
            String priori,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance if a covariance matrix is singular.")
            double tolerance) throws Exception {

        algorithm = "qda";
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        if (priori != null) params.setProperty("smile.qda.priori", priori);
        if (tolerance > 0) params.setProperty("smile.qda.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "rda", description = "Regularized Discriminant Analysis",
             mixinStandardHelpOptions = true)
    void rda(
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--alpha"}, paramLabel = "<value>", required = true, description = "The regularization factor in [0, 1] allows a continuum of models between LDA and QDA.")
            double alpha,
            @Option(names = {"--priori"}, paramLabel = "<value>", description = "The priori probability of each class.")
            String priori,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance if a covariance matrix is singular.")
            double tolerance) throws Exception {

        algorithm = "rda";
        params.setProperty("smile.rda.alpha", String.valueOf(alpha));
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        if (priori != null) params.setProperty("smile.rda.priori", priori);
        if (tolerance > 0) params.setProperty("smile.rda.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "mlp", description = "Multilayer Perceptron",
             mixinStandardHelpOptions = true)
    void mlp(
            @Option(names = {"--regression"}, description = "Train a regression model.")
            boolean regression,
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--layers"}, paramLabel = "<network>", required = true, description = "The neural network layers <ReLU(100)|Sigmoid(30)>.")
            String layers,
            @Option(names = {"--epochs"}, paramLabel = "<value>", description = "The number of training epochs.")
            int epochs,
            @Option(names = {"--mini-batch"}, paramLabel = "<value>", description = "The mini batch sample size.")
            int miniBatch,
            @Option(names = {"--learning-rate"}, paramLabel = "<value>", description = "The learning rate schedule <0.01, linear(0.01, 10000, 0.001), piecewise(...), polynomial(...), inverse(...), exp(...)>.")
            String learningRate,
            @Option(names = {"--momentum"}, paramLabel = "<value>", description = "The momentum schedule.")
            String momentum,
            @Option(names = {"--weight-decay"}, paramLabel = "<value>", description = "The weight decay.")
            double weightDecay,
            @Option(names = {"--clip_norm"}, paramLabel = "<value>", description = "The gradient clipping norm.")
            double clipNorm,
            @Option(names = {"--rho"}, paramLabel = "<value>", description = "RMSProp rho.")
            double rho,
            @Option(names = {"--epsilon"}, paramLabel = "<value>", description = "RMSProp epsilon.")
            double epsilon) throws Exception {

        algorithm = "mlp";
        classification = !regression;
        params.setProperty("smile.mlp.layers", layers);
        if (epochs > 0) params.setProperty("smile.mlp.epochs", String.valueOf(epochs));
        if (miniBatch > 0) params.setProperty("smile.mlp.mini_batch", String.valueOf(miniBatch));
        if (learningRate != null) params.setProperty("smile.mlp.learning_rate", learningRate);
        if (momentum != null) params.setProperty("smile.mlp.momentum", momentum);
        if (weightDecay > 0) params.setProperty("smile.mlp.weight_decay", String.valueOf(weightDecay));
        if (clipNorm > 0) params.setProperty("smile.mlp.clip_norm", String.valueOf(clipNorm));
        if (rho > 0) params.setProperty("smile.mlp.RMSProp.rho", String.valueOf(rho));
        if (epsilon > 0) params.setProperty("smile.mlp.RMSProp.epsilon", String.valueOf(epsilon));
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        run();
    }

    @Command(name = "svm", description = "Support Vector Machine",
             mixinStandardHelpOptions = true)
    void svm(
            @Option(names = {"--regression"}, description = "Train a regression model.")
            boolean regression,
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--kernel"}, paramLabel = "<function>", required = true, description = "The kernel function.")
            String kernel,
            @Option(names = {"-C"}, paramLabel = "<value>", description = "The soft margin penalty parameter.")
            double C,
            @Option(names = {"--epsilon"}, paramLabel = "<value>", description = "The parameter of epsilon-insensitive hinge loss.")
            double epsilon,
            @Option(names = {"--ovr"}, description = "One vs Rest strategy for multiclass classification.")
            boolean ovr,
            @Option(names = {"--ovo"}, description = "One vs One strategy for multiclass classification.")
            boolean ovo,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance of convergence test.")
            double tolerance) throws Exception {

        algorithm = "svm";
        classification = !regression;
        params.setProperty("smile.svm.kernel", kernel);
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        if (C > 0) params.setProperty("smile.svm.C", String.valueOf(C));
        if (epsilon > 0) params.setProperty("smile.svm.epsilon", String.valueOf(epsilon));
        if (ovr) params.setProperty("smile.svm.strategy", "ovr");
        if (ovo) params.setProperty("smile.svm.strategy", "ovo");
        if (tolerance > 0) params.setProperty("smile.svm.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "rbf", description = "Radial Basis Function Network",
             mixinStandardHelpOptions = true)
    void rbf(
            @Option(names = {"--regression"}, description = "Train a regression model.")
            boolean regression,
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--neurons"}, paramLabel = "<value>", required = true, description = "The number of neurons (radial basis functions).")
            int neurons,
            @Option(names = {"--normalize"}, description = "Normalized RBF network.")
            boolean normalize) throws Exception {

        algorithm = "rbf";
        classification = !regression;
        params.setProperty("smile.rbf.neurons", String.valueOf(neurons));
        params.setProperty("smile.rbf.normalize", String.valueOf(normalize));
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        run();
    }

    @Command(name = "ols", description = "Ordinary Least Squares",
             mixinStandardHelpOptions = true)
    void ols(
            @Option(names = {"--method"}, paramLabel = "<qr or svd>", description = "The fitting method <qr, svd>.")
            String method,
            @Option(names = {"--stderr"}, description = "Compute the standard errors of the estimate of parameters.")
            boolean stderr,
            @Option(names = {"--recursive"}, description = "Recursive least squares.")
            boolean recursive) throws Exception {

        algorithm = "ols";
        params.setProperty("smile.ols.standard_error", String.valueOf(stderr));
        params.setProperty("smile.ols.recursive", String.valueOf(recursive));
        if (method != null) params.setProperty("smile.ols.method", method);
        run();
    }

    @Command(name = "lasso", description = "Least Absolute Shrinkage and Selection Operator",
             mixinStandardHelpOptions = true)
    void lasso(
            @Option(names = {"--lambda"}, paramLabel = "<value>", required = true, description = "The regularization on linear weights.")
            double lambda,
            @Option(names = {"--iterations"}, paramLabel = "<value>", description = "The maximum number of iteration.")
            int iterations,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance to stop iterations (relative target duality gap).")
            double tolerance) throws Exception {

        algorithm = "lasso";
        params.setProperty("smile.lasso.lambda", String.valueOf(lambda));
        if (iterations > 0) params.setProperty("smile.lasso.iterations", String.valueOf(iterations));
        if (tolerance > 0) params.setProperty("smile.lasso.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "ridge", description = "Ridge Regression",
             mixinStandardHelpOptions = true)
    void ridge(
            @Option(names = {"--lambda"}, paramLabel = "<value>", required = true, description = "The regularization on linear weights.")
            double lambda) throws Exception {

        algorithm = "ridge";
        params.setProperty("smile.ridge.lambda", String.valueOf(lambda));
        run();
    }

    @Command(name = "elastic-net", description = "Least Absolute Shrinkage and Selection Operator",
             mixinStandardHelpOptions = true)
    void elasticNet(
            @Option(names = {"--lambda1"}, paramLabel = "<value>", required = true, description = "The L1 regularization on linear weights.")
            double lambda1,
            @Option(names = {"--lambda2"}, paramLabel = "<value>", required = true, description = "The L2 regularization on linear weights.")
            double lambda2,
            @Option(names = {"--iterations"}, paramLabel = "<value>", description = "The maximum number of iteration.")
            int iterations,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The tolerance to stop iterations (relative target duality gap).")
            double tolerance) throws Exception {

        algorithm = "elastic-net";
        params.setProperty("smile.elastic_net.lambda1", String.valueOf(lambda1));
        params.setProperty("smile.elastic_net.lambda2", String.valueOf(lambda2));
        if (iterations > 0) params.setProperty("smile.elastic_net.iterations", String.valueOf(iterations));
        if (tolerance > 0) params.setProperty("smile.elastic_net.tolerance", String.valueOf(tolerance));
        run();
    }

    @Command(name = "gaussian-process", description = "Gaussian Process Regression",
             mixinStandardHelpOptions = true)
    void gaussianProcess(
            @Option(names = {"--transform"}, paramLabel = "<rule>", description = "The feature transformation <standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>.")
            String transform,
            @Option(names = {"--kernel"}, paramLabel = "<function>", required = true, description = "The kernel function.")
            String kernel,
            @Option(names = {"--noise"}, paramLabel = "<value>", required = true, description = "The noise variance.")
            double noise,
            @Option(names = {"--normalize"}, description = "Normalize the response variable.")
            boolean normalize,
            @Option(names = {"--iterations"}, paramLabel = "<value>", description = "The maximum number of HPO iterations.")
            int iterations,
            @Option(names = {"--tolerance"}, paramLabel = "<value>", description = "The stopping tolerance for HPO.")
            double tolerance) throws Exception {

        algorithm = "gaussian-process";
        params.setProperty("smile.gaussian_process.kernel", kernel);
        params.setProperty("smile.gaussian_process.noise", String.valueOf(noise));
        params.setProperty("smile.gaussian_process.normalize", String.valueOf(normalize));
        if (transform != null) params.setProperty("smile.feature.transform", transform);
        if (iterations > 0) params.setProperty("smile.gaussian_process.iterations", String.valueOf(iterations));
        if (tolerance > 0) params.setProperty("smile.gaussian_process.tolerance", String.valueOf(tolerance));
        run();
    }
}
