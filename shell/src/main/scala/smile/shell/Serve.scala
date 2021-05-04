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

import scala.io.StdIn
import scopt.OParser
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.common._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl._
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsObject, JsValue, RootJsonFormat, deserializationError}
import smile.data.Tuple
import smile.data.`type`.StructType
import smile.model.DataFrameModel

class SmileTupleJsonProtocol(schema: StructType) extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object SmileTupleFormat extends RootJsonFormat[Tuple] {
    def write(row: Tuple): JsObject = {
      JsObject()
    }

    def read(value: JsValue): Tuple = value match {
      case JsObject(fields) =>
        val row = new Array[AnyRef](schema.length)
        for (i <- 0 until row.length) {
          row(i) = fields.get(schema.field(i).name)
        }
        Tuple.of(row, schema)
      case _ => deserializationError("JSON Object expected")
    }
  }
}

/**
  * Serve command options.
  * @param model the model file path.
  * @param port HTTP server port number.
  */
case class ServeConfig(model: String = "",
                       port: Int = -1)

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
        opt[Int]("port")
          .optional()
          .action((x, c) => c.copy(port = x))
          .text("HTTP port number"),
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
    if (!modelObj.isInstanceOf[DataFrameModel]) {
      Console.err.println(s"{config.model} doesn't contain a valid model.")
      return
    }

    val model = modelObj.asInstanceOf[DataFrameModel]

    implicit val system = ActorSystem(Behaviors.empty, "smile")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

    val route =
      path("smile") {
        extractRequestEntity { request =>
          request.contentType.mediaType match {
            case MediaTypes.`application/json` =>
              import SprayJsonSupport._
              implicit val jsonStreamingSupport = EntityStreamingSupport.json()
              entity(asSourceOf[JsValue]) { json =>
                complete(json)
              }
            case MediaTypes.`text/csv` | MediaTypes.`text/plain` =>
              implicit val marshaller = akka.http.scaladsl.marshalling.Marshaller.stringMarshaller(MediaTypes.`text/csv`)
              implicit val csvStreamingSupport = EntityStreamingSupport.csv()
              val lines = request.dataBytes.via(CsvParsing.lineScanner())
                .map(_.map(_.utf8String))
                .map(_.mkString(","))
              complete(lines)
            case _ =>
              complete(s"Unsupported Content-Type: ${request.contentType}")
          }
        }
      }

    val conf = ConfigFactory.load()
    val port = if (config.port >= 0) config.port else conf.getInt("smile.http.server.default-http-port")
    val bindingFuture = Http().newServerAt("localhost", port).bind(route)

    println(s"Smile online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
