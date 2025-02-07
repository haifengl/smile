/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve.chat

import java.util.concurrent.SubmissionPublisher
import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern.*
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.JavaFlowSupport
import akka.util.Timeout
import smile.serve.chat.JsonSupport.*

class ChatRoutes(generator: ActorRef[Generator.Command], dao: ChatDB)
                (implicit val system: ActorSystem[?], val timeout: Timeout) {
  private implicit val ec: ExecutionContext = system.executionContext
  private val log = system.log

  val routes: Route = concat(
    path("chat" / "completions") {
      post {
        import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling.*
        entity(as[CompletionRequest]) { request =>
          log.info("Receive {}", request)
          if (request.stream.getOrElse(false)) {
            val publisher = new SubmissionPublisher[String]()
            val source = JavaFlowSupport.Source.fromPublisher(publisher)
              .map(message => ServerSentEvent(" " + message)) // in case client eats the space after 'data:'

            generator ! Generator.ChatStream(request, publisher)
            complete(source)
          } else {
            val completion: Future[CompletionResponse] = generator.askWithStatus(ref => Generator.Chat(request, ref))
            complete(completion)
          }
        }
      }
    },
    pathPrefix("threads") {
      concat(
        pathEnd {
          concat(
            get {
              parameters("limit".as[Int].withDefault(100), "cursor".?) { (limit, cursor) =>
                val threads = dao.getThreads(Math.min(500, limit), cursor.map(_.toLong))
                complete(threads.map(ListThreadResponse(_)))
              }
            },
            post {
              extractClientIP { clientIP =>
                optionalHeaderValueByName("User-Agent") { userAgent =>
                  log.info("Create thread for {} using {}", clientIP, userAgent)
                  onSuccess(dao.insertThread(clientIP.toOption, userAgent)) { thread =>
                    complete(StatusCodes.Created, thread)
                  }
                }
              }
            }
          )
        },
        pathPrefix(LongNumber) { threadId =>
          concat(
            pathEnd {
              complete(dao.getThread(threadId))
            },
            pathPrefix("messages") {
              concat(
                pathEnd {
                  get {
                    parameters("limit".as[Int].withDefault(100), "cursor".?) { (limit, cursor) =>
                      val messages = dao.getMessages(threadId, Math.min(500, limit), cursor.map(_.toLong))
                      complete(messages.map(ListMessageResponse(_)))
                    }
                  }
                },
                path(LongNumber) { id =>
                  complete(dao.getMessage(threadId, id))
                }
              )
            }
          )
        }
      )
    }
  )
}
