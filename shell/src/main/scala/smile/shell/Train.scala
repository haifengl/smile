/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.shell

import java.util.Properties
import scopt.OParser
import smile.data.formula._
import smile.io.Read
import smile.math.MathEx
import smile.model._
import smile.validation._

/**
  * Train command options.
  * @param algorithm the algorithm name.
  * @param formula the model formula.
  * @param train the training data file path.
  * @param test the test data file path.
  * @param format the input data format.
  * @param model the model file path.
  * @param classification true if the problem type is classification.
  * @param kfold k-fold cross validation.
  * @param round the number of rounds of repeated cross validation.
  * @param ensemble the flag to create the ensemble of cross validation models.
  * @param seed the random number generator seed.
  * @param params the hyperparameter key-value pairs.
  */
case class TrainConfig(algorithm: String = "",
                       formula: Option[String] = None,
                       train: String = "",
                       test: Option[String] = None,
                       format: String = "",
                       model: String = "",
                       classification: Boolean = true,
                       kfold: Int = 1,
                       round: Int = 1,
                       ensemble: Boolean = false,
                       seed: Option[Long] = None,
                       params: Properties = new Properties())

/**
  * Trains a supervised learning model.
  */
object Train {
  /**
    * Runs a training job.
    * @param args the command line arguments.
    */
  def apply(args: Array[String]): Unit = {
    parse(args) match {
      case Some(config) =>
        val data = Read.data(config.train, config.format)
        val test = config.test.map(Read.data(_, config.format))

        val formula: Formula = config.formula.map(Formula.of(_)).getOrElse({
          // Uses 'class' or 'y' or the first column as the target
          // and the rest as the predictors.
          val columns = data.names()
          val target = if (columns.contains("class")) "class"
            else if (columns.contains("y")) "y"
            else columns(0)
          Formula.lhs(target)
        })

        config.seed.map(MathEx.setSeed(_))
        if (config.classification) {
          val model = ClassificationModel(config.algorithm, formula, data, config.params, config.kfold, config.round, config.ensemble, test)
          println(s"Training metrics: ${model.train}")
          model.validation.map(metrics => println(s"Validation metrics: ${metrics}"))
          model.test.map(metrics => println(s"Test metrics: ${metrics}"))
          smile.write(model, config.model)
        } else {
          val model = RegressionModel(config.algorithm, formula, data, config.params, config.kfold, config.round, config.ensemble, test)
          println(s"Training metrics: ${model.train}")
          model.validation.map(metrics => println(s"Validation metrics: ${metrics}"))
          model.test.map(metrics => println(s"Test metrics: ${metrics}"))
          smile.write(model, config.model)
          if (test.isDefined) {
            val metrics = RegressionMetrics.of(model.regression, formula, test.get)
            println(s"Validation metrics: ${metrics}")
          }
        }
      case _ => ()
    }
  }

