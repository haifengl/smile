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

import scala.io.StdIn
import scopt.OParser
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.common._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import smile.llm.llama._

/**
  * Serve command options.
  * @param model the model checkpoint directory path.
  * @param tokenizer the tokenizer model file path.
  * @param maxSeqLen the maximum sequence length.
  * @param maxBatchSize the maximum batch size.
  * @param port HTTP server port number.
  */
case class ServeConfig(model: String,
                       tokenizer: String,
                       maxSeqLen: Int = 2048,
                       maxBatchSize: Int = 4,
                       port: Int = -1)

/**
  * Online prediction.
  */
object Serve extends LazyLogging with JsonSupport {
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
        opt[Int]("port")
          .optional()
          .action((x, c) => c.copy(port = x))
          .text("HTTP port number"),
      )
    }

    OParser.parse(parser, args, ServeConfig("Llama3-8B-Instruct", "Llama3-8B-Instruct/tokenizer.model"))
    // If arguments be bad, the error message would have been displayed.
  }

  /**
    * Online prediction.
    * @param config the serve configuration.
    */
  def serve(config: ServeConfig): Unit = {
    val generator = Llama.build(config.model, config.tokenizer, config.maxBatchSize, config.maxSeqLen)

    implicit val system = ActorSystem(Behaviors.empty, "smile")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext
    // Source rendering support trait
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()

    val route =
      path("v1" / "chat" / "completions") {
        post {
          entity(as[CompletionRequest]) { request =>
            if (request.model == generator.family()) {
              val seed: java.lang.Long = if (request.seed.isDefined) request.seed.get else null
              val completions = generator.chat(Array(request.messages),
                request.max_tokens.getOrElse(2048), request.temperature.getOrElse(0.6),
                request.top_p.getOrElse(0.9), request.logprobs.getOrElse(false), seed)
              complete(CompletionResponse(completions(0)))
            } else {
              complete(StatusCodes.BadRequest, s"Unknown model: ${request.model}")
            }
          }
        }
      }

    val conf = ConfigFactory.load()
    val port = if (config.port > 0) config.port else conf.getInt("smile.http.server.default-http-port")
    val bindingFuture = Http().newServerAt("localhost", port).bind(route)

    println(s"Smile serve at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
