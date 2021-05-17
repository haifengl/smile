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
import smile.data.DataFrame
import smile.data.formula._
import smile.io.Read
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
  * @param params the hyperparameter key-value pairs.
  */
case class TrainConfig(algorithm: String = "",
                       formula: String = "",
                       train: String = "",
                       test: Option[String] = None,
                       format: String = "",
                       model: String = "",
                       transform: String = null,
                       classification: Boolean = true,
                       kfold: Int = 1,
                       round: Int = 1,
                       ensemble: Boolean = false,
                       params: Map[String, String] = Map())

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

        val formula = getFormula(config, data)
        val props = getHyperparameters(config.algorithm, config.params)

        if (config.classification) {
          val model = ClassificationModel(config.algorithm, config.transform, formula, data, props, config.kfold, config.round, config.ensemble, test)
          println(s"Training metrics: ${model.train}")
          model.validation.map(metrics => println(s"Validation metrics: ${metrics}"))
          model.test.map(metrics => println(s"Test metrics: ${metrics}"))
          smile.write(model, config.model)
        } else {
          val model = RegressionModel(config.algorithm, config.transform, formula, data, props, config.kfold, config.round, config.ensemble, test)
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
        opt[String]("algo")
          .required()
          .valueName("<random.forest, gradient.boost, ada.boost, cart, logit, mlp, svm, rbf, gaussian.process, fld, lda, qda, rda, ols, lasso, elastic.net, ridge>")
          .action((x, c) => c.copy(algorithm = x))
          .text("The algorithm to train the model"),
        opt[String]("formula")
          .optional()
          .valueName("<class ~ .>")
          .action((x, c) => c.copy(formula = x))
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
        opt[String]("transform")
          .optional()
          .valueName("<standardizer, winsor(0.01,0.99), minmax, MaxAbs, L1, L2, Linf>")
          .action((x, c) => c.copy(transform = x))
          .text("The optional feature transformation"),
        opt[String]("format")
          .optional()
          .valueName("<csv,header=true,delimiter=\\t,comment=#,escape=\\,quote=\">")
          .action((x, c) => c.copy(format = x))
          .text("The data file format/schema"),
        opt[String]("model")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(model = x))
          .text("The model file to save"),
        opt[Unit]("regression")
          .optional()
          .action((_, c) => c.copy(classification = false))
          .text("To train a regression model"),
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
          .text("Ensemble of cross validation models."),
        opt[Map[String, String]]("params")
          .valueName("k1=v1,k2=v2...")
          .action((x, c) => c.copy(params = x))
          .text("The hyper-parameters"),
      )
    }

    OParser.parse(parser, args, TrainConfig())
    // If arguments be bad, the error message would have been displayed.
  }

  /**
    * Returns the model formula. If the config doesn't specify the formula,
    * uses 'class' or 'y' or the first column as the target and the rest as
    * the predictors.
    * @param config the training configuration.
    * @param data the training data.
    * @return the model formula.
    */
  def getFormula(config: TrainConfig, data: DataFrame): Formula = {
    if (config.formula.isEmpty) {
      val columns = data.names()
      val target =
        if (columns.contains("class")) "class"
        else if (columns.contains("y")) "y"
        else columns(0)
      Formula.lhs(target)
    } else {
      Formula.of(config.formula)
    }
  }

  /**
    * Returns the the hyper-parameter settings.
    * @param algo the algorithm name.
    * @param params the parameter key-value pairs.
    * @return the hyperparameter settings.
    */
  def getHyperparameters(algo: String, params: Map[String, String]): Properties = {
    val prefix = s"smile.${algo}."
    val props = new Properties()
    for ((k, v) <- params) {
      props.setProperty(prefix + k, v)
    }
    props
  }
}
