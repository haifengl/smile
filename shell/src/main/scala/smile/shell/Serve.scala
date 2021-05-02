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

import scala.util.Random
import scala.io.StdIn
import scopt.OParser
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

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
    /*
    val modelObj = smile.read(config.model)
    if (!modelObj.isInstanceOf[Model]) {
      Console.err.println(s"{config.model} doesn't contain a valid model.")
      return
    }

    val model = modelObj.asInstanceOf[Model]
    */
    val numbers = Source.fromIterator(() =>
      Iterator.continually(Random.nextInt()))

    implicit val system = ActorSystem(Behaviors.empty, "smile")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

    val route =
      path("predict") {
        post {
          complete(
            HttpEntity(
              ContentTypes.`text/plain(UTF-8)`,
              // transform each number to a chunk of bytes
              numbers.map(n => ByteString(s"$n\n"))
            )
          )
        }
      }

    val conf = ConfigFactory.load()
    val port = conf.getInt("akka.http.server.default-http-port")
    val bindingFuture = Http().newServerAt("localhost", port).bind(route)

    println(s"Smile online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
