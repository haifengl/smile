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

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scopt.OParser
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.common._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import smile.data._
import smile.data.`type`.StructType
import smile.model.{ClassificationModel, RegressionModel}

/**
  * Serve command options.
  * @param model the model file path.
  * @param probability the flag if output posteriori probabilities for soft classifiers.
  */
case class ServeConfig(model: String,
                       probability: Boolean = false,
                       host: String = "localhost",
                       port: Int = 8728)

/**
  * Online prediction.
  */
object Serve extends LazyLogging {
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
        head("Smile", BuildInfo.version),
        opt[String]("model")
          .required()
          .action((x, c) => c.copy(model = x))
          .text("The model file"),
        opt[Unit]("probability")
          .optional()
          .action((_, c) => c.copy(probability = true))
          .text("Output the posteriori probabilities for soft classifier"),
        opt[String]("host")
          .optional()
          .action((x, c) => c.copy(host = x))
          .text("The IP address to listen on (0.0.0.0 for all available addresses)"),
        opt[Int]("port")
          .optional()
          .action((x, c) => c.copy(port = x))
          .text("The port number")
      )
    }

    OParser.parse(parser, args, ServeConfig(""))
    // If arguments be bad, the error message would have been displayed.
  }

  /**
    * Online prediction.
    * @param config the serve configuration.
    */
  def serve(config: ServeConfig): Unit = {
    val model = smile.read(config.model)
    val (schema, predictor) = model match {
      case model: ClassificationModel =>
        val schema = model.schema
        val predict: Option[Tuple] => JsValue =
          tuple => tuple.map { model.predict(_, config.probability) }
            .getOrElse(JsString("Invalid instance"))
        (schema, predict)

      case model: RegressionModel =>
        val schema = model.schema
        val predict: Option[Tuple] => JsValue =
          tuple => tuple.map { model(_) }
            .getOrElse(JsString("Invalid instance"))
        (schema, predict)

      case _ =>
        Console.err.println(s"{config.model} doesn't contain a valid model.")
        return
    }

    implicit val system = ActorSystem(Behaviors.empty, "smile")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext
    // Source rendering support trait
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()

    val route = path("v1" / "infer") {
      post {
        parameters("format".?) { case format =>
          format.getOrElse("json") match {
            case "json" =>
              entity(asSourceOf[JsValue]) { json =>
                complete(processJSON(schema, json)(predictor))
              }
            case csvFormat if csvFormat.startsWith("csv") =>
              extractDataBytes { bytes =>
                complete(processCSV(schema, bytes, csvFormat)(predictor))
              }
            case _ =>
              complete(StatusCodes.UnsupportedMediaType)
          }
        }
      }
    }

    val bindingFuture = Http().newServerAt(config.host, config.port).bind(route)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))
    bindingFuture.onComplete {
      case Success(_) =>
        system.log.info("Smile online at http://{}:{}/v1/infer", config.host, config.port)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def processJSON[T](schema: StructType, stream: Source[JsValue, Any])
                    (processor: Option[Tuple] => T): Source[T, Any]  = {
    stream.map(schema.json(_)).map(processor)
  }

  def getCsvFormatByte(format: String, param: Array[String]): Byte = {
    if (param.length != 2 || param(1).length != 1) {
      throw new IllegalArgumentException(s"Invalid CSV format specification: $format")
    }
    param(1).charAt(0).toByte
  }

  def processCSV[T](schema: StructType, bytes: Source[ByteString, Any], format: String)
                   (processor: Option[Tuple] => T): Source[T, Any] = {
    var delimiter = CsvParsing.Comma
    var quote = CsvParsing.DoubleQuote
    var escape = CsvParsing.Backslash
    var header = false

    format.split(",").foreach { token =>
      val option = token.split("=", 2)
      option(0) match {
        case "delimiter" => delimiter = getCsvFormatByte(format, option)
        case "quote" => quote = getCsvFormatByte(format, option)
        case "escape" => escape = getCsvFormatByte(format, option)
        case "header" => header = if (option.length == 1) true else option(1).toBoolean
        case unknown => if (!unknown.equals("csv"))
          logger.warn(s"Unknown CSV format specification: $token")
      }
    }

    implicit val marshaller = akka.http.scaladsl.marshalling.Marshaller.stringMarshaller(MediaTypes.`text/csv`)
    implicit val csvStreamingSupport = EntityStreamingSupport.csv()
    val lines = bytes.via(CsvParsing.lineScanner(delimiter, quote, escape))

    if (header) {
      lines.via(CsvToMap.toMapAsStrings())
        .map(schema.csv(_))
        .map(processor)
    } else {
      lines.map(_.map(_.utf8String))
        .map(schema.csv(_))
        .map(processor)
    }
  }
}
