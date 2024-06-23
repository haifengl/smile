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
package smile.serve.chat

import java.util.concurrent.SubmissionPublisher
import scala.concurrent.ExecutionContext
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.JavaFlowSupport
import akka.util.Timeout

class ChatRoutes(generator: ActorRef[Generator.Command])(implicit val system: ActorSystem[_], implicit val timeout: Timeout) extends JsonSupport {
  private implicit val ec: ExecutionContext = system.executionContext
  private val log = system.log

  val routes: Route = pathPrefix("v1" / "chat") {
    concat(
      path("completions") {
        post {
          import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
          entity(as[CompletionRequest]) { request =>
            log.info("Receive {}", request)
            if (request.stream.getOrElse(false)) {
              val publisher = new SubmissionPublisher[String]()
              val source = JavaFlowSupport.Source.fromPublisher(publisher)
                .map(message => ServerSentEvent(message))

              generator ! Generator.ChatStream(request, publisher)
              complete(source)
            } else {
              val result = generator.askWithStatus(ref => Generator.Chat(request, ref))
              complete(result)
            }
          }
        }
      }
    )
  }
}