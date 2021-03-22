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
import scala.jdk.CollectionConverters._
import scopt.OParser
import smile.classification._
import smile.data.{CategoricalEncoder, DataFrame, Tuple}
import smile.data.formula._
import smile.io.Read
import smile.validation._

/**
  * Training command options.
  * @param algorithm the algorithm name.
  * @param formula the model formula.
  * @param train the training data file path.
  * @param test the test data file path.
  * @param format the input data format.
  * @param kfold k-fold cross validation.
  * @param model the model file path.
  * @param params the hyperparameter key-value pairs.
  */
case class TrainConfig(algorithm: String = "",
                       formula: String = "",
                       train: String = "",
                       test: String = "",
                       format: String = "",
                       kfold: Int = 0,
                       model: String = "",
                       params: Map[String, String] = Map())

/**
  * The machine learning model.
  * @param algorithm the algorithm name.
  * @param formula the model formula.
  * @param model the model.
  */
case class Model(algorithm: String, formula: Formula, model: AnyRef)

/**
  * Trains a classification model.
  */
object Train {
  /**
    * Runs a training job.
    * @param args the command line arguments.
    */
  def apply(args: Array[String]): Unit = {
    parse(args) match {
      case Some(config) =>
        val model = train(config)
        if (model != null) smile.write(model, config.model)
      case _ => ()
    }
  }

  /**
    * Parses the training job arguments.
    * @param args the command line arguments.
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
          .action((x, c) => c.copy(algorithm = x))
          .text("The algorithm to train the model"),
        opt[String]("formula")
          .optional()
          .action((x, c) => c.copy(formula = x))
          .text("The model formula"),
        opt[String]("data")
          .required()
          .action((x, c) => c.copy(train = x))
          .text("The training data file"),
        opt[String]("test")
          .optional()
          .action((x, c) => c.copy(test = x))
          .text("The test data file"),
        opt[String]("format")
          .optional()
          .action((x, c) => c.copy(format = x))
          .text("The data file format/schema"),
        opt[Int]("kfold")
          .optional()
          .action((x, c) => c.copy(kfold = x))
          .text("The k-fold cross validation"),
        opt[String]("model")
          .required()
          .action((x, c) => c.copy(model = x))
          .text("The model file to save"),
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
    * Trains the model.
    * @param config the training configuration.
    * @return the model.
    */
  def train(config: TrainConfig): Model = {
    val data = Read.data(config.train, config.format)
    val test = if (config.test.isEmpty) None else Some(Read.data(config.test, config.format))

    val formula = getFormula(config, data)
    val props = getHyperparameters(config.algorithm, config.params)

    config.algorithm match {
      case "random.forest" =>
        trainDataFrameClassifier(formula, data, props, test, config) { (formula, data, props) =>
          RandomForest.fit(formula, data, props)
        }
      case "gbt" =>
        trainDataFrameClassifier(formula, data, props, test, config) { (formula, data, props) =>
          GradientTreeBoost.fit(formula, data, props)
        }
      case "adaboost" =>
        trainDataFrameClassifier(formula, data, props, test, config) { (formula, data, props) =>
          AdaBoost.fit(formula, data, props)
        }
      case "logit" =>
        trainVectorClassifier(formula, data, props, false, CategoricalEncoder.DUMMY, test, config) { (x, y, props) =>
          LogisticRegression.fit(x, y, props)
        }
      case "fld" =>
        trainVectorClassifier(formula, data, props, false, CategoricalEncoder.DUMMY, test, config) { (x, y, props) =>
          FLD.fit(x, y, props)
        }
      case "lda" =>
        trainVectorClassifier(formula, data, props, false, CategoricalEncoder.DUMMY, test, config) { (x, y, props) =>
          LDA.fit(x, y, props)
        }
      case "qda" =>
        trainVectorClassifier(formula, data, props, false, CategoricalEncoder.DUMMY, test, config) { (x, y, props) =>
          QDA.fit(x, y, props)
        }
      case "rda" =>
        trainVectorClassifier(formula, data, props, false, CategoricalEncoder.DUMMY, test, config) { (x, y, props) =>
          RDA.fit(x, y, props)
        }
      case algo =>
        println("Unsupported algorithm: " + algo)
        null
    }
  }

  /**
    * Trains a data frame classifier model.
    * @param formula the model formula.
    * @param data the training data.
    * @param props the hyperparameters.
    * @param test the optional validation data.
    * @param config the training configuration.
    * @return the model.
    */
  def trainDataFrameClassifier(formula: Formula, data: DataFrame, props: Properties,
                               test: Option[DataFrame], config: TrainConfig)
                              (trainer: (Formula, DataFrame, Properties) => DataFrameClassifier): Model = {
    val start = System.nanoTime()
    val model = trainer(formula, data, props)
    val fitTime = (System.nanoTime() - start) / 1E6

    if (config.kfold > 1) {
      val metrics = cv.classification(config.kfold, formula, data) { (formula, data) =>
        trainer(formula, data, props)
      }
      println(s"${config.kfold}-fold cross validation metrics: ${metrics}")
    } else {
      val metrics = ClassificationMetrics.of(fitTime, model, formula, data)
      println(s"Training metrics: ${metrics}")
    }

    if (test.isDefined) {
      val metrics = ClassificationMetrics.of(model, formula, test.get)
      println(s"Validation metrics: ${metrics}")
    }

    Model(config.algorithm, formula, model)
  }

  /**
    * Trains a classifier taking vector input.
    * @param formula the model formula.
    * @param props the hyperparameters.
    * @param data the training data.
    * @param test the optional validation data.
    * @param config the training configuration.
    * @return the model.
    */
  def trainVectorClassifier(formula: Formula, data: DataFrame, props: Properties,
                            bias: Boolean, encoder: CategoricalEncoder,
                            test: Option[DataFrame], config: TrainConfig)
                           (trainer: (Array[Array[Double]], Array[Int], Properties) => Classifier[Array[Double]]): Model = {
    val x = formula.x(data).toArray(bias, encoder)
    val y = formula.y(data).toIntArray()

    val start = System.nanoTime()
    val model = trainer(x, y, props)
    val fitTime = (System.nanoTime() - start) / 1E6

    if (config.kfold > 1) {
      val metrics = cv.classification(config.kfold, x, y) { (x, y) =>
        trainer(x, y, props)
      }
      println(s"${config.kfold}-fold cross validation metrics: ${metrics}")
    } else {
      val metrics = ClassificationMetrics.of(fitTime, model, x, y)
      println(s"Training metrics: ${metrics}")
    }

    if (test.isDefined) {
      val testx = formula.x(test.get).toArray(bias, encoder)
      val testy = formula.y(test.get).toIntArray()
      val metrics = ClassificationMetrics.of(model, testx, testy)
      println(s"Validation metrics: ${metrics}")
    }

    Model(config.algorithm, formula, model)
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
