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
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
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

  def apply(config: ServeConfig): Behavior[Command] = {
    Behaviors.setup { context =>
      val log = context.log
      val model = Llama.build(config.model, config.tokenizer,
        config.maxBatchSize, config.maxSeqLen, config.device)

      Behaviors.receiveMessage {
        case Chat(request, replyTo) =>
          try {
            if (request.model != model.family()) {
              throw new IllegalArgumentException(s"Unsupported model: ${request.model}")
            }

            val seed: java.lang.Long = if (request.seed.isDefined) request.seed.get else null
            val completions = model.chat(Array(request.messages.toArray),
                request.max_tokens.getOrElse(config.maxSeqLen / 2), request.temperature.getOrElse(0.6),
                request.top_p.getOrElse(0.9), request.logprobs.getOrElse(false), seed, null)
            val response = CompletionResponse(completions(0))
            log.info("Reply {}", response)
            replyTo ! StatusReply.Success(response)
          } catch {
            case e: Throwable => replyTo ! StatusReply.Error(e)
          }
          Behaviors.same

        case ChatStream(request, publisher) =>
          try {
            if (request.model != model.family()) {
              throw new IllegalArgumentException(s"Unsupported model: ${request.model}")
            }

            val seed: java.lang.Long = if (request.seed.isDefined) request.seed.get else null
            val completions = model.chat(Array(request.messages.toArray),
                request.max_tokens.getOrElse(config.maxSeqLen / 2), request.temperature.getOrElse(0.6),
                request.top_p.getOrElse(0.9), request.logprobs.getOrElse(false), seed, publisher)
            val response = CompletionResponse(completions(0))
            log.info("Reply {}", response)
          } catch {
            case e: Throwable => log.error("ChatStream: ", e)
          } finally {
            publisher.close()
          }
          Behaviors.same
      }
    }
  }
}