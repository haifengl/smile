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
  * Serve command options.
  * @param model the model file path.
  */
case class ServeConfig(model: String = "")

/**
  * Online prediction.
  */
object Serve {
  /**
    * Runs an online prediction HTTP server.
    * @param args the command line arguments.
    */
  def apply(args: Array[String]): Unit = {
    parse(args) match {
      case Some(config) => serve(config)
      case _ => ()
    }
  }

  /**
    * Parses the serve job arguments.
    * @param args the command line arguments.
    */
  def parse(args: Array[String]): Option[ServeConfig] = {
    val builder = OParser.builder[ServeConfig]
    val parser = {
      import builder._
      OParser.sequence(
        programName("smile serve"),
        head("Smile", "2.x"),
        opt[String]("model")
          .required()
          .action((x, c) => c.copy(model = x))
          .text("The model file"),
      )
    }

    OParser.parse(parser, args, ServeConfig())
    // If arguments be bad, the error message would have been displayed.
  }

  /**
    * Online prediction.
    * @param config the serve configuration.
    */
  def serve(config: ServeConfig): Unit = {
    val modelObj = smile.read(config.model)
    if (!modelObj.isInstanceOf[Model]) {
      Console.err.println(s"{config.model} doesn't contain a valid model.")
      return
    }

    val model = modelObj.asInstanceOf[Model]
  }
}
