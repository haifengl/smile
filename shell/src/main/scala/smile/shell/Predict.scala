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

import scopt.OParser
import smile.classification._
import smile.data.{CategoricalEncoder, DataFrame, Tuple}
import smile.data.formula._
import smile.io.Read
import smile.regression.{DataFrameRegression, LinearModel}
import smile.util.Strings

/**
  * Predict command options.
  * @param model the model file path.
  * @param data the data file path.
  * @param format the input data format.
  * @param probability the flag if output posteriori probabilities for soft classifiers.
  */
case class PredictConfig(model: String = "",
                         data: String = "",
                         format: String = "",
                         probability: Boolean = false)

/**
  * Batch prediction on a file.
  */
object Predict {
  /**
    * Runs a batch prediction job.
    * @param args the command line arguments.
    */
  def apply(args: Array[String]): Unit = {
    parse(args) match {
      case Some(config) => predict(config)
      case _ => ()
    }
  }

  /**
    * Parses the prediction job arguments.
    * @param args the command line arguments.
    * @return the configuration.
    */
  def parse(args: Array[String]): Option[PredictConfig] = {
    val builder = OParser.builder[PredictConfig]
    val parser = {
      import builder._
      OParser.sequence(
        programName("smile predict"),
        head("Smile", "2.x"),
        opt[String]("model")
          .required()
          .action((x, c) => c.copy(model = x))
          .text("The model file"),
        opt[String]("data")
          .required()
          .action((x, c) => c.copy(data = x))
          .text("The data file"),
        opt[String]("format")
          .optional()
          .action((x, c) => c.copy(format = x))
          .text("The data file format/schema"),
        opt[Unit]("probability")
          .optional()
          .action((_, c) => c.copy(probability = true))
          .text("Output the posteriori probabilities for soft classifier"),
      )
    }

    OParser.parse(parser, args, PredictConfig())
    // If arguments be bad, the error message would have been displayed.
  }

  /**
    * Batch prediction.
    * @param config the prediction configuration.
    */
  def predict(config: PredictConfig): Unit = {
    val data = Read.data(config.data, config.format)
    val modelObj = smile.read(config.model)
    if (!modelObj.isInstanceOf[Model]) {
      Console.err.println(s"{config.model} doesn't contain a valid model.")
      return
    }

    val model = modelObj.asInstanceOf[Model]
    model.algorithm match {
      case "random.forest" =>
        if (model.numClasses > 1)
          predictSoftClassifier(data, model.model.asInstanceOf[smile.classification.RandomForest], model.numClasses, config.probability)
        else
          predictRegression(data, model.model.asInstanceOf[smile.regression.RandomForest])
      case "gbt" =>
        if (model.numClasses > 1)
          predictSoftClassifier(data, model.model.asInstanceOf[smile.classification.GradientTreeBoost], model.numClasses, config.probability)
        else
          predictRegression(data, model.model.asInstanceOf[smile.regression.GradientTreeBoost])
      case "adaboost" =>
        predictSoftClassifier(data, model.model.asInstanceOf[AdaBoost], model.numClasses, config.probability)
      case "logit" =>
        predictSoftClassifier(data, model.formula, model.model.asInstanceOf[LogisticRegression],
          model.numClasses, config.probability, false, CategoricalEncoder.DUMMY)
      case "fld" =>
        predictClassifier(data, model.formula, model.model.asInstanceOf[FLD], false, CategoricalEncoder.DUMMY)
      case "lda" =>
        predictSoftClassifier(data, model.formula, model.model.asInstanceOf[LDA],
          model.numClasses, config.probability, false, CategoricalEncoder.DUMMY)
      case "qda" =>
        predictSoftClassifier(data, model.formula, model.model.asInstanceOf[QDA],
          model.numClasses, config.probability, false, CategoricalEncoder.DUMMY)
      case "rda" =>
        predictSoftClassifier(data, model.formula, model.model.asInstanceOf[RDA],
          model.numClasses, config.probability, false, CategoricalEncoder.DUMMY)
      case "ols" | "lasso" | "elastic.net" | "ridge" =>
        predictRegression(data, model.model.asInstanceOf[LinearModel])
      case algo =>
        println("Unsupported algorithm: " + algo)
    }
  }

  /**
    * Predicts with a soft classifier.
    * @param model the model.
    * @param data the data.
    */
  def predictSoftClassifier(data: DataFrame, model: SoftClassifier[Tuple], numClasses: Int, probability: Boolean): Unit = {
    if (probability) {
      val posteriori = Array.ofDim[Double](numClasses)
      (0 until data.size).foreach { i =>
        val y = model.predict(data(i), posteriori)
        println(s"$y ${Strings.toString(posteriori)}")
      }
    } else {
      (0 until data.size).foreach { i =>
        println(model.predict(data(i)))
      }
    }
  }

  /**
    * Predicts with a soft classifier taking vector input.
    * @param data the data.
    * @param model the model.
    * @param formula the model formula.
    * @param numClasses the number of classes.
    * @param bias the flag to generate the bias term.
    * @param encoder the categorical variable encoder.
    */
  def predictSoftClassifier(data: DataFrame, formula: Formula, model: SoftClassifier[Array[Double]],
                            numClasses: Int, probability: Boolean, bias: Boolean, encoder: CategoricalEncoder): Unit = {
    if (probability) {
      val posteriori = Array.ofDim[Double](numClasses)
      formula.x(data).toArray(bias, encoder).foreach { x =>
        val y = model.predict(x, posteriori)
        println(s"$y ${Strings.toString(posteriori)}")
      }
    } else {
      formula.x(data).toArray(bias, encoder).foreach { x =>
        println(model.predict(x))
      }
    }
  }

  /**
    * Predicts with a classifier taking vector input.
    * @param data the data.
    * @param model the model.
    * @param formula the model formula.
    * @param bias the flag to generate the bias term.
    * @param encoder the categorical variable encoder.
    */
  def predictClassifier(data: DataFrame, formula: Formula, model: Classifier[Array[Double]],
                        bias: Boolean, encoder: CategoricalEncoder): Unit = {
    formula.x(data).toArray(bias, encoder).foreach { x =>
      println(model.predict(x))
    }
  }

  /**
    * Predicts with a regression model.
    * @param model the model.
    * @param data the data.
    */
  def predictRegression(data: DataFrame, model: DataFrameRegression): Unit = {
    model.predict(data).foreach(y => println(Strings.format(y)))
  }
}
