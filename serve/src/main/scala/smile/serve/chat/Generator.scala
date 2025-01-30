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
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import smile.llm.{Message, Role}
import smile.llm.llama._
import smile.serve.ServeConfig

/** GenAI actor.
  *
  * @author Karl Li
  */
object Generator {
  // actor protocol
  sealed trait Command
  final case class Chat(request: CompletionRequest, replyTo: ActorRef[StatusReply[CompletionResponse]]) extends Command
  final case class ChatStream(request: CompletionRequest, publisher: SubmissionPublisher[String]) extends Command

  def apply(config: ServeConfig, dao: ChatDB): Behavior[Command] = {
    val model = Llama.build(config.model, config.tokenizer,
      config.maxBatchSize, config.maxSeqLen, config.device)

    Behaviors.setup { context =>
      implicit val ec = context.executionContext
      val log = context.log

      Behaviors.receiveMessage {
        case Chat(request, replyTo) =>
          try {
            if (request.model != model.family()) {
              throw new IllegalArgumentException(s"Unsupported model: ${request.model}")
            }

            val result = dao.insertMessages(request)
            Await.ready(result, 10.seconds).onComplete {
              case Success((threadId, context)) =>
                val messages = context.reverse.map(msg => new Message(Role.valueOf(msg.role), msg.content)) ++ request.messages
                val seed: java.lang.Long = if (request.seed.isDefined) request.seed.get else null
                val completions = model.chat(Array(messages.toArray),
                  request.max_tokens.getOrElse(config.maxSeqLen / 4), request.temperature.getOrElse(0.6),
                  request.top_p.getOrElse(0.9), request.logprobs.getOrElse(false), seed, null)
                val response = CompletionResponse(threadId, completions(0))
                log.info("Reply {}", response)
                dao.insertMessages(threadId, response)
                replyTo ! StatusReply.Success(response)
              case Failure(ex) =>
                replyTo ! StatusReply.Error(ex)
            }
          } catch {
            case e: Throwable => replyTo ! StatusReply.Error(e)
          }
          Behaviors.same

        case ChatStream(request, publisher) =>
          if (request.model != model.family()) {
            publisher.submit(s"Unsupported model: ${request.model}")
            publisher.close()
          } else {
            val result = dao.insertMessages(request)
            Await.ready(result, 10.seconds).onComplete {
              case Success((threadId, context)) =>
                try {
                  val messages = context.reverse.map(msg => new Message(Role.valueOf(msg.role), msg.content)) ++ request.messages
                  val seed: java.lang.Long = if (request.seed.isDefined) request.seed.get else null
                  val completions = model.chat(Array(messages.toArray),
                    request.max_tokens.getOrElse(config.maxSeqLen / 4), request.temperature.getOrElse(0.6),
                    request.top_p.getOrElse(0.9), request.logprobs.getOrElse(false), seed, publisher)
                  val response = CompletionResponse(threadId, completions(0))
                  log.info("Reply {}", response)
                  dao.insertMessages(threadId, response)
                } catch {
                  case e: Throwable => log.error("ChatStream: ", e)
                } finally {
                  publisher.close()
                }
              case Failure(ex) =>
                publisher.close()
                log.error("ChatStream: ", ex)
            }
          }
          Behaviors.same
      }
    }
  }
}