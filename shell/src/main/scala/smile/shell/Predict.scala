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
package smile.shell

import scopt.OParser
import smile.io.Read
import smile.model.*
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
      import builder.*
      OParser.sequence(
        programName("smile predict"),
        head("Smile", BuildInfo.version),
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
    smile.read(config.model) match {
      case model: ClassificationModel =>
        if (config.probability && model.classifier.soft()) {
          val posteriori = new java.util.ArrayList[Array[Double]]()
          val y = model.classifier.predict(data, posteriori)
          (0 until y.length).foreach { i =>
            print(y(i))
            posteriori.get(i).foreach { prob => print(" %.4f" format prob) }
            println()
          }
        } else {
          model.classifier.predict(data).foreach(y => println(y))
        }

      case model: RegressionModel =>
        model.regression.predict(data).foreach(y => println(Strings.format(y)))

      case _ =>
        Console.err.println(s"{config.model} doesn't contain a valid model.")
    }
  }
}
