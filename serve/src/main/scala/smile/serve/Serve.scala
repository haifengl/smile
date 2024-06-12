/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve

import scala.util.{Failure, Success}
import scopt.OParser
import akka.actor.typed.{ActorSystem, Terminated}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

/**
  * Serve command options.
  * @param model the model checkpoint directory path.
  * @param tokenizer the tokenizer model file path.
  * @param maxSeqLen the maximum sequence length.
  * @param maxBatchSize the maximum batch size.
  * @param device the CUDA device ID. Note that CUDA won’t concurrently run
  *               kernels on multiple devices from a single process.
  */
case class ServeConfig(model: String,
                       tokenizer: String,
                       maxSeqLen: Int = 2048,
                       maxBatchSize: Int = 4,
                       device: Int = 0)

/** LLM Serving.
  *
  * @author Karl Li
  */
object Serve extends JsonSupport {
  val conf = ConfigFactory.load()

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
        programName("smile-serve"),
        head("SmileServe", "- Large Language Model (LLM) Inference Server"),
        opt[String]("model")
          .required()
          .action((x, c) => c.copy(model = x))
          .text("The model checkpoint directory path"),
        opt[String]("tokenizer")
          .required()
          .action((x, c) => c.copy(tokenizer = x))
          .text("The tokenizer model file path"),
        opt[Int]("max-seq-len")
          .optional()
          .action((x, c) => c.copy(maxSeqLen = x))
          .text("The maximum sequence length"),
        opt[Int]("max-batch-size")
          .optional()
          .action((x, c) => c.copy(maxBatchSize = x))
          .text("The maximum batch size"),
        opt[Int]("device")
          .optional()
          .action((x, c) => c.copy(device = x))
          .text("The CUDA device ID")
      )
    }

    OParser.parse(parser, args, ServeConfig("Llama3-8B-Instruct", "Llama3-8B-Instruct/tokenizer.model"))
    // If arguments be bad, the error message would have been displayed.
  }

  /**
    * Online prediction.
    * @param config the serve configuration.
    */
  def serve(config: ServeConfig): ActorSystem[Nothing] = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val system = context.system
      // context cannot be used inside Future.onComplete, which is outside of an actor.
      val log = context.log
      implicit val exceptionHandler = ExceptionHandler {
        case ex: IllegalArgumentException =>
          log.error("HTTP exception handler", ex)
          complete(HttpResponse(StatusCodes.BadRequest, entity = exceptionMessage(ex)))
        case ex: SecurityException =>
          log.error("HTTP exception handler", ex)
          complete(HttpResponse(StatusCodes.Unauthorized))
      }

      // asking someone requires a timeout if the timeout hits without response
      // the ask is failed with a TimeoutException
      implicit val timeout = Timeout.create(conf.getDuration("smile.serve.timeout"))
      val generator = context.spawn(Generator(config), "Generator")
      context.watch(generator)

      val routes = Route.seal {
        // Route.seal internally wraps its argument route with the handleExceptions
        // directive in order to catch and handle any exception.
        path("v1" / "chat" / "completions") {
          post {
            entity(as[CompletionRequest]) { request =>
              log.info("Receive {}", request)
              val result = generator.askWithStatus(ref => Generator.Chat(request, ref))
              complete(result)
            }
          }
        }
      }

      startHttpServer(routes)(context.system)
      Behaviors.receiveSignal[Nothing] {
        case (context, Terminated(ref)) =>
          context.log.info("Actor {} stopped. Akka system is terminating...", ref.path.name)
          system.terminate()
          Behaviors.stopped
      }
    }
    ActorSystem[Nothing](rootBehavior, "SmileServe")
  }

  /**
    * Returns exception message. In case getMessage returns null
    * (e.g. NullPointerException), the class name is returned.
    */
  private def exceptionMessage(ex: Throwable): String = {
    Option(ex.getMessage).getOrElse(s"${ex.getClass.getName}(null)")
  }

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext

    val interface = conf.getString("akka.http.server.interface")
    val port = conf.getInt("akka.http.server.port")
    val futureBinding = Http().newServerAt(interface, port).bind(routes)
    futureBinding.onComplete {
      case Success(_) =>
        system.log.info("SmileServe service online at http://{}:{}/", interface, port)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