  /**
    * Parses the training job arguments.
    * @param args the command line arguments.
    * @return the configuration.
    */
  def parse(args: Array[String]): Option[TrainConfig] = {
    val builder = OParser.builder[TrainConfig]
    val parser = {
      import builder._
      OParser.sequence(
        programName("smile train"),
        head("Smile", "2.x"),
        opt[String]("formula")
          .optional()
          .valueName("<class ~ .>")
          .action((x, c) => c.copy(formula = Some(x)))
          .text("The model formula"),
        opt[String]("data")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(train = x))
          .text("The training data file"),
        opt[String]("test")
          .optional()
          .valueName("<file>")
          .action((x, c) => c.copy(test = Some(x)))
          .text("The optional test data file"),
        opt[String]("model")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(model = x))
          .text("The model file to save"),
        opt[String]("format")
          .optional()
          .valueName("<csv,header=true,delimiter=\\t,comment=#,escape=\\,quote=\">")
          .action((x, c) => c.copy(format = x))
          .text("The data file format"),
        opt[Int]("kfold")
          .optional()
          .action((x, c) => c.copy(kfold = x))
          .text("The k-fold cross validation"),
        opt[Int]("round")
          .optional()
          .action((x, c) => c.copy(round = x))
          .text("The number of rounds of repeated cross validation"),
        opt[Unit]("ensemble")
          .optional()
          .action((_, c) => c.copy(ensemble = true))
          .text("Ensemble cross validation models"),
        opt[Long]("seed")
          .optional()
          .action((x, c) => c.copy(seed = Some(x)))
          .text("The random number generator seed"),
        cmd("random_forest")
          .action((_, c) => c.copy(algorithm = "random_forest"))
          .text("Random Forest")
          .children(
            opt[Unit]("regression")
              .optional()
              .action((_, c) => c.copy(classification = false))
              .text("To train a regression model"),
            opt[Int]("trees")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.random_forest.trees", x.toString); c})
              .text("The number of trees"),
            opt[Int]("mtry")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.random_forest.mtry", x.toString); c})
              .text("The number of features to train node split"),
            opt[String]("split")
              .optional()
              .valueName("<GINI, ENTROPY, CLASSIFICATION_ERROR>")
              .action((x, c) => {c.params.setProperty("smile.random_forest.split_rule", x); c})
              .text("The split rule"),
            opt[Int]("max_depth")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.random_forest.max_depth", x.toString); c})
              .text("The maximum tree depth"),
            opt[Int]("max_nodes")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.random_forest.max_nodes", x.toString); c})
              .text("The maximum number of leaf nodes"),
            opt[Int]("node_size")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.random_forest.node_size", x.toString); c})
              .text("The minimum leaf node size"),
            opt[Double]("sampling")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.random_forest.sampling_rate", x.toString); c})
              .text("The sampling rate"),
            opt[String]("class_weight")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.random_forest.class_weight", x); c})
              .text("The class weights"),
          ),
        cmd("gradient_boost")
          .action((_, c) => c.copy(algorithm = "gradient_boost"))
          .text("Gradient Boosting")
          .children(
            opt[Unit]("regression")
              .optional()
              .action((_, c) => c.copy(classification = false))
              .text("To train a regression model"),
            opt[Int]("trees")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gradient_boost.trees", x.toString); c})
              .text("The number of trees"),
            opt[Double]("shrinkage")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gradient_boost.shrinkage", x.toString); c})
              .text("The shrinkage parameter in (0, 1] controls the learning rate"),
            opt[Int]("max_depth")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gradient_boost.max_depth", x.toString); c})
              .text("The maximum tree depth"),
            opt[Int]("max_nodes")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gradient_boost.max_nodes", x.toString); c})
              .text("The maximum number of leaf nodes"),
            opt[Int]("node_size")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gradient_boost.node_size", x.toString); c})
              .text("The minimum leaf node size"),
            opt[Double]("sampling")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gradient_boost.sampling_rate", x.toString); c})
              .text("The sampling rate"),
          ),
        cmd("adaboost")
          .action((_, c) => c.copy(algorithm = "adaboost"))
          .text("AdaBoost")
          .children(
            opt[Int]("trees")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.adaboost.trees", x.toString); c})
              .text("The number of trees"),
            opt[Int]("max_depth")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.adaboost.max_depth", x.toString); c})
              .text("The maximum tree depth"),
            opt[Int]("max_nodes")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.adaboost.max_nodes", x.toString); c})
              .text("The maximum number of leaf nodes"),
            opt[Int]("node_size")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.adaboost.node_size", x.toString); c})
              .text("The minimum leaf node size"),
          ),
        cmd("cart")
          .action((_, c) => c.copy(algorithm = "cart"))
          .text("Classification and Regression Tree")
          .children(
            opt[Unit]("regression")
              .optional()
              .action((_, c) => c.copy(classification = false))
              .text("To train a regression model"),
            opt[String]("split")
              .optional()
              .valueName("<GINI, ENTROPY, CLASSIFICATION_ERROR>")
              .action((x, c) => {c.params.setProperty("smile.cart.split_rule", x); c})
              .text("The split rule"),
            opt[Int]("max_depth")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.cart.max_depth", x.toString); c})
              .text("The maximum tree depth"),
            opt[Int]("max_nodes")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.cart.max_nodes", x.toString); c})
              .text("The maximum number of leaf nodes"),
            opt[Int]("node_size")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.cart.node_size", x.toString); c})
              .text("The minimum leaf node size"),
          ),
        cmd("logistic")
          .action((_, c) => c.copy(algorithm = "logistic"))
          .text("Logistic Regression")
          .children(
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[Double]("lambda")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.logistic.lambda", x.toString); c})
              .text("The regularization on linear weights"),
            opt[Int]("iterations")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.logistic.iterations", x.toString); c})
              .text("The maximum number of iterations"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.logistic.tolerance", x.toString); c})
              .text("The tolerance to stop iterations"),
          ),
        cmd("fisher")
          .action((_, c) => c.copy(algorithm = "fisher"))
          .text("Fisher's Linear Discriminant")
          .children(
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[Int]("dimension")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.fisher.dimension", x.toString); c})
              .text("The dimensionality of mapped space"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.fisher.tolerance", x.toString); c})
              .text("The tolerance if a covariance matrix is singular"),
          ),
        cmd("lda")
          .action((_, c) => c.copy(algorithm = "lda"))
          .text("Linear Discriminant Analysis")
          .children(
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[String]("priori")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.lda.priori", x); c})
              .text("The priori probability of each class"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.lda.tolerance", x.toString); c})
              .text("The tolerance if a covariance matrix is singular"),
          ),
        cmd("qda")
          .action((_, c) => c.copy(algorithm = "qda"))
          .text("Quadratic Discriminant Analysis")
          .children(
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[String]("priori")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.qda.priori", x); c})
              .text("The priori probability of each class"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.qda.tolerance", x.toString); c})
              .text("The tolerance if a covariance matrix is singular"),
          ),
        cmd("rda")
          .action((_, c) => c.copy(algorithm = "rda"))
          .text("Regularized Discriminant Analysis")
          .children(
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[Double]("alpha")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.rda.alpha", x.toString); c})
              .text("The regularization factor in [0, 1] allows a continuum of models between LDA and QDA"),
            opt[String]("priori")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.rda.priori", x); c})
              .text("The priori probability of each class"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.rda.tolerance", x.toString); c})
              .text("The tolerance if a covariance matrix is singular"),
          ),
        cmd("mlp")
          .action((_, c) => c.copy(algorithm = "mlp"))
          .text("Multilayer Perceptron")
          .children(
            opt[Unit]("regression")
              .optional()
              .action((_, c) => c.copy(classification = false))
              .text("To train a regression model"),
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[String]("layers")
              .optional()
              .valueName("<ReLU(100)|Sigmoid(30)>")
              .action((x, c) => {c.params.setProperty("smile.mlp.layers", x); c})
              .text("The neural network layers"),
            opt[Int]("epochs")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.epochs", x.toString); c})
              .text("The number of training epochs"),
            opt[Int]("mini_batch")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.mini_batch", x.toString); c})
              .text("The split rule"),
            opt[String]("learning_rate")
              .optional()
              .valueName("<0.01, linear(0.01, 10000, 0.001), piecewise(...), polynomial(...), inverse(...), exp(...)>")
              .action((x, c) => {c.params.setProperty("smile.mlp.learning_rate", x); c})
              .text("The learning rate schedule"),
            opt[String]("momentum")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.momentum", x); c})
              .text("The momentum schedule"),
            opt[Double]("weight_decay")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.weight_decay", x.toString); c})
              .text("The weight decay"),
            opt[Double]("clip_norm")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.clip_norm", x.toString); c})
              .text("The gradient clipping norm"),
            opt[Double]("clip_value")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.clip_value", x.toString); c})
              .text("The gradient clipping value"),
            opt[Double]("rho")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.RMSProp.rho", x.toString); c})
              .text("RMSProp rho"),
            opt[Double]("epsilon")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.mlp.RMSProp.epsilon", x.toString); c})
              .text("RMSProp epsilon"),
          ),
        cmd("svm")
          .action((_, c) => c.copy(algorithm = "svm"))
          .text("Support Vector Machine")
          .children(
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[String]("kernel")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.svm.kernel", x); c})
              .text("The kernel function"),
            opt[Double]("C")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.svm.C", x.toString); c})
              .text("The soft margin penalty parameter"),
            opt[Double]("epsilon")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.svm.epsilon", x.toString); c.copy(classification = false)})
              .text("The parameter of epsilon-insensitive hinge loss"),
            opt[Unit]("ovr")
              .optional()
              .action((_, c) => {c.params.setProperty("smile.svm.strategy", "ovr"); c})
              .text("One vs Rest strategy for multiclass classification"),
            opt[Unit]("ovo")
              .optional()
              .action((_, c) => {c.params.setProperty("smile.svm.strategy", "ovo"); c})
              .text("One vs One strategy for multiclass classification"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.svm.tolerance", x.toString); c})
              .text("The tolerance of convergence test"),
          ),
        cmd("rbf")
          .action((_, c) => c.copy(algorithm = "rbf"))
          .text("Radial Basis Function Network")
          .children(
            opt[Unit]("regression")
              .optional()
              .action((_, c) => c.copy(classification = false))
              .text("To train a regression model"),
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[Int]("neurons")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.rbf.neurons", x.toString); c})
              .text("The number of neurons (radial basis functions)"),
            opt[Unit]("normalize")
              .optional()
              .action((_, c) => {c.params.setProperty("smile.rbf.normalize", "true"); c})
              .text("Normalized RBF network"),
          ),
        cmd("ols")
          .action((_, c) => c.copy(algorithm = "ols"))
          .text("Ordinary Least Squares")
          .children(
            opt[String]("method")
              .optional()
              .valueName("<qr, svd>")
              .action((x, c) => {c.params.setProperty("smile.ols.method", x); c})
              .text("The fitting method"),
            opt[Unit]("stderr")
              .optional()
              .action((_, c) => {c.params.setProperty("smile.ols.standard_error", "true"); c})
              .text("Compute the standard errors of the estimate of parameters."),
            opt[Unit]("recursive")
              .optional()
              .action((_, c) => {c.params.setProperty("smile.rbf.recursive", "true"); c})
              .text("Recursive least squares"),
          ),
        cmd("lasso")
          .action((_, c) => c.copy(algorithm = "lasso"))
          .text("LASSO - Least Absolute Shrinkage and Selection Operator")
          .children(
            opt[Double]("lambda")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.lasso.lambda", x.toString); c})
              .text("The regularization on linear weights"),
            opt[Int]("iterations")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.lasso.iterations", x.toString); c})
              .text("The maximum number of iterations"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.lasso.tolerance", x.toString); c})
              .text("The tolerance to stop iterations (relative target duality gap)"),
          ),
        cmd("ridge")
          .action((_, c) => c.copy(algorithm = "ridge"))
          .text("Ridge Regression")
          .children(
            opt[Double]("lambda")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.ridge.lambda", x.toString); c})
              .text("The regularization on linear weights"),
          ),
        cmd("elastic_net")
          .action((_, c) => c.copy(algorithm = "elastic_net"))
          .text("Elastic Net")
          .children(
            opt[Double]("lambda1")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.elastic_net.lambda1", x.toString); c})
              .text("The L1 regularization on linear weights"),
            opt[Double]("lambda2")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.elastic_net.lambda2", x.toString); c})
              .text("The L2 regularization on linear weights"),
            opt[Int]("iterations")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.elastic_net.iterations", x.toString); c})
              .text("The maximum number of iterations"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.elastic_net.tolerance", x.toString); c})
              .text("The tolerance to stop iterations (relative target duality gap)"),
          ),
        cmd("gaussian_process")
          .action((_, c) => c.copy(algorithm = "gaussian_process"))
          .text("Gaussian Process Regression")
          .children(
            opt[String]("transform")
              .optional()
              .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
              .action((x, c) => {c.params.setProperty("smile.feature.transform", x); c})
              .text("The feature transformation"),
            opt[String]("kernel")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gaussian_process.kernel", x); c})
              .text("The kernel function"),
            opt[Double]("noise")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gaussian_process.noise", x.toString); c})
              .text("The noise variance"),
            opt[Unit]("normalize")
              .optional()
              .action((_, c) => {c.params.setProperty("smile.gaussian_process.normalize", "true"); c})
              .text("Normalize the response variable"),
            opt[Int]("iterations")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gaussian_process.iterations", x.toString); c})
              .text("The maximum number of HPO iterations"),
            opt[Double]("tolerance")
              .optional()
              .action((x, c) => {c.params.setProperty("smile.gaussian_process.tolerance", x.toString); c})
              .text("The stopping tolerance for HPO"),
          ),
      )
    }

    OParser.parse(parser, args, TrainConfig())
    // If arguments be bad, the error message would have been displayed.
  }
}
