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
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import smile.llm.llama._

/** GenAI actor.
  *
  * @author Karl Li
  */
object Generator {
  // actor protocol
  sealed trait Command
  final case class Chat(request: CompletionRequest, replyTo: ActorRef[StatusReply[CompletionResponse]]) extends Command

  def apply(config: ServeConfig): Behavior[Command] = {
    Behaviors.setup { context =>
      implicit val ec = context.executionContext
      val log = context.log // context cannot be used inside Future.onComplete, which is outside of an actor.
      val model = Llama.build(config.model, config.tokenizer,
        config.maxBatchSize, config.maxSeqLen, config.device)

      Behaviors.receiveMessage {
        case Chat(request, replyTo) =>
          try {
            if (request.model != model.family()) {
              throw new IllegalArgumentException(s"Unsupported model: ${request.model}")
            }

            val seed: java.lang.Long = if (request.seed.isDefined) request.seed.get else null
            val completions = model.chat(Array(request.messages),
                request.max_tokens.getOrElse(2048), request.temperature.getOrElse(0.6),
                request.top_p.getOrElse(0.9), request.logprobs.getOrElse(false), seed)
            replyTo ! StatusReply.Success(CompletionResponse(completions(0)))
          } catch {
            case e: Throwable => replyTo ! StatusReply.Error(e)
          }
          Behaviors.same
      }
    }
  }
}